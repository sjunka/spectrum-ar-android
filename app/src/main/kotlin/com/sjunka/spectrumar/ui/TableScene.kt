package com.sjunka.spectrumar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.google.android.filament.LightManager
import com.sjunka.spectrumar.app.SpectrumController
import com.sjunka.spectrumar.core.CameraMath
import com.sjunka.spectrumar.scene.FilamentProjector
import com.sjunka.spectrumar.scene.PitchRig
import com.sjunka.spectrumar.scene.RigBitmaps
import io.github.sceneview.FrameRatePolicy
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.createEnvironment
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberView
import io.github.sceneview.math.Direction

/** TABLE 3D mode: the shared PitchRig rendered in a SceneView with a scripted/orbit camera. */
@Composable
fun TableScene(c: SpectrumController, bitmaps: RigBitmaps, modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val view = rememberView(engine)
    val cameraNode = rememberCameraNode(engine) { near = 1.0f; far = 900f }
    val density = LocalDensity.current.density
    val environment = rememberEnvironment(environmentLoader) {
        createEnvironment(environmentLoader).also { it.skybox?.setColor(0.0275f, 0.0431f, 0.0784f, 1f) }
    }
    val mainLight = rememberMainLightNode(engine) { intensity = 95_000f; isShadowCaster = false }
    val fillLight = rememberFillLightNode(engine) { intensity = 38_000f; isShadowCaster = false }

    val projector = remember(view) { FilamentProjector(view, { c.rig }, { cameraNode.worldPosition }, density) }

    SideEffect {
        view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply { enabled = false }
        view.bloomOptions = view.bloomOptions.apply { enabled = true; strength = 0.22f; lensFlare = false }
        view.setShadowingEnabled(false)
    }
    DisposableEffect(c) {
        c.onCamera = { p ->
            val e = p.eye
            cameraNode.position = Position(e.x, e.y, e.z)
            cameraNode.lookAt(Position(p.tx, p.ty, p.tz), Direction(0f, 1f, 0f), smooth = false)
        }
        onDispose { c.onCamera = null; c.rig = null }
    }

    Box(modifier.fillMaxSize()) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine, modelLoader = modelLoader, materialLoader = materialLoader, environmentLoader = environmentLoader,
            view = view, cameraNode = cameraNode, cameraManipulator = null, environment = environment,
            mainLightNode = mainLight, fillLightNode = fillLight,
            frameRatePolicy = FrameRatePolicy.Continuous(),
            renderQuality = RenderQuality.Default,
            autoCenterContent = false,
            onGestureListener = null,
        ) {
            val rig = remember(engine) { PitchRig(engine, materialLoader, c.data, bitmaps).also { c.rig = it; it.controlVisible = c.pitchControlOn } }
            NodeLifecycle(rig.root, null)
            val driver = remember(engine) { Node(engine).also { d -> d.onFrame = { t -> c.onFrame(t, projector) } } }
            NodeLifecycle(driver, null)
        }
        GestureLayer(
            SceneGestureCallbacks(
                onDown = { o -> !c.demoOn && c.beginDrag(o.x, o.y, projector) },
                onDrag = { o -> c.dragTo(o.x, o.y, projector) },
                onDragEnd = { c.endDrag() },
                onTap = { o -> if (!c.demoOn) c.tap(o.x, o.y, projector) },
                onOrbit = { d -> if (!c.demoOn) c.camera.orbit(-d.x * 0.006f, d.y * 0.004f) },
                onTransform = { pan, zoom, _ ->
                    if (!c.demoOn) {
                        val mPerPx = 2f * c.camera.current.dist * CameraMath.DEFAULT_TAN_V / projector.height.coerceAtLeast(1f)
                        c.camera.pan(pan.x * mPerPx, pan.y * mPerPx)
                        if (zoom != 1f) c.camera.zoom(zoom)
                    }
                },
            ),
        )
    }
}
