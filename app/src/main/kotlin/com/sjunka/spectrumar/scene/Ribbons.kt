package com.sjunka.spectrumar.scene

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.node.GeometryNode
import dev.romainguy.kotlin.math.Float2
import kotlin.math.sqrt

private val UP = Direction(0f, 1f, 0f)

/**
 * Flat strip lying on the pitch that follows a polyline: used for the ball trail and the 5 s heat
 * trail. Vertex and index counts never change, so updates rewrite existing GPU buffers.
 */
class Ribbon(private val engine: Engine, material: MaterialInstance, private val maxPoints: Int, private val y: Float) {
    private val verts = ArrayList<Geometry.Vertex>(maxPoints * 2)
    private val geometry: Geometry
    val node: GeometryNode

    init {
        // spread placeholder positions so Filament computes a non-empty bounding box (node is hidden until updated)
        repeat(maxPoints * 2) { k -> verts += Geometry.Vertex(Position(if (k % 2 == 0) -50f else 50f, y, if (k % 2 == 0) -30f else 30f), UP, Float2(0f, 0f)) }
        val idx = ArrayList<Int>()
        for (i in 0 until maxPoints - 1) {
            val a = i * 2; val b = a + 1; val c = a + 2; val d = a + 3
            idx += listOf(a, b, c, b, d, c)
        }
        geometry = Geometry.Builder(RenderableManager.PrimitiveType.TRIANGLES).vertices(verts.toList()).indices(idx).build(engine)
        node = GeometryNode(engine, geometry, material)
        node.setCulling(false)
        node.isShadowCaster = false
        node.isShadowReceiver = false
        node.setGlobalBlendOrderEnabled(true)
        node.isVisible = false
    }

    /** [xz] holds x,z pairs, oldest first. */
    fun update(xz: FloatArray, count: Int, tailWidth: Float, headWidth: Float) {
        if (count < 2) { node.isVisible = false; return }
        val n = minOf(count, maxPoints)
        val list = ArrayList<Geometry.Vertex>(maxPoints * 2)
        for (i in 0 until maxPoints) {
            val k = minOf(i, n - 1)
            val x = xz[k * 2]; val z = xz[k * 2 + 1]
            val pk = maxOf(k - 1, 0); val nk = minOf(k + 1, n - 1)
            var tx = xz[nk * 2] - xz[pk * 2]; var tz = xz[nk * 2 + 1] - xz[pk * 2 + 1]
            val l = sqrt(tx * tx + tz * tz)
            if (l < 1e-4f) { tx = 1f; tz = 0f } else { tx /= l; tz /= l }
            val u = if (i >= n) 1f else k / (n - 1f)
            val w = if (i >= n) 0f else (tailWidth + (headWidth - tailWidth) * u) * 0.5f
            val nx = -tz * w; val nz = tx * w
            list += Geometry.Vertex(Position(x + nx, y, z + nz), UP, Float2(u, 0f))
            list += Geometry.Vertex(Position(x - nx, y, z - nz), UP, Float2(u, 1f))
        }
        geometry.update(engine, list)
        node.isVisible = true
    }

    fun hide() { node.isVisible = false }
}

/** Animated row of chevrons from [from] to [to] that march towards the target. */
class DashedArrow(private val engine: Engine, material: MaterialInstance, private val dashes: Int, private val y: Float) {
    private val geometry: Geometry
    val node: GeometryNode

    init {
        val v = ArrayList<Geometry.Vertex>()
        repeat(dashes * 4) { k -> v += Geometry.Vertex(Position(if (k % 2 == 0) -50f else 50f, y, if (k % 2 == 0) -30f else 30f), UP, Float2(0f, 0f)) }
        val idx = ArrayList<Int>()
        for (i in 0 until dashes) { val a = i * 4; idx += listOf(a, a + 1, a + 2, a + 1, a + 3, a + 2) }
        geometry = Geometry.Builder(RenderableManager.PrimitiveType.TRIANGLES).vertices(v).indices(idx).build(engine)
        node = GeometryNode(engine, geometry, material)
        node.setCulling(false)
        node.isShadowCaster = false
        node.isShadowReceiver = false
        node.setGlobalBlendOrderEnabled(true)
        node.isVisible = false
    }

    /** [phase] in 0..1 moves the dashes along the arrow; [grow] 0..1 animates the arrow drawing itself. */
    fun update(fx: Float, fz: Float, tx: Float, tz: Float, phase: Float, grow: Float, width: Float, spacing: Float) {
        val dx = tx - fx; val dz = tz - fz
        val len = sqrt(dx * dx + dz * dz)
        if (len < 3f) { node.isVisible = false; return }
        val ux = dx / len; val uz = dz / len
        val nx = -uz; val nz = ux
        val shown = len * grow.coerceIn(0f, 1f)
        val list = ArrayList<Geometry.Vertex>(dashes * 4)
        val dashLen = spacing * 0.78f
        for (i in 0 until dashes) {
            var s = (i + phase) * spacing + 1.5f // keep the first dash off the carrier
            val visible = s + dashLen < shown && s + dashLen < len - 1.2f
            if (!visible) s = 0f
            val a = if (visible) s else 0f; val b = if (visible) s + dashLen else 0f
            val w = if (visible) width * (0.55f + 0.45f * (s / len)) * 0.5f else 0f
            fun p(t: Float, side: Float) = Position(fx + ux * t + nx * w * side, y, fz + uz * t + nz * w * side)
            list += Geometry.Vertex(p(a, 1f), UP, Float2(0f, 0f))
            list += Geometry.Vertex(p(a, -1f), UP, Float2(0f, 1f))
            list += Geometry.Vertex(p(b, 1f), UP, Float2(1f, 0f))
            list += Geometry.Vertex(p(b, -1f), UP, Float2(1f, 1f))
        }
        geometry.update(engine, list)
        node.isVisible = true
    }

    fun hide() { node.isVisible = false }
}

/** Horizontal quad (w along x, h along z) at y = 0 facing up; replaces PlaneNode so the geometry is fully under our control. */
fun flatQuad(engine: Engine, w: Float, h: Float, material: MaterialInstance): GeometryNode {
    val hw = w / 2; val hh = h / 2
    val v = listOf(
        Geometry.Vertex(Position(-hw, 0f, -hh), UP, Float2(0f, 0f)),
        Geometry.Vertex(Position(hw, 0f, -hh), UP, Float2(1f, 0f)),
        Geometry.Vertex(Position(-hw, 0f, hh), UP, Float2(0f, 1f)),
        Geometry.Vertex(Position(hw, 0f, hh), UP, Float2(1f, 1f)),
    )
    val g = Geometry.Builder(RenderableManager.PrimitiveType.TRIANGLES).vertices(v).indices(listOf(0, 2, 1, 1, 2, 3)).build(engine)
    return GeometryNode(engine, g, material).also { it.setCulling(false) }
}
