package com.sjunka.spectrumar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sjunka.spectrumar.app.AppMode
import com.sjunka.spectrumar.app.SpectrumController
import com.sjunka.spectrumar.core.CameraPreset
import com.sjunka.spectrumar.core.MarkerKind
import com.sjunka.spectrumar.core.Team
import com.sjunka.spectrumar.core.Timeline
import kotlin.math.roundToInt

@Composable
fun Hud(
    c: SpectrumController,
    modeEnabled: Boolean,
    onMode: (AppMode) -> Unit,
    arContent: @Composable BoxScope.() -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        if (c.mode == AppMode.AR) arContent()
        LabelsCanvas(c)
        PlayerCard(c)
        AnimatedVisibility(!c.demoOn, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                TopBar(c, modeEnabled, onMode, Modifier.align(Alignment.TopCenter))
                                Column(
                    Modifier.align(Alignment.BottomCenter).onSizeChanged { c.camera.reserveBottom(it.height / c.overlayHeight.coerceAtLeast(1f)) },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ToolRail(c, Modifier.align(Alignment.CenterHorizontally))
                    AnimatedVisibility(c.pitchControlOn, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) { ControlBar(c) }
                    AnimatedVisibility(c.whatIfOn, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) { WhatIfPanel(c) }
                    BottomPanel(c)
                }
            }
        }
        DemoOverlay(c)
    }
}

// ---- top bar ----------------------------------------------------------------------------

@Composable
private fun TopBar(c: SpectrumController, modeEnabled: Boolean, onMode: (AppMode) -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(Tok.Primary, 8.dp)
                Spacer(Modifier.width(8.dp))
                Text("SPECTRUM", color = Tok.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Spacer(Modifier.width(6.dp))
                Text("AR", color = Tok.Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            }
            Text("Optical tracking, on your table", color = Tok.Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.weight(1f))
        Segmented(c.mode, modeEnabled, onMode)
        Spacer(Modifier.width(10.dp))
        RoundButton(Icons24.Info, false, "About") { c.aboutOpen = true }
    }
}

@Composable
private fun Segmented(mode: AppMode, enabled: Boolean, onMode: (AppMode) -> Unit) {
    Row(Modifier.glass(radius = 22.dp).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((m, label) in listOf(AppMode.TABLE to "3D", AppMode.AR to "AR")) {
            val sel = m == mode
            Box(
                Modifier.clip(RoundedCornerShape(19.dp))
                    .background(if (sel) Tok.Primary else Color.Transparent)
                    .noRipple { if (enabled && !sel) onMode(m) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (sel) Tok.Bg else Tok.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
fun RoundButton(path: String, active: Boolean, desc: String, tint: Color = Tok.Text, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).glass(radius = 22.dp, alpha = 0.9f)
            .then(if (active) Modifier.border(1.5.dp, Tok.Primary, CircleShape) else Modifier)
            .noRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(path, if (active) Tok.Primary else tint, 22.dp) }
}

// ---- tool rail --------------------------------------------------------------------------

@Composable
private fun ToolRail(c: SpectrumController, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        RailButton(Icons24.Layers, "Control", c.pitchControlOn) { c.togglePitchControl() }
        RailButton(Icons24.Split, "What-if", c.whatIfOn) { c.toggleWhatIf() }
        RailButton(Icons24.Tag, "Numbers", c.numbersOn) { c.toggleNumbers() }
        RailButton(Icons24.Movie, "Demo", c.demoOn) { c.toggleDemo() }
    }
}

@Composable
private fun RailButton(path: String, label: String, active: Boolean, onClick: () -> Unit) {
    val bg by animateFloatAsState(if (active) 1f else 0f, tween(220), label = "rail")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Tok.Surface2.copy(0.9f), Tok.Surface.copy(0.9f))))
                .background(Tok.Primary.copy(alpha = 0.18f * bg))
                .border(1.dp, if (active) Tok.Primary else Tok.Border, RoundedCornerShape(18.dp))
                .noRipple(onClick),
            contentAlignment = Alignment.Center,
        ) { Ico(path, if (active) Tok.Primary else Tok.Text, 24.dp) }
        Text(label, color = if (active) Tok.Primary else Tok.Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp), fontWeight = FontWeight.Medium)
    }
}

// ---- bottom panel -----------------------------------------------------------------------

@Composable
private fun BottomPanel(c: SpectrumController) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 10.dp)
            .glass().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Timeline.clockTextWithTenths(c.timeSec.toDouble()), color = Tok.Text, style = Num, fontSize = 22.sp)
            Text("  /  ${Timeline.clockText(c.data.duration)}", color = Tok.Muted, style = Num, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            SmallButton(Icons24.Prev) { c.prevMarker() }
            Spacer(Modifier.width(8.dp))
            SmallButton(Icons24.Next) { c.nextMarker() }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(Tok.Surface2)
                    .border(1.dp, Tok.Border, RoundedCornerShape(17.dp)).noRipple { c.cycleSpeed() }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(speedLabel(c.speed), color = Tok.Primary, style = Num, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(6.dp))
        Scrubber(c)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Tok.Primary, Color(0xFF00B882))))
                    .noRipple { c.togglePlay() },
                contentAlignment = Alignment.Center,
            ) { Ico(if (c.playing) Icons24.Pause else Icons24.Play, Tok.Bg, 28.dp) }
            Spacer(Modifier.width(14.dp))
            if (c.mode == AppMode.TABLE) PresetRow(c) else Text(
                "Tap a player for live stats", color = Tok.Muted, fontSize = 13.sp,
            )
        }
    }
}

fun speedLabel(s: Float): String = when (s) {
    0.25f -> "0.25x"; 0.5f -> "0.5x"; 1f -> "1x"; 2f -> "2x"; 4f -> "4x"; else -> "${s}x"
}

@Composable
private fun SmallButton(path: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Tok.Surface2).border(1.dp, Tok.Border, CircleShape).noRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(path, Tok.Text, 18.dp) }
}

@Composable
private fun PresetRow(c: SpectrumController) {
    val presets = listOf(CameraPreset.TACTICAL, CameraPreset.STADIUM, CameraPreset.BALL, CameraPreset.CLOSE)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(presets) { p ->
            val sel = c.preset == p
            Box(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (sel) Tok.Primary.copy(alpha = 0.18f) else Tok.Surface2)
                    .border(1.dp, if (sel) Tok.Primary else Tok.Border, RoundedCornerShape(16.dp))
                    .noRipple { c.setPreset(p) }.padding(horizontal = 12.dp, vertical = 9.dp),
            ) { Text(p.label, color = if (sel) Tok.Primary else Tok.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
}

@Composable
private fun Scrubber(c: SpectrumController) {
    Canvas(
        Modifier.fillMaxWidth().height(34.dp)
            .pointerInput(c) { detectTapGestures { o -> c.seekFraction(o.x / size.width) } }
            .pointerInput(c) {
                detectHorizontalDragGestures(onDragStart = { c.clock.playing.let { } }) { change, _ ->
                    c.seekFraction(change.position.x / size.width); change.consume()
                }
            },
    ) {
        val cy = size.height / 2
        val track = 5.dp.toPx()
        drawRoundRect(Tok.Surface2, Offset(0f, cy - track / 2), Size(size.width, track), CornerRadius(track))
        val fr = (c.timeSec / c.data.duration.toFloat()).coerceIn(0f, 1f)
        drawRoundRect(
            Brush.horizontalGradient(listOf(Tok.Primary.copy(0.55f), Tok.Primary)),
            Offset(0f, cy - track / 2), Size(size.width * fr, track), CornerRadius(track),
        )
        for (m in c.markers) {
            val x = (m.time / c.data.duration).toFloat() * size.width
            val col = when (m.kind) {
                MarkerKind.GOAL -> Tok.Warn
                MarkerKind.SHOT -> Tok.Danger
                else -> if (m.team == Team.HOME) Tok.Home else Tok.Away
            }
            if (m.kind == MarkerKind.GOAL) {
                val r = 6.dp.toPx()
                val p = Path().apply { moveTo(x, cy - r); lineTo(x + r, cy); lineTo(x, cy + r); lineTo(x - r, cy); close() }
                drawPath(p, col)
            } else {
                drawCircle(Tok.Bg, 4.dp.toPx(), Offset(x, cy))
                drawCircle(col, 3.dp.toPx(), Offset(x, cy))
            }
        }
        val tx = size.width * fr
        drawCircle(Tok.Primary.copy(0.25f), 14.dp.toPx(), Offset(tx, cy))
        drawCircle(Tok.Text, 8.dp.toPx(), Offset(tx, cy))
        drawCircle(Tok.Primary, 4.5.dp.toPx(), Offset(tx, cy))
    }
}

// ---- pitch control bar ------------------------------------------------------------------

@Composable
private fun ControlBar(c: SpectrumController) {
    val share by animateFloatAsState(c.homeShare, tween(250), label = "share")
    val pct = (share * 100).roundToInt()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).glass().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$pct%", color = Tok.Home, style = Num, fontSize = 18.sp, modifier = Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            Text("PITCH CONTROL", color = Tok.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(4.dp))
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                val r = CornerRadius(size.height)
                drawRoundRect(Tok.Away, Offset.Zero, size, r)
                drawRoundRect(Brush.horizontalGradient(listOf(Tok.Home, Color(0xFF00C48A))), Offset.Zero, Size(size.width * share, size.height), r)
                drawRoundRect(Color.White, Offset(size.width * share - 1.5.dp.toPx(), -2.dp.toPx()), Size(3.dp.toPx(), size.height + 4.dp.toPx()), CornerRadius(2f))
            }
        }
        Text("${100 - pct}%", color = Tok.Away, style = Num, fontSize = 18.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
    }
}

// ---- what-if ----------------------------------------------------------------------------

@Composable
private fun WhatIfPanel(c: SpectrumController) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).glass().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("WHAT-IF", color = Tok.Warn, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.width(10.dp))
            Text(c.whatIfNote.ifEmpty { "Paused. Drag a player." }, color = Tok.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        if (c.options.isEmpty()) {
            Text("Drag any player on the pitch to test a different shape.", color = Tok.Muted, fontSize = 12.sp)
        }
        val maxEv = (c.options.maxOfOrNull { it.ev } ?: 0.0001).coerceAtLeast(0.0001)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((idx, o) in c.options.withIndex()) {
                val frac by animateFloatAsState((o.ev / maxEv).toFloat().coerceIn(0.04f, 1f), tween(300), label = "ev$idx")
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (o.best) Tok.Primary.copy(alpha = 0.12f) else Tok.Surface2.copy(alpha = 0.6f))
                        .border(1.dp, if (o.best) Tok.Primary else Tok.Border, RoundedCornerShape(14.dp)).padding(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(if (o.best) Tok.Primary else Tok.Surface2)
                                .border(1.dp, if (o.best) Tok.Primary else Tok.Border, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("${o.number}", color = if (o.best) Tok.Bg else Tok.Text, style = Num, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(6.dp))
                        Text("xT ${"%.3f".format(o.xt)}", color = if (o.best) Tok.Primary else Tok.Text, style = Num, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                        drawRoundRect(Tok.Surface, Offset.Zero, size, CornerRadius(size.height))
                        drawRoundRect(if (o.best) Tok.Primary else Tok.Muted.copy(0.8f), Offset.Zero, Size(size.width * frac, size.height), CornerRadius(size.height))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${(o.probability * 100).roundToInt()}% complete", color = Tok.Muted, style = Num, fontSize = 10.sp)
                }
            }
        }
    }
}

// ---- floating player card ----------------------------------------------------------------

@Composable
private fun PlayerCard(c: SpectrumController) {
    val s = c.selStats ?: return
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val density = LocalDensity.current
    val color = if (s.team == Team.HOME) Tok.Home else Tok.Away
    Box(
        Modifier
            .onSizeChanged { size = it }
            .graphicsLayer {
                c.overlayTick // redraw each frame without recomposing
                val a = c.cardAnchor
                if (a == null) { alpha = 0f } else {
                    alpha = 1f
                    val margin = 12.dp.toPx()
                    translationX = (a.x - size.width / 2f).coerceIn(margin, (c.overlayWidth - size.width - margin).coerceAtLeast(margin))
                    translationY = (a.y - size.height - 14.dp.toPx()).coerceIn(122.dp.toPx(), c.overlayHeight.coerceAtLeast(500f))
                }
            }
            .width(176.dp)
            .glass(radius = 20.dp, alpha = 0.92f)
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Text("${s.number}", color = Tok.Bg, style = Num, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(if (s.team == Team.HOME) "HOME" else "AWAY", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("Player ${s.number}", color = Tok.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(26.dp).noRipple { c.clearSelection() }, contentAlignment = Alignment.Center) { Ico(Icons24.Close, Tok.Muted, 16.dp) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${s.speedKmh}", color = Tok.Text, style = Num, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Text(" km/h", color = Tok.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 7.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("DISTANCE", color = Tok.Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text("${s.distanceM} m", color = Tok.Text, style = Num, fontSize = 14.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("TOP SPEED", color = Tok.Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text("${s.topKmh} km/h", color = Tok.Warn, style = Num, fontSize = 14.sp)
                }
            }
        }
    }
}

// ---- jersey numbers ----------------------------------------------------------------------

@Composable
private fun LabelsCanvas(c: SpectrumController) {
    val dens = LocalDensity.current.density
    val textPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        }
    }
    Canvas(Modifier.fillMaxSize().onSizeChanged { c.overlayWidth = it.width.toFloat(); c.overlayHeight = it.height.toFloat() }) {
        c.overlayTick
        for (l in c.labels) {
            val team = c.data.players[l.index].team
            val col = if (team == Team.HOME) Tok.Home else Tok.Away
            val r = (if (l.priority) 10.5f else 8.5f) * dens * l.scale
            val cx = l.x; val cy = l.y - r - 2f * dens
            val a = l.opacity.coerceIn(0f, 1f)
            drawCircle(Tok.Bg.copy(alpha = 0.78f * a), r, Offset(cx, cy))
            drawCircle(col.copy(alpha = a), r, Offset(cx, cy), style = Stroke(width = (if (l.priority) 2f else 1.3f) * dens))
            textPaint.color = (if (l.priority) col else Tok.Text).copy(alpha = a).toArgb()
            textPaint.textSize = (if (l.priority) 11f else 9f) * dens * l.scale
            drawContext.canvas.nativeCanvas.drawText("${c.data.players[l.index].number}", cx, cy + textPaint.textSize * 0.36f, textPaint)
        }
    }
}

// ---- demo --------------------------------------------------------------------------------

@Composable
private fun DemoOverlay(c: SpectrumController) {
    val bars by animateFloatAsState(if (c.demoOn) 1f else 0f, tween(600), label = "bars")
    if (bars <= 0.001f) return
    Box(Modifier.fillMaxSize().noRipple { c.setDemo(false) }) {
        Box(Modifier.fillMaxWidth().height((96 * bars).dp).background(Color.Black).align(Alignment.TopCenter))
        Box(Modifier.fillMaxWidth().height((120 * bars).dp).background(Color.Black).align(Alignment.BottomCenter))
        Row(
            Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(start = 18.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(Tok.Danger, 8.dp); Spacer(Modifier.width(8.dp))
            Text("DEMO", color = Tok.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 22.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val cap = c.caption
            val goal = cap == "GOAL"
            Text(
                cap, color = if (goal) Tok.Warn else Tok.Text, fontSize = if (goal) 34.sp else 18.sp,
                fontWeight = if (goal) FontWeight.ExtraBold else FontWeight.SemiBold, letterSpacing = if (goal) 6.sp else 0.3.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text("Tap anywhere to exit", color = Tok.Muted, fontSize = 11.sp)
        }
    }
}
