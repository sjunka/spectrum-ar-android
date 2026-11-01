package com.sjunka.spectrumar.ar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.sjunka.spectrumar.app.ArPhase
import com.sjunka.spectrumar.app.Haptic
import com.sjunka.spectrumar.app.SpectrumController
import com.sjunka.spectrumar.core.MatchData
import com.sjunka.spectrumar.scene.FilamentProjector
import com.sjunka.spectrumar.scene.PitchRig
import com.sjunka.spectrumar.scene.RigBitmaps
import com.sjunka.spectrumar.ui.GestureLayer
import com.sjunka.spectrumar.ui.SceneGestureCallbacks
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.Node
import io.github.sceneview.node.PlaneNode
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Size
import io.github.sceneview.rememberARView
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.texture.ImageTexture
import com.google.android.filament.utils.TextureType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin

/** Mutable AR placement state; read every frame so it is deliberately not Compose state. */
class ArPlacement {
    var anchor: Anchor? = null
    var lastHit: HitResult? = null
    var userScale = 1f
    var yawDeg = 0f
    var camFwdX = 0f
    var camFwdZ = -1f
    var needsInitialYaw = false
    fun reset() { anchor?.detach(); anchor = null; userScale = 1f }
}

/** Real-world width of the 105 m pitch when first placed (about 60 cm, a table-top board). */
const val TABLE_SCALE = 0.60f / MatchData.PITCH_LENGTH

/** AR mode: the same PitchRig anchored on a detected horizontal surface. */
@Composable
fun ArScene(c: SpectrumController, bitmaps: RigBitmaps, onFailure: (String) -> Unit, modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val view = rememberARView(engine)
    val cameraNode = rememberARCameraNode(engine)
    val density = LocalDensity.current.density
    val st = remember { ArPlacement() }
    val projector = remember(view) { FilamentProjector(view, { c.rig }, { cameraNode.worldPosition }, density) }

    DisposableEffect(c) {
        c.arPhase = ArPhase.SEARCHING
        onDispose { c.rig = null; st.anchor?.detach(); st.anchor = null; st.lastHit = null }
    }

    Box(modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine, modelLoader = modelLoader, materialLoader = materialLoader, view = view, cameraNode = cameraNode,
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL,
            planeRenderer = true,
            permissionHandler = null,
            cameraPermissionOverlay = null,
            arCoreAvailabilityOverlay = null,
            onSessionFailure = { f -> onFailure(describeFailure(f)) },
            onSessionFailed = { e -> onFailure(e.message ?: "The AR session could not start.") },
            onTrackingFailureChanged = { r -> c.arNotice = r?.let { trackingMessage(it.name) } },
            onSessionUpdated = { _, frame ->
                try {
                    val cam = frame.camera
                    if (cam.trackingState == TrackingState.TRACKING) {
                        val z = cam.pose.zAxis
                        st.camFwdX = -z[0]; st.camFwdZ = -z[2]
                        if (st.anchor == null) {
                            val hits = frame.hitTest(view.viewport.width / 2f, view.viewport.height * 0.45f)
                            val h = hits.firstOrNull {
                                val t = it.trackable
                                t is Plane && t.type == Plane.Type.HORIZONTAL_UPWARD_FACING && t.isPoseInPolygon(it.hitPose) && t.trackingState == TrackingState.TRACKING
                            }
                            st.lastHit = h
                            val phase = if (h != null) ArPhase.READY else ArPhase.SEARCHING
                            if (c.arPhase != phase && c.arPhase != ArPhase.PLACED) c.arPhase = phase
                        }
                    }
                } catch (_: Throwable) { /* a lost frame must never crash the session loop */ }
            },
        ) {
            val rig = remember(engine) {
                PitchRig(engine, materialLoader, c.data, bitmaps).also {
                    it.setFloodlights(false); c.rig = it
                    it.controlVisible = c.pitchControlOn
                    it.root.scale = Scale(TABLE_SCALE)
                }
            }
            val anchorNode = remember(engine) { Node(engine).also { it.addChildNode(rig.root); it.isVisible = false } }
            NodeLifecycle(anchorNode, null)

            // flat reticle drawn on the detected surface
            val reticle = remember(engine) {
                val ring = materialLoader.createImageInstance(ImageTexture.Builder().bitmap(bitmaps.ringMint, TextureType.COLOR).build(engine))
                val glow = materialLoader.createImageInstance(ImageTexture.Builder().bitmap(bitmaps.baseHome, TextureType.COLOR).build(engine))
                val root = Node(engine)
                val r = PlaneNode(engine, size = Size(0.20f, 0.20f), normal = Direction(y = 1f), materialInstance = ring)
                val g = PlaneNode(engine, size = Size(0.28f, 0.28f), normal = Direction(y = 1f), materialInstance = glow)
                g.position = Position(0f, -0.002f, 0f)
                r.isShadowCaster = false; g.isShadowCaster = false; r.isShadowReceiver = false; g.isShadowReceiver = false
                root.addChildNode(g); root.addChildNode(r); root.isVisible = false
                Triple(root, r, g)
            }
            NodeLifecycle(reticle.first, null)

            val driver = remember(engine) {
                Node(engine).also { d ->
                    d.onFrame = { t ->
                        try {
                            val a = st.anchor
                            val rt = reticle.first
                            if (a == null) {
                                val h = st.lastHit
                                anchorNode.isVisible = false
                                if (h != null && c.arPhase == ArPhase.READY && h.trackable.trackingState == TrackingState.TRACKING) {
                                    val p = h.hitPose
                                    rt.worldPosition = Position(p.tx(), p.ty() + 0.003f, p.tz())
                                    rt.worldQuaternion = Quaternion(p.qx(), p.qy(), p.qz(), p.qw())
                                    val pulse = 1f + 0.08f * sin(t / 1e9f * 4f)
                                    reticle.second.scale = Scale(pulse)
                                    reticle.second.rotation = Rotation(y = ((t / 1e7f) % 360f))
                                    rt.isVisible = true
                                } else rt.isVisible = false
                            } else {
                                rt.isVisible = false
                                when (a.trackingState) {
                                    TrackingState.TRACKING -> {
                                        val p = a.pose
                                        anchorNode.worldPosition = Position(p.tx(), p.ty(), p.tz())
                                        anchorNode.worldQuaternion = Quaternion(p.qx(), p.qy(), p.qz(), p.qw())
                                        anchorNode.isVisible = true
                                        if (c.arNotice == "Anchor lost") c.arNotice = null
                                        if (st.needsInitialYaw) {
                                            st.yawDeg = Math.toDegrees(atan2(-st.camFwdZ.toDouble(), st.camFwdX.toDouble())).toFloat()
                                            st.needsInitialYaw = false
                                        }
                                        rig.root.scale = Scale(TABLE_SCALE * st.userScale)
                                        rig.root.rotation = Rotation(y = st.yawDeg)
                                    }
                                    TrackingState.PAUSED -> c.arNotice = "Tracking paused: move the phone slowly"
                                    TrackingState.STOPPED -> {
                                        st.reset(); c.arPhase = ArPhase.SEARCHING; c.selectNone()
                                        c.arNotice = "Anchor lost, scan again to re-place"
                                    }
                                }
                            }
                            c.onFrame(t, if (st.anchor != null) projector else null)
                        } catch (e: Throwable) {
                            android.util.Log.w("SpectrumAR", "frame error", e)
                        }
                    }
                }
            }
            NodeLifecycle(driver, null)
        }
        GestureLayer(
            SceneGestureCallbacks(
                onDown = { o -> st.anchor != null && !c.demoOn && c.beginDrag(o.x, o.y, projector) },
                onDrag = { o -> c.dragTo(o.x, o.y, projector) },
                onDragEnd = { c.endDrag() },
                onTap = { o ->
                    if (st.anchor == null) {
                        val h = st.lastHit
                        if (h != null) {
                            try {
                                st.anchor = h.createAnchor(); st.needsInitialYaw = true; st.userScale = 1f
                                c.arPhase = ArPhase.PLACED; c.arNotice = null; c.fireHaptic(Haptic.HEAVY)
                            } catch (e: Throwable) { c.arNotice = "Could not anchor here, try another spot"; c.fireHaptic(Haptic.REJECT) }
                        } else { c.arNotice = "No surface yet: keep scanning the table"; c.fireHaptic(Haptic.REJECT) }
                    } else c.tap(o.x, o.y, projector)
                },
                onOrbit = { },
                onTransform = { _, zoom, rot ->
                    if (st.anchor != null) {
                        st.userScale = (st.userScale * zoom).coerceIn(0.35f, 4f)
                        st.yawDeg -= rot
                    }
                },
            ),
            enabled = !c.demoOn,
        )
    }
}

fun describeFailure(f: Any): String = when (f::class.simpleName) {
    "ArCoreNotInstalled", "UserDeclinedInstall" -> "Google Play Services for AR is required. Install it from the Play Store and try again."
    "ApkTooOld", "SdkTooOld" -> "Google Play Services for AR needs an update."
    "DeviceNotCompatible" -> "This device does not support ARCore."
    "CameraNotAvailable" -> "The camera is in use by another app. Close it and try again."
    "ResourceExhausted" -> "The device ran out of resources for AR. Close other apps and retry."
    "Fatal" -> "ARCore hit a fatal error. Restart the app."
    else -> "The AR session could not start (${f::class.simpleName})."
}

fun trackingMessage(reason: String): String = when (reason) {
    "NONE" -> ""
    "BAD_STATE" -> "AR is recovering, hold still for a moment"
    "INSUFFICIENT_LIGHT" -> "Too dark: move to a brighter spot"
    "EXCESSIVE_MOTION" -> "Moving too fast: slow down"
    "INSUFFICIENT_FEATURES" -> "Point at a textured surface (wood, fabric)"
    "CAMERA_UNAVAILABLE" -> "Camera unavailable"
    else -> "Tracking lost: move the phone slowly"
}
