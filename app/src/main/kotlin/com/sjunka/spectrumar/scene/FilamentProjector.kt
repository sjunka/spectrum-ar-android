package com.sjunka.spectrumar.scene

import androidx.compose.ui.geometry.Offset
import com.google.android.filament.View
import com.sjunka.spectrumar.app.SceneProjector
import com.sjunka.spectrumar.core.Vec2
import com.sjunka.spectrumar.core.Vec3
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.utils.screenToRay
import io.github.sceneview.utils.worldToScreen
import kotlin.math.abs

/** Maps between the rig's local pitch metres and screen pixels through the active Filament view and camera. */
class FilamentProjector(
    private val view: View,
    private val rig: () -> PitchRig?,
    private val cameraWorld: () -> Position,
    override val density: Float,
) : SceneProjector {
    override val width: Float get() = view.viewport.width.toFloat()
    override val height: Float get() = view.viewport.height.toFloat()

    override fun toScreen(x: Float, y: Float, z: Float): Offset? {
        val root = rig()?.root ?: return null
        val w = root.getWorldPosition(Position(x, y, z))
        val s = view.worldToScreen(w) ?: return null
        return Offset(s.x, s.y)
    }

    override fun groundHit(sx: Float, sy: Float): Vec2? {
        val root = rig()?.root ?: return null
        val ray = view.screenToRay(sx, sy) ?: return null
        val o = root.getLocalPosition(ray.origin)
        val e = root.getLocalPosition(ray.origin + ray.direction)
        val dy = e.y - o.y
        if (abs(dy) < 1e-7f) return null
        val t = -o.y / dy
        if (t < 0f) return null
        return Vec2(o.x + (e.x - o.x) * t, o.z + (e.z - o.z) * t)
    }

    override fun cameraLocal(): Vec3 {
        val root = rig()?.root ?: return Vec3(0f, 100f, 100f)
        val p = root.getLocalPosition(cameraWorld())
        return Vec3(p.x, p.y, p.z)
    }
}
