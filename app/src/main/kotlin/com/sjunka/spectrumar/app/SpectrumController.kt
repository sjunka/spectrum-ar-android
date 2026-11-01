package com.sjunka.spectrumar.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.sjunka.spectrumar.core.CameraController
import com.sjunka.spectrumar.core.CameraPreset
import com.sjunka.spectrumar.core.LabelCandidate
import com.sjunka.spectrumar.core.LabelLayout
import com.sjunka.spectrumar.core.MatchData
import com.sjunka.spectrumar.core.OrbitPose
import com.sjunka.spectrumar.core.PassEvaluator
import com.sjunka.spectrumar.core.PassOption
import com.sjunka.spectrumar.core.PitchControl
import com.sjunka.spectrumar.core.PitchControlGrid
import com.sjunka.spectrumar.core.ReplayClock
import com.sjunka.spectrumar.core.Team
import com.sjunka.spectrumar.core.Timeline
import com.sjunka.spectrumar.core.TimelineMarker
import com.sjunka.spectrumar.core.Vec2
import com.sjunka.spectrumar.core.Vec3
import com.sjunka.spectrumar.core.VisibleLabel
import com.sjunka.spectrumar.scene.PitchRig
import com.sjunka.spectrumar.scene.RigFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

enum class AppMode { TABLE, AR }
enum class Haptic { TICK, CLICK, CONFIRM, HEAVY, REJECT }
enum class ArPhase { SEARCHING, READY, PLACED }

/** Screen/world bridge implemented by the host (table SceneView or AR SceneView). Coordinates are rig-local pitch metres. */
interface SceneProjector {
    val width: Float
    val height: Float
    val density: Float
    fun toScreen(x: Float, y: Float, z: Float): Offset?
    fun groundHit(sx: Float, sy: Float): Vec2?
    fun cameraLocal(): Vec3
}

data class SelStats(val number: Int, val team: Team, val speedKmh: Int, val distanceM: Int, val topKmh: Int)
data class OptionUi(val number: Int, val xt: Double, val probability: Double, val ev: Double, val best: Boolean)

/**
 * Single source of truth shared by both modes. Compose-visible state uses snapshot state; everything
 * that changes every frame lives in plain fields and is drawn through [overlayTick].
 */
class SpectrumController(
    val data: MatchData,
    private val scope: CoroutineScope,
    private val haptic: (Haptic) -> Unit,
) {
    val clock = ReplayClock(data.duration)
    val camera = CameraController()
    val markers: List<TimelineMarker> = Timeline.markers(data.events)
    val frame = RigFrame(data.playerCount)
    private val n = data.playerCount

    var rig: PitchRig? = null
    /** Scene that created [rig]; a late dispose of a replaced scene must not clear the new one. */
    var rigOwner: Any? = null
    var onCamera: ((OrbitPose) -> Unit)? = null

    // Compose-visible state
    var mode by mutableStateOf(AppMode.TABLE)
    var playing by mutableStateOf(true)
    var speed by mutableFloatStateOf(1f)
    var timeSec by mutableFloatStateOf(0f)
    var selected by mutableIntStateOf(-1)
    var selStats by mutableStateOf<SelStats?>(null)
    var pitchControlOn by mutableStateOf(false)
    var homeShare by mutableFloatStateOf(0.5f)
    var whatIfOn by mutableStateOf(false)
    var options by mutableStateOf<List<OptionUi>>(emptyList())
    var whatIfNote by mutableStateOf("")
    var demoOn by mutableStateOf(false)
    var caption by mutableStateOf("")
    var preset by mutableStateOf(CameraPreset.TACTICAL)
    var numbersOn by mutableStateOf(true)
    var aboutOpen by mutableStateOf(false)
    var arPhase by mutableStateOf(ArPhase.SEARCHING)
    var arNotice by mutableStateOf<String?>(null)
    var pendingSelectCarrier = false
    var overlayTick by mutableIntStateOf(0)
    var overlayWidth = 1080f
    var overlayHeight = 2400f

    // per-frame overlay output (read in draw/layer phases)
    var labels: List<VisibleLabel> = emptyList()
    var cardAnchor: Offset? = null
    var carrierIndex = -1

    private val overrides = HashMap<Int, Vec2>()
    private var overridesDirty = true
    private var lastNanos = 0L
    private var seconds = 0f
    private var lastWhatIfTime = -1.0
    private var arrowAnim = 0f

    // pitch control background work
    private val latestControl = AtomicReference<PitchControlGrid?>(null)
    @Volatile private var controlTime = 0.0
    @Volatile private var controlOverrides: Map<Int, Vec2> = emptyMap()
    private var controlJob: Job? = null
    private var uploadedGrid: PitchControlGrid? = null

    // demo
    private var demoClock = 0.0
    private var demoPrev = -1.0

    init {
        clock.playing = true
        camera.setPreset(CameraPreset.TACTICAL)
    }

    // ---- public intents -------------------------------------------------------------------

    fun togglePlay() {
        clock.togglePlay(); playing = clock.playing
        if (clock.playing) clearOverrides()
        haptic(Haptic.CLICK)
    }

    fun applyPlaying(p: Boolean) { clock.playing = p; playing = p; if (p) clearOverrides() }

    fun cycleSpeed() { clock.cycleSpeed(); speed = clock.speed.toFloat(); haptic(Haptic.TICK) }

    fun setSpeed(s: Double) { clock.setSpeed(s); speed = clock.speed.toFloat() }

    fun seekFraction(fr: Float) { seekTo(fr.coerceIn(0f, 1f) * data.duration) }

    fun seekTo(t: Double) {
        clock.seek(t); timeSec = clock.time.toFloat(); clearOverrides(); overridesDirty = true
    }

    fun nextMarker() { Timeline.nextMarkerTime(markers, clock.time)?.let { seekTo((it - 0.3).coerceAtLeast(0.0)); haptic(Haptic.TICK) } }
    fun prevMarker() { Timeline.prevMarkerTime(markers, clock.time - 0.6)?.let { seekTo((it - 0.3).coerceAtLeast(0.0)); haptic(Haptic.TICK) } }

    fun setPreset(p: CameraPreset, fromUser: Boolean = true) {
        preset = p
        val ball = data.ballPosition(clock.time)
        camera.setPreset(p, ball, otherPositions())
        if (fromUser) haptic(Haptic.CLICK)
    }

    fun toggleNumbers() { numbersOn = !numbersOn; haptic(Haptic.TICK) }

    fun select(i: Int) {
        if (i == selected) return
        selected = i; frame.selected = i
        if (i < 0) selStats = null
        overlayTick++
    }

    fun clearSelection() { select(-1) }
    fun selectNone() = clearSelection()
    fun fireHaptic(h: Haptic) = haptic(h)

    fun setPitchControl(on: Boolean, fromUser: Boolean = false) {
        if (on == pitchControlOn) return
        pitchControlOn = on
        rig?.controlVisible = on
        if (on) startControlLoop() else { controlJob?.cancel(); controlJob = null }
        if (fromUser) haptic(Haptic.CONFIRM)
    }

    fun togglePitchControl() = setPitchControl(!pitchControlOn, true)

    fun setWhatIf(on: Boolean, fromUser: Boolean = false) {
        if (on == whatIfOn) return
        whatIfOn = on
        if (on) { applyPlaying(false); overridesDirty = true; frame.arrowGrow = 0f }
        else { clearOverrides(); frame.arrowCount = 0; options = emptyList(); whatIfNote = "" }
        if (fromUser) haptic(Haptic.CONFIRM)
    }

    fun toggleWhatIf() = setWhatIf(!whatIfOn, true)

    fun toggleDemo() = setDemo(!demoOn)

    fun setDemo(on: Boolean) {
        if (on == demoOn) return
        demoOn = on
        haptic(Haptic.HEAVY)
        if (on) {
            demoClock = 0.0; demoPrev = -1.0; clearSelection(); setWhatIf(false); camera.reserveBottom(0.14f)
            camera.snapToGoal()
        } else {
            caption = ""; camera.yawBias = 0f; pendingSelectCarrier = false; demoCarrierSelect = false
            setPreset(CameraPreset.TACTICAL, fromUser = false)
            setSpeed(1.0); applyPlaying(true)
            setPitchControl(false); setWhatIf(false); clearSelection()
        }
    }

    // ---- picking and dragging -------------------------------------------------------------

    /** Nearest player to a tap, projected body centre within ~44 dp. */
    fun pickPlayer(sx: Float, sy: Float, proj: SceneProjector, radiusDp: Float = 44f): Int {
        val maxD = radiusDp * proj.density
        var best = -1; var bestD = Float.MAX_VALUE
        val t = clock.time
        for (i in 0 until n) {
            val p = positionOf(i, t)
            val o = proj.toScreen(p.x, 2.2f, p.y) ?: continue
            val d = hypot(o.x - sx, o.y - sy)
            if (d < maxD && d < bestD) { bestD = d; best = i }
        }
        return best
    }

    fun tap(sx: Float, sy: Float, proj: SceneProjector) {
        val i = pickPlayer(sx, sy, proj)
        if (i >= 0) { select(i); haptic(Haptic.CONFIRM) }
        else if (selected >= 0) { clearSelection(); haptic(Haptic.TICK) }
    }

    private var dragIndex = -1

    /** Begins a what-if drag when the touch lands on a player. Returns true when the gesture is consumed. */
    fun beginDrag(sx: Float, sy: Float, proj: SceneProjector): Boolean {
        if (!whatIfOn) return false
        val i = pickPlayer(sx, sy, proj, 52f)
        if (i < 0) return false
        dragIndex = i; frame.dragged = i
        haptic(Haptic.CLICK)
        return true
    }

    fun dragTo(sx: Float, sy: Float, proj: SceneProjector) {
        val i = dragIndex
        if (i < 0) return
        val g = proj.groundHit(sx, sy) ?: return
        overrides[i] = Vec2(g.x.coerceIn(-55f, 55f), g.y.coerceIn(-36f, 36f))
        overridesDirty = true
    }

    fun endDrag() {
        if (dragIndex >= 0) haptic(Haptic.TICK)
        dragIndex = -1; frame.dragged = -1
    }

    private fun clearOverrides() { if (overrides.isNotEmpty()) { overrides.clear(); overridesDirty = true } }

    // ---- per frame ------------------------------------------------------------------------

    private fun positionOf(i: Int, t: Double): Vec2 = overrides[i] ?: data.position(i, t)

    private fun otherPositions(): List<Vec2> { val t = clock.time; return List(n) { positionOf(it, t) } }

    fun onFrame(nanos: Long, proj: SceneProjector?) {
        val dt = if (lastNanos == 0L) 0f else ((nanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = nanos
        seconds += dt
        frame.seconds = seconds

        if (demoOn) demoTick(dt.toDouble())
        if (!whatIfOn || clock.playing) {
            clock.advance(dt.toDouble())
        }
        val t = clock.time
        if (abs(timeSec - t.toFloat()) > 0.004f) timeSec = t.toFloat()
        if (playing != clock.playing) playing = clock.playing

        // entities
        for (i in 0 until n) { val p = positionOf(i, t); frame.px[i] = p.x; frame.pz[i] = p.y }
        val ball = data.ballPosition(t)
        frame.bx = ball.x; frame.bz = ball.y
        var carrier: Int? = null
        var bestD = 6f
        for (i in 0 until n) {
            val d = hypot(frame.px[i] - ball.x, frame.pz[i] - ball.y)
            if (d <= bestD) { bestD = d; carrier = i }
        }
        carrierIndex = carrier ?: -1
        frame.carrier = carrierIndex
        if (demoOn && demoCarrierSelect && carrier != null) select(carrier)
        if (pendingSelectCarrier && carrier != null) { select(carrier); pendingSelectCarrier = false }

        // selection card + stats
        val sel = selected
        if (sel >= 0) {
            val s = SelStats(
                data.players[sel].number, data.players[sel].team,
                MatchData.mpsToKmh(data.speed(sel, t)).toInt(), data.distance(sel, t).toInt(),
                MatchData.mpsToKmh(data.topSpeed(sel, t)).toInt(),
            )
            if (s != selStats) selStats = s
            var c = 0
            val steps = 40
            for (k in 0 until steps) {
                val tt = t - 5.0 * (1.0 - k / (steps - 1.0))
                if (tt < 0.0) continue
                val p = data.position(sel, tt)
                frame.selTrail[c * 2] = p.x; frame.selTrail[c * 2 + 1] = p.y; c++
            }
            frame.selTrailCount = c
        } else frame.selTrailCount = 0
        run {
            var c = 0
            val steps = 32
            for (k in 0 until steps) {
                val tt = t - 1.4 * (1.0 - k / (steps - 1.0))
                if (tt < 0.0) continue
                val p = data.ballPosition(tt)
                frame.ballTrail[c * 2] = p.x; frame.ballTrail[c * 2 + 1] = p.y; c++
            }
            frame.ballTrailCount = c
        }

        // what-if
        if (whatIfOn) {
            if (overridesDirty || abs(t - lastWhatIfTime) > 1e-6) { recomputeWhatIf(data.snapshot(t, overrides), carrier); overridesDirty = false; lastWhatIfTime = t }
            arrowAnim = (arrowAnim + dt * 2.2f).coerceAtMost(1f)
            frame.arrowGrow = arrowAnim
        }

        // pitch control upload
        if (pitchControlOn) {
            controlTime = t; controlOverrides = HashMap(overrides)
            val g = latestControl.getAndSet(null)
            if (g != null) { rig?.uploadControl(g.values, g.cols, g.rows); homeShare = g.homeShare; uploadedGrid = g }
        }

        // camera
        if (proj != null && mode == AppMode.TABLE) {
            camera.aspect = proj.width / max(1f, proj.height)
            if (preset != CameraPreset.TACTICAL && preset != CameraPreset.FREE) camera.track(ball, otherPositions())
            if (demoOn) camera.yawBias = DemoScript.yawBias(demoClock)
            camera.step(dt)
            onCamera?.invoke(camera.current)
            if (camera.preset == CameraPreset.FREE && preset != CameraPreset.FREE) preset = CameraPreset.FREE
        }

        rig?.update(frame)

        if (proj != null) computeOverlays(proj, t)
        overlayTick++
    }

    private fun recomputeWhatIf(snap: List<com.sjunka.spectrumar.core.PlayerSnapshot>, carrier: Int?) {
        if (carrier == null) {
            options = emptyList(); frame.arrowCount = 0
            whatIfNote = "Ball is loose: scrub to a moment with a ball carrier"
            return
        }
        val list = PassEvaluator.options(carrier, snap)
        val top = list.take(3)
        options = top.mapIndexed { idx, o -> OptionUi(data.players[o.target].number, o.targetValue, o.probability, o.expectedValue, idx == 0) }
        whatIfNote = "#${data.players[carrier].number} on the ball"
        val cp = snap.first { it.index == carrier }.position
        frame.arrowCount = top.size
        frame.arrowTarget = IntArray(3) { -1 }
        top.forEachIndexed { k, o ->
            val tp = snap.first { it.index == o.target }.position
            frame.arrow[k * 4] = cp.x; frame.arrow[k * 4 + 1] = cp.y; frame.arrow[k * 4 + 2] = tp.x; frame.arrow[k * 4 + 3] = tp.y
            frame.arrowTarget[k] = o.target
        }
        if (arrowAnim >= 1f) arrowAnim = 0.35f
    }

    private fun computeOverlays(proj: SceneProjector, t: Double) {
        val cam = proj.cameraLocal()
        val ref = cam.length().coerceAtLeast(1f)
        val cands = ArrayList<LabelCandidate>(n)
        val dens = proj.density
        for (i in 0 until n) {
            val o = proj.toScreen(frame.px[i], 6.0f, frame.pz[i]) ?: continue
            val dx = cam.x - frame.px[i]; val dy = cam.y; val dz = cam.z - frame.pz[i]
            val depth = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
            cands += LabelCandidate(i, o.x, o.y, depth, i == selected || i == carrierIndex || i == frame.dragged)
        }
        val raw = LabelLayout.layout(
            cands, proj.width, proj.height,
            minSeparation = 17f * dens, nearDepth = ref * 0.95f, farDepth = ref * 2.1f,
        )
        labels = if (numbersOn) raw else raw.filter { it.priority }
        cardAnchor = if (selected >= 0) proj.toScreen(frame.px[selected], 7.5f, frame.pz[selected]) else null
    }

    // ---- pitch control loop --------------------------------------------------------------

    private fun startControlLoop() {
        controlJob?.cancel()
        controlJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val t0 = System.nanoTime()
                val snap = data.snapshot(controlTime, controlOverrides)
                latestControl.set(PitchControl.compute(snap))
                val spent = (System.nanoTime() - t0) / 1_000_000
                delay((83 - spent).coerceAtLeast(8))
            }
        }
    }

    fun shutdown() { controlJob?.cancel() }

    // ---- demo ----------------------------------------------------------------------------

    private var demoCarrierSelect = false

    private fun demoTick(dt: Double) {
        demoClock += dt
        if (demoClock >= DemoScript.LOOP_SECONDS) { demoClock = 0.0; demoPrev = -1.0 }
        for (step in DemoScript.due(demoPrev, demoClock)) runDemo(step.action)
        demoPrev = demoClock
    }

    private fun runDemo(a: DemoAction) {
        when (a) {
            is DemoAction.Seek -> { clock.seek(a.matchTime); clearOverrides(); overridesDirty = true }
            is DemoAction.Speed -> setSpeed(a.value)
            is DemoAction.Preset -> setPreset(a.preset, fromUser = false)
            is DemoAction.PitchControl -> setPitchControl(a.on)
            is DemoAction.SelectCarrier -> { demoCarrierSelect = a.on; if (!a.on) clearSelection() }
            is DemoAction.WhatIf -> setWhatIf(a.on)
            is DemoAction.MoveTeammate -> {
                val c = carrierIndex
                val snapOpts = if (c >= 0) PassEvaluator.options(c, data.snapshot(clock.time, overrides)) else emptyList()
                snapOpts.firstOrNull()?.let {
                    val p = positionOf(it.target, clock.time)
                    overrides[it.target] = Vec2((p.x + a.dx).coerceIn(-50f, 50f), (p.y + a.dy).coerceIn(-32f, 32f))
                    overridesDirty = true; arrowAnim = 0.2f
                }
            }
            is DemoAction.Play -> applyPlaying(a.playing)
            is DemoAction.Caption -> caption = a.text
        }
    }
}
