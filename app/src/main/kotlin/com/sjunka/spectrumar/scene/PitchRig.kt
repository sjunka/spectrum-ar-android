package com.sjunka.spectrumar.scene

import android.graphics.Bitmap
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.sjunka.spectrumar.core.MatchData
import com.sjunka.spectrumar.core.Team
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CapsuleNode
import io.github.sceneview.node.ConeNode
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.PlaneNode
import io.github.sceneview.node.SphereNode
import io.github.sceneview.texture.ImageTexture
import io.github.sceneview.texture.setBitmap
import com.google.android.filament.utils.TextureType
import kotlin.math.PI
import kotlin.math.sin

/** Bitmaps generated off the main thread (during the splash) and uploaded once per rig. */
class RigBitmaps(
    val pitch: Bitmap,
    val baseHome: Bitmap, val baseAway: Bitmap,
    val ringMint: Bitmap, val ringWarn: Bitmap, val ringWhite: Bitmap,
    val trailHome: Bitmap, val trailAway: Bitmap, val trailBall: Bitmap,
    val chevronBright: Bitmap, val chevronDim: Bitmap,
    val ballGlow: Bitmap, val shadow: Bitmap,
) {
    companion object {
        fun generate() = RigBitmaps(
            pitch = Textures.pitch(),
            baseHome = Textures.glow(Textures.MINT, 128, 0.0f, 0.85f), baseAway = Textures.glow(Textures.VIOLET, 128, 0.0f, 0.85f),
            ringMint = Textures.ring(Textures.MINT), ringWarn = Textures.ring(Textures.WARN), ringWhite = Textures.ring(0xFFFFFFFF.toInt()),
            trailHome = Textures.trail(Textures.MINT), trailAway = Textures.trail(Textures.VIOLET), trailBall = Textures.trail(Textures.WARN),
            chevronBright = Textures.chevron(0xFF7CFFD8.toInt()), chevronDim = Textures.chevron(0xFFB4ABFF.toInt()),
            ballGlow = Textures.glow(Textures.WARN, 128, 0.0f, 0.9f), shadow = Textures.glow(0xFF000000.toInt(), 128, 0.0f, 0.55f),
        )
    }
}

/** Per-frame values the rig renders. Positions are pitch metres: x along the pitch, z across. */
class RigFrame(n: Int) {
    val px = FloatArray(n); val pz = FloatArray(n)
    var bx = 0f; var bz = 0f
    var selected = -1; var carrier = -1; var dragged = -1
    var seconds = 0f
    val selTrail = FloatArray(2 * 40); var selTrailCount = 0
    val ballTrail = FloatArray(2 * 32); var ballTrailCount = 0
    var arrowCount = 0
    val arrow = FloatArray(3 * 4)   // from x, from z, to x, to z
    var arrowGrow = 1f
    var arrowTarget = IntArray(3) { -1 }
    var showFloodlights = true
}

/**
 * The 3D scene content: pitch, players, ball, overlays. It owns a single [root] node and is attached
 * to either the table-top SceneView or under an AR anchor, so both modes render the very same graph.
 * Units are real metres of pitch; the AR mode just scales [root] down to table size.
 */
class PitchRig(
    private val engine: Engine,
    private val ml: MaterialLoader,
    private val data: MatchData,
    bitmaps: RigBitmaps,
) {
    val root = Node(engine)
    private val n = data.playerCount
    private val textures = ArrayList<Texture>()

    private fun tex(b: Bitmap, mips: Boolean = true): Texture {
        val t = ImageTexture.Builder().bitmap(b, TextureType.COLOR).build(engine)
        textures += t
        return t
    }
    private fun imageMat(b: Bitmap): MaterialInstance = ml.createImageInstance(tex(b))

    // materials
    private val homeBody = ml.createColorInstance(0xFF00E5A0.toInt(), metallic = 0.15f, roughness = 0.38f)
    private val awayBody = ml.createColorInstance(0xFF7C6CFF.toInt(), metallic = 0.15f, roughness = 0.38f)
    private val homeHead = ml.createColorInstance(0xFFBFFFE6.toInt(), metallic = 0.0f, roughness = 0.35f)
    private val awayHead = ml.createColorInstance(0xFFD8D3FF.toInt(), metallic = 0.0f, roughness = 0.35f)
    private val ballMat = ml.createColorInstance(0xFFFFFFFF.toInt(), metallic = 0.0f, roughness = 0.25f)
    private val pitchTexture = tex(bitmaps.pitch)
    private val pitchMat = ml.createTextureInstance(pitchTexture, isOpaque = true, metallic = 0f, roughness = 0.85f, reflectance = 0.05f)
    private val slabMat = ml.createColorInstance(0xFF0F1626.toInt(), metallic = 0.4f, roughness = 0.5f)
    private val edgeMat = ml.createUnlitColorInstance(0xFF00E5A0.toInt())
    private val markerMat = ml.createUnlitColorInstance(0xFF00E5A0.toInt())
    private val padMat = ml.createColorInstance(0xFF1B2744.toInt(), metallic = 0.6f, roughness = 0.4f)
    private val lampMat = ml.createUnlitColorInstance(0xFFFFF4D6.toInt())

    private val baseHome = imageMat(bitmaps.baseHome)
    private val baseAway = imageMat(bitmaps.baseAway)
    private val ringMint = imageMat(bitmaps.ringMint)
    private val ringWarn = imageMat(bitmaps.ringWarn)
    private val ringWhite = imageMat(bitmaps.ringWhite)
    private val ballGlowMat = imageMat(bitmaps.ballGlow)
    private val shadowMat = imageMat(bitmaps.shadow)

    // pitch control overlay: RGBA8 texture rewritten at ~12 Hz
    val controlW = 105; val controlH = 68
    private val controlTexture: Texture = Texture.Builder().width(controlW).height(controlH).levels(1)
        .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.RGBA8).usage(Texture.Usage.DEFAULT).build(engine)
        .also { textures += it }
    private val controlBitmap = Bitmap.createBitmap(controlW, controlH, Bitmap.Config.ARGB_8888).also { it.isPremultiplied = false }
    private val controlMat = ml.createImageInstance(controlTexture)
    private var controlPixels = IntArray(controlW * controlH)

    private val pitchNode: PlaneNode
    private val controlNode: PlaneNode
    private val playerNodes = ArrayList<Node>(n)
    private val bodyNodes = ArrayList<Node>(n)
    private val ball: SphereNode
    private val ballGroup = Node(engine)
    private val ballGlow: PlaneNode
    private val ballShadow: PlaneNode
    private val selRing: PlaneNode
    private val carrierRing: PlaneNode
    private val targetRing: PlaneNode
    private val marker: ConeNode
    private val lights = ArrayList<Node>()

    private val selTrailRibbon: Ribbon
    private val ballTrailRibbon: Ribbon
    private val arrows: List<DashedArrow>
    private val arrowMats: List<MaterialInstance>
    private val trailHomeMat = imageMat(bitmaps.trailHome)
    private val trailAwayMat = imageMat(bitmaps.trailAway)
    private val trailBallMat = imageMat(bitmaps.trailBall)

    var controlVisible: Boolean
        get() = controlNode.isVisible
        set(v) { controlNode.isVisible = v }

    init {
        val L = MatchData.PITCH_LENGTH; val W = MatchData.PITCH_WIDTH
        val tw = L + 2 * Textures.MARGIN; val th = W + 2 * Textures.MARGIN
        pitchNode = PlaneNode(engine, size = Size(tw, th), normal = Direction(y = 1f), materialInstance = pitchMat) { }
            .also { it.isShadowCaster = false; root.addChildNode(it) }
        // base slab (gives the board a physical thickness in AR)
        CubeNode(engine, size = Size(tw, 1.8f, th), center = Position(0f, -0.95f, 0f), materialInstance = slabMat).also {
            it.isShadowCaster = false; root.addChildNode(it)
        }
        CubeNode(engine, size = Size(tw + 0.5f, 0.28f, th + 0.5f), center = Position(0f, -0.22f, 0f), materialInstance = edgeMat).also {
            it.isShadowCaster = false; it.isShadowReceiver = false; root.addChildNode(it)
        }

        controlNode = PlaneNode(engine, size = Size(L, W), normal = Direction(y = 1f), materialInstance = controlMat) { }.also {
            it.position = Position(0f, 0.05f, 0f); it.isVisible = false
            it.isShadowCaster = false; it.isShadowReceiver = false; it.setGlobalBlendOrderEnabled(true); it.setBlendOrder(1)
            root.addChildNode(it)
        }

        // ribbons and arrows
        selTrailRibbon = Ribbon(engine, trailHomeMat, 40, 0.14f).also { it.node.setBlendOrder(3); root.addChildNode(it.node) }
        ballTrailRibbon = Ribbon(engine, trailBallMat, 32, 0.16f).also { it.node.setBlendOrder(3); root.addChildNode(it.node) }
        val chevB = imageMat(bitmaps.chevronBright)
        val chevD = imageMat(bitmaps.chevronDim)
        arrowMats = listOf(chevB, chevD, chevD)
        arrows = arrowMats.map { m -> DashedArrow(engine, m, 12, 0.24f).also { it.node.setBlendOrder(6); root.addChildNode(it.node) } }

        // players
        for (i in 0 until n) {
            val home = data.players[i].team == Team.HOME
            val g = Node(engine)
            val base = PlaneNode(engine, size = Size(8.5f, 8.5f), normal = Direction(y = 1f), materialInstance = if (home) baseHome else baseAway) { }
            base.position = Position(0f, 0.10f, 0f); base.isShadowCaster = false; base.isShadowReceiver = false
            base.setGlobalBlendOrderEnabled(true); base.setBlendOrder(2)
            val body = Node(engine)
            val cap = CapsuleNode(engine, radius = 0.85f, height = 1.7f, materialInstance = if (home) homeBody else awayBody)
            cap.position = Position(0f, 1.75f, 0f)
            val head = SphereNode(engine, radius = 0.72f, stacks = 16, slices = 20, materialInstance = if (home) homeHead else awayHead)
            head.position = Position(0f, 4.05f, 0f)
            body.addChildNode(cap); body.addChildNode(head)
            g.addChildNode(base); g.addChildNode(body)
            root.addChildNode(g)
            playerNodes += g; bodyNodes += body
        }

        // ball
        ballShadow = PlaneNode(engine, size = Size(4.2f, 4.2f), normal = Direction(y = 1f), materialInstance = shadowMat) { }.also {
            it.position = Position(0f, 0.09f, 0f); it.isShadowCaster = false; it.isShadowReceiver = false
            it.setGlobalBlendOrderEnabled(true); it.setBlendOrder(2)
        }
        ballGlow = PlaneNode(engine, size = Size(9f, 9f), normal = Direction(y = 1f), materialInstance = ballGlowMat) { }.also {
            it.position = Position(0f, 0.12f, 0f); it.isShadowCaster = false; it.isShadowReceiver = false
            it.setGlobalBlendOrderEnabled(true); it.setBlendOrder(2)
        }
        ball = SphereNode(engine, radius = 0.85f, stacks = 16, slices = 20, materialInstance = ballMat)
        ball.position = Position(0f, 0.9f, 0f)
        ballGroup.addChildNode(ballGlow); ballGroup.addChildNode(ballShadow); ballGroup.addChildNode(ball)
        root.addChildNode(ballGroup)

        // rings and marker
        fun ring(m: MaterialInstance, size: Float, order: Int) = PlaneNode(engine, size = Size(size, size), normal = Direction(y = 1f), materialInstance = m) { }.also {
            it.position = Position(0f, 0.2f, 0f); it.isVisible = false
            it.isShadowCaster = false; it.isShadowReceiver = false; it.setGlobalBlendOrderEnabled(true); it.setBlendOrder(order)
            root.addChildNode(it)
        }
        selRing = ring(ringWhite, 11f, 4)
        carrierRing = ring(ringWarn, 9f, 4)
        targetRing = ring(ringMint, 10f, 4)
        marker = ConeNode(engine, radius = 0.9f, height = 1.5f, sideCount = 20, materialInstance = markerMat).also {
            it.rotation = Rotation(x = 180f); it.isVisible = false; it.isShadowCaster = false; it.isShadowReceiver = false
            root.addChildNode(it)
        }

        // floodlight pylons (table mode only; hidden in AR)
        for (sx in listOf(-1f, 1f)) for (sz in listOf(-1f, 1f)) {
            val g = Node(engine)
            val pole = CylinderNode(engine, radius = 0.45f, height = 34f, materialInstance = padMat)
            pole.position = Position(0f, 17f, 0f)
            val lamp = CubeNode(engine, size = Size(6f, 2.2f, 1.2f), materialInstance = lampMat)
            lamp.position = Position(0f, 34.6f, 0f)
            pole.isShadowCaster = false; lamp.isShadowCaster = false
            g.addChildNode(pole); g.addChildNode(lamp)
            g.position = Position(sx * (L / 2 + 13f), 0f, sz * (W / 2 + 11f))
            root.addChildNode(g); lights += g
        }
    }

    fun setFloodlights(v: Boolean) { lights.forEach { it.isVisible = v } }

    /** Uploads a pitch-control grid (home probability, row major, 105x68) as the overlay texture. */
    fun uploadControl(values: FloatArray, cols: Int, rows: Int) {
        if (cols != controlW || rows != controlH) return
        val px = controlPixels
        for (i in px.indices) {
            val v = values[i]
            val d = (v - 0.5f) * 2f              // -1 away .. +1 home
            val ad = kotlin.math.abs(d)
            val s = ad * ad * (3f - 2f * ad)     // smoothstep: soft boundary, saturated cores
            val a = (0.07f + 0.50f * s)
            val r: Float; val g: Float; val b: Float
            if (d >= 0) { r = 0f; g = 0.90f; b = 0.63f } else { r = 0.486f; g = 0.424f; b = 1f }
            // near the contested line the colours blend towards white
            val white = (1f - ad) * (1f - ad) * 0.55f
            px[i] = ((a * 255).toInt() shl 24) or (((r + (1 - r) * white) * 255).toInt() shl 16) or
                (((g + (1 - g) * white) * 255).toInt() shl 8) or ((b + (1 - b) * white) * 255).toInt()
        }
        controlBitmap.setPixels(px, 0, controlW, 0, 0, controlW, controlH)
        controlTexture.setBitmap(engine, controlBitmap)
        controlNode.requestRender()
    }

    private var lastTrailPlayer = -2

    fun update(f: RigFrame) {
        val t = f.seconds
        for (i in 0 until n) {
            val node = playerNodes[i]
            val drag = i == f.dragged
            node.position = Position(f.px[i], if (drag) 2.2f else 0f, f.pz[i])
            val s = if (drag) 1.25f else if (i == f.selected) 1.12f else 1f
            node.scale = Scale(s)
        }
        ballGroup.position = Position(f.bx, 0f, f.bz)

        // selection
        val sel = f.selected
        if (sel >= 0) {
            val pulse = 1f + 0.06f * sin(t * 4.2f)
            selRing.isVisible = true
            selRing.position = Position(f.px[sel], 0.2f, f.pz[sel])
            selRing.scale = Scale(pulse)
            selRing.rotation = Rotation(y = (t * 40f) % 360f)
            marker.isVisible = true
            marker.position = Position(f.px[sel], 6.9f + 0.35f * sin(t * 3.4f), f.pz[sel])
            marker.rotation = Rotation(x = 180f, y = (t * 90f) % 360f)
        } else { selRing.isVisible = false; marker.isVisible = false }

        if (f.carrier >= 0) {
            carrierRing.isVisible = true
            carrierRing.position = Position(f.px[f.carrier], 0.19f, f.pz[f.carrier])
            carrierRing.scale = Scale(1f + 0.09f * sin(t * 6f))
        } else carrierRing.isVisible = false

        // ribbons
        if (sel >= 0 && f.selTrailCount >= 2) {
            val home = data.players[sel].team == Team.HOME
            selTrailRibbon.node.setMaterialInstanceAt(0, if (home) trailHomeMat else trailAwayMat)
            selTrailRibbon.update(f.selTrail, f.selTrailCount, 0.2f, 2.9f)
        } else selTrailRibbon.hide()
        if (f.ballTrailCount >= 2) ballTrailRibbon.update(f.ballTrail, f.ballTrailCount, 0.1f, 1.7f) else ballTrailRibbon.hide()

        // pass arrows
        for (k in 0 until 3) {
            if (k < f.arrowCount) {
                val o = k * 4
                val w = if (k == 0) 3.6f else 2.1f
                arrows[k].update(f.arrow[o], f.arrow[o + 1], f.arrow[o + 2], f.arrow[o + 3], (t * (if (k == 0) 0.9f else 0.6f)) % 1f, f.arrowGrow, w, 4.4f)
            } else arrows[k].hide()
        }
        if (f.arrowCount > 0 && f.arrowTarget[0] >= 0) {
            val tg = f.arrowTarget[0]
            targetRing.isVisible = true
            targetRing.position = Position(f.px[tg], 0.21f, f.pz[tg])
            targetRing.scale = Scale(1f + 0.12f * sin(t * 5f))
            targetRing.rotation = Rotation(y = (-t * 60f) % 360f)
        } else targetRing.isVisible = false
    }

    fun destroyTextures() {
        // The engine owns everything when the SceneView goes away; nothing else to release explicitly.
    }
}
