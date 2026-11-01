package com.sjunka.spectrumar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize

/** Design tokens shared by the three Genius Sports concept prototypes. */
object Tok {
    val Bg = Color(0xFF070B14)
    val Surface = Color(0xFF0F1626)
    val Surface2 = Color(0xFF172036)
    val Border = Color(0xFF22304D)
    val Primary = Color(0xFF00E5A0)
    val Secondary = Color(0xFF7C6CFF)
    val Danger = Color(0xFFFF4D6D)
    val Warn = Color(0xFFFFB84D)
    val Text = Color(0xFFE8EEF9)
    val Muted = Color(0xFF7C8AA5)
    val Home = Primary
    val Away = Secondary
    val Glass = Color(0xE60F1626)
    val Radius = 20.dp
}

val Tabular = TextStyle(fontFeatureSettings = "tnum", fontFamily = FontFamily.Default)

@Composable
fun SpectrumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Tok.Primary, secondary = Tok.Secondary, background = Tok.Bg, surface = Tok.Surface,
            surfaceVariant = Tok.Surface2, onSurface = Tok.Text, onBackground = Tok.Text, error = Tok.Danger,
            outline = Tok.Border, onPrimary = Tok.Bg, onSecondary = Tok.Bg,
        ),
        content = content,
    )
}

/** Translucent rounded card used for every floating piece of chrome. */
fun Modifier.glass(radius: Dp = Tok.Radius, alpha: Float = 0.86f): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(Brush.verticalGradient(listOf(Tok.Surface2.copy(alpha = alpha), Tok.Surface.copy(alpha = alpha))))
    .border(BorderStroke(1.dp, Tok.Border.copy(alpha = 0.9f)), RoundedCornerShape(radius))

fun Modifier.noRipple(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

/** Minimal icon set drawn from 24x24 path data so no drawable assets are needed. */
object Icons24 {
    const val Play = "M8,5v14l11,-7z"
    const val Pause = "M6,19h4V5H6v14zm8,-14v14h4V5h-4z"
    const val Next = "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z"
    const val Prev = "M6,6h2v12H6zm3.5,6l8.5,6V6z"
    const val Info = "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2L11,7h2v2z"
    const val Layers = "M11.99,18.54l-7.37,-5.73L3,14.07l9,7 9,-7 -1.63,-1.27 -7.38,5.74zM12,16l7.36,-5.73L21,9l-9,-7 -9,7 1.63,1.27L12,16z"
    const val Close = "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"
    const val Movie = "M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4h-4z"
    const val Split = "M14,4l2.29,2.29 -2.88,2.88 1.42,1.42 2.88,-2.88L20,10V4zM10,4H4v6l2.29,-2.29 4.71,4.7V20h2v-8.41l-5.29,-5.3z"
    const val Cube = "M3,4v5h2V5h4V3H4C3.45,3 3,3.45 3,4zM5,15H3v5c0,0.55 0.45,1 1,1h5v-2H5V15zM19,19h-4v2h5c0.55,0 1,-0.45 1,-1v-5h-2V19zM19,3h-5v2h4v4h2V4C20,3.45 19.55,3 19,3zM12,6.17L7,8.9v5.59l5,2.73 5,-2.73V8.9L12,6.17zM11,15.8l-3,-1.64v-3.5l3,1.64V15.8zM12,11.1 L9.2,9.58 12,8.06l2.8,1.52L12,11.1zM16,14.16l-3,1.64v-3.5l3,-1.64V14.16z"
    const val Camera = "M17,10.5V7c0,-0.55 -0.45,-1 -1,-1H4c-0.55,0 -1,0.45 -1,1v10c0,0.55 0.45,1 1,1h12c0.55,0 1,-0.45 1,-1v-3.5l4,4v-11l-4,4z"
    const val Tag = "M20,10V8h-4V4h-2v4h-4V4H8v4H4v2h4v4H4v2h4v4h2v-4h4v4h2v-4h4v-2h-4v-4h4zM14,14h-4v-4h4v4z"
    const val Refresh = "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"
}

@Composable
fun Ico(path: String, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val p: Path = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) { drawPath(p, tint, style = Fill) }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(50)).background(color))
}

val Num = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
