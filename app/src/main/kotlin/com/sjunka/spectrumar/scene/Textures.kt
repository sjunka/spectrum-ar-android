package com.sjunka.spectrumar.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.sjunka.spectrumar.core.MatchData
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/** Procedural bitmaps: no binary art assets ship with the app. */
object Textures {
    /** Plane that carries the pitch texture extends this far past the touchlines (metres). */
    const val MARGIN = 7f
    const val PX_PER_M = 16

    const val MINT = 0xFF00E5A0.toInt()
    const val VIOLET = 0xFF7C6CFF.toInt()
    const val WARN = 0xFFFFB84D.toInt()
    const val DANGER = 0xFFFF4D6D.toInt()

    private fun argb(a: Float, c: Int) = Color.argb((a * 255).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    /** Pitch with mowing stripes, glowing markings and a soft fade into the page background. */
    fun pitch(): Bitmap {
        val L = MatchData.PITCH_LENGTH; val W = MatchData.PITCH_WIDTH
        val tw = ((L + 2 * MARGIN) * PX_PER_M).toInt(); val th = ((W + 2 * MARGIN) * PX_PER_M).toInt()
        val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = PX_PER_M.toFloat()
        fun px(x: Float) = (x + L / 2 + MARGIN) * s
        fun py(y: Float) = (y + W / 2 + MARGIN) * s
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // surround
        c.drawColor(0xFF0A1220.toInt())
        // mowing stripes along the length
        val stripes = 14
        for (i in 0 until stripes) {
            p.shader = null
            p.color = if (i % 2 == 0) 0xFF0B3A33.toInt() else 0xFF0D463C.toInt()
            c.drawRect(px(-L / 2 + i * L / stripes), py(-W / 2), px(-L / 2 + (i + 1) * L / stripes), py(W / 2), p)
        }
        // soft lighting pools (stadium flood look)
        p.shader = RadialGradient(px(0f), py(0f), L * s * 0.62f, intArrayOf(argb(0.20f, 0xFF7CFFD8.toInt()), argb(0f, 0xFF7CFFD8.toInt())), null, Shader.TileMode.CLAMP)
        c.drawRect(px(-L / 2), py(-W / 2), px(L / 2), py(W / 2), p)
        p.shader = LinearGradient(0f, py(-W / 2), 0f, py(W / 2), intArrayOf(argb(0.22f, Color.BLACK), 0, argb(0.22f, Color.BLACK)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(px(-L / 2), py(-W / 2), px(L / 2), py(W / 2), p)
        p.shader = null
        // fade of the surround into transparent dark edge
        val edge = Paint().apply {
            shader = RadialGradient(tw / 2f, th / 2f, tw * 0.5f, intArrayOf(0, 0, argb(1f, 0xFF070B14.toInt())), floatArrayOf(0f, 0.80f, 1f), Shader.TileMode.CLAMP)
        }
        // markings: wide soft halo then crisp line
        val lineW = 0.42f
        fun strokeAll(paint: Paint, draw: (Paint) -> Unit) = draw(paint)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = argb(0.16f, MINT); strokeWidth = lineW * 4.2f * s }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = argb(0.94f, 0xFFB8FFE8.toInt()); strokeWidth = lineW * s; strokeCap = Paint.Cap.ROUND }
        fun markings(paint: Paint) {
            c.drawRect(px(-L / 2), py(-W / 2), px(L / 2), py(W / 2), paint)
            c.drawLine(px(0f), py(-W / 2), px(0f), py(W / 2), paint)
            c.drawCircle(px(0f), py(0f), 9.15f * s, paint)
            for (sg in listOf(-1f, 1f)) {
                val gx = sg * L / 2
                // penalty area 16.5 x 40.32, goal area 5.5 x 18.32
                c.drawRect(minOf(px(gx), px(gx - sg * 16.5f)), py(-20.16f), maxOf(px(gx), px(gx - sg * 16.5f)), py(20.16f), paint)
                c.drawRect(minOf(px(gx), px(gx - sg * 5.5f)), py(-9.16f), maxOf(px(gx), px(gx - sg * 5.5f)), py(9.16f), paint)
                // penalty arc: radius 9.15 around the spot, outside the box
                val spotX = gx - sg * 11f
                val a = Math.toDegrees(acos(((16.5f - 11f) / 9.15f).toDouble())).toFloat()
                val rect = RectF(px(spotX) - 9.15f * s, py(0f) - 9.15f * s, px(spotX) + 9.15f * s, py(0f) + 9.15f * s)
                c.drawArc(rect, if (sg > 0) 180f - a else -a, 2 * a, false, paint)
                // corner arcs at (gx, +-W/2)
            }
            for (cx in listOf(-L / 2, L / 2)) for (cy in listOf(-W / 2, W / 2)) {
                val r = RectF(px(cx) - s, py(cy) - s, px(cx) + s, py(cy) + s)
                val start = (if (cx < 0) 0f else 180f) + (if (cy < 0) 0f else -90f).let { if (cx < 0) it else -it }
                val st = when {
                    cx < 0 && cy < 0 -> 0f
                    cx > 0 && cy < 0 -> 90f
                    cx > 0 && cy > 0 -> 180f
                    else -> 270f
                }
                c.drawArc(r, st, 90f, false, paint)
            }
        }
        markings(halo)
        markings(line)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0.95f, 0xFFB8FFE8.toInt()) }
        c.drawCircle(px(0f), py(0f), 0.45f * s, dot)
        c.drawCircle(px(-L / 2 + 11f), py(0f), 0.4f * s, dot)
        c.drawCircle(px(L / 2 - 11f), py(0f), 0.4f * s, dot)
        // goal mouths: tinted team colours behind each goal line
        val goalP = Paint(Paint.ANTI_ALIAS_FLAG)
        goalP.color = argb(0.55f, MINT); c.drawRect(px(-L / 2 - 2.4f), py(-3.66f), px(-L / 2), py(3.66f), goalP)
        goalP.color = argb(0.55f, VIOLET); c.drawRect(px(L / 2), py(-3.66f), px(L / 2 + 2.4f), py(3.66f), goalP)
        c.drawRect(0f, 0f, tw.toFloat(), th.toFloat(), edge)
        return bmp
    }

    /** Soft radial glow, premultiplied-friendly. */
    fun glow(color: Int, size: Int = 128, core: Float = 0.25f, peak: Float = 0.95f): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(size / 2f, size / 2f, size / 2f,
            intArrayOf(argb(peak, color), argb(peak * 0.55f, color), argb(0f, color)),
            floatArrayOf(core, 0.62f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        return b
    }

    /** Thin glowing ring (selection / ball carrier). */
    fun ring(color: Int, size: Int = 256, radiusFrac: Float = 0.38f, thickness: Float = 0.035f): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        p.color = argb(0.22f, color); p.strokeWidth = size * thickness * 4.5f
        c.drawCircle(size / 2f, size / 2f, size * radiusFrac, p)
        p.color = argb(0.95f, color); p.strokeWidth = size * thickness
        c.drawCircle(size / 2f, size / 2f, size * radiusFrac, p)
        // four tick marks outward for a "targeting" feel
        p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = size * thickness * 1.3f
        for (i in 0 until 4) {
            val a = i * PI.toFloat() / 2f + PI.toFloat() / 4f
            val r0 = size * (radiusFrac + 0.04f); val r1 = size * (radiusFrac + 0.095f)
            c.drawLine(size / 2f + cos(a) * r0, size / 2f + sin(a) * r0, size / 2f + cos(a) * r1, size / 2f + sin(a) * r1, p)
        }
        return b
    }

    /** Ribbon texture: u runs along the strip (0 = tail, 1 = head), v across. */
    fun trail(color: Int, w: Int = 128, h: Int = 32): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        for (y in 0 until h) {
            val v = abs((y + 0.5f) / h - 0.5f) * 2f
            val across = (1f - v * v).coerceIn(0f, 1f)
            for (x in 0 until w) {
                val u = (x + 0.5f) / w
                val a = (u * u) * across
                val hot = (u * u * u)
                val r = Color.red(color) + ((255 - Color.red(color)) * hot).toInt()
                val g = Color.green(color) + ((255 - Color.green(color)) * hot).toInt()
                val bl = Color.blue(color) + ((255 - Color.blue(color)) * hot).toInt()
                px[y * w + x] = Color.argb((a * 235).toInt().coerceIn(0, 255), r.coerceIn(0, 255), g.coerceIn(0, 255), bl.coerceIn(0, 255))
            }
        }
        b.setPixels(px, 0, w, 0, 0, w, h)
        return b
    }

    /** Dash with a chevron head for the animated pass arrow. */
    fun chevron(color: Int, size: Int = 64): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = argb(0.28f, color)
        val path = android.graphics.Path().apply {
            moveTo(size * 0.05f, size * 0.12f); lineTo(size * 0.62f, size * 0.12f); lineTo(size * 0.95f, size * 0.5f)
            lineTo(size * 0.62f, size * 0.88f); lineTo(size * 0.05f, size * 0.88f); lineTo(size * 0.38f, size * 0.5f); close()
        }
        c.drawPath(path, p)
        val q = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = argb(0.98f, color); }
        val inner = android.graphics.Path().apply {
            moveTo(size * 0.14f, size * 0.24f); lineTo(size * 0.58f, size * 0.24f); lineTo(size * 0.84f, size * 0.5f)
            lineTo(size * 0.58f, size * 0.76f); lineTo(size * 0.14f, size * 0.76f); lineTo(size * 0.40f, size * 0.5f); close()
        }
        c.drawPath(inner, q)
        return b
    }
}
