package com.sjunka.spectrumar.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sjunka.spectrumar.app.AppMode
import com.sjunka.spectrumar.app.ArPhase
import com.sjunka.spectrumar.app.Haptic
import com.sjunka.spectrumar.app.SpectrumController
import com.sjunka.spectrumar.ar.ArAvailability
import com.sjunka.spectrumar.ar.ArScene
import com.sjunka.spectrumar.ar.ArSupport
import com.sjunka.spectrumar.core.MatchData
import com.sjunka.spectrumar.scene.RigBitmaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Loaded(val data: MatchData, val bitmaps: RigBitmaps)

private fun hapticConstant(h: Haptic): Int = when (h) {
    Haptic.TICK -> HapticFeedbackConstants.CLOCK_TICK
    Haptic.CLICK -> HapticFeedbackConstants.VIRTUAL_KEY
    Haptic.CONFIRM -> if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
    Haptic.HEAVY -> HapticFeedbackConstants.LONG_PRESS
    Haptic.REJECT -> if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
}

private fun fire(view: View, h: Haptic) {
    try { view.performHapticFeedback(hapticConstant(h)) } catch (_: Throwable) { }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val view = LocalView.current
    var loaded by remember { mutableStateOf<Loaded?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var splashDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val t0 = System.currentTimeMillis()
        try {
            loaded = withContext(Dispatchers.Default) {
                val json = ctx.assets.open("match.json").use { it.readBytes().decodeToString() }
                val bin = ctx.assets.open("tracking.bin").use { it.readBytes() }
                Loaded(MatchData.fromJson(json, bin), RigBitmaps.generate())
            }
        } catch (e: Throwable) {
            loadError = "Could not load the match data: ${e.message}"
        }
        val left = 1700 - (System.currentTimeMillis() - t0)
        if (left > 0) delay(left)
        splashDone = true
    }

    Box(Modifier.fillMaxSize().background(Tok.Bg)) {
        val l = loaded
        if (l != null) Main(l)
        Crossfade(targetState = !(splashDone && (l != null || loadError != null)), animationSpec = tween(500), label = "splash") { show ->
            if (show) SplashScreen() else if (loadError != null && l == null) ErrorScreen(loadError!!)
        }
    }
}

@Composable
private fun Main(l: Loaded) {
    val view = LocalView.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = remember(l) { SpectrumController(l.data, scope) { h -> fire(view, h) } }
    var notice by remember { mutableStateOf<String?>(null) }
    var checkingAr by remember { mutableStateOf(false) }

    fun enterAr() {
        scope.launch {
            checkingAr = true
            val a = ArSupport.check(ctx)
            checkingAr = false
            when (a) {
                ArAvailability.SUPPORTED, ArAvailability.UNKNOWN -> {
                    c.mode = AppMode.AR; c.arNotice = null; notice = null
                    c.fireHaptic(Haptic.CONFIRM)
                }
                ArAvailability.NEEDS_INSTALL -> {
                    val act = ctx as? Activity
                    val asked = act != null && ArSupport.requestInstall(act)
                    notice = if (asked) "Install Google Play Services for AR, then tap AR again." else
                        "Google Play Services for AR is required for AR mode. The 3D table still works."
                    c.fireHaptic(Haptic.REJECT)
                }
                ArAvailability.UNSUPPORTED -> {
                    notice = "AR is not available on this device (ARCore unsupported). The 3D table mode works fully."
                    c.fireHaptic(Haptic.REJECT)
                }
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enterAr() else {
            notice = "Camera permission denied, so AR mode cannot start. Allow it in Settings to use AR. The 3D table still works."
            c.fireHaptic(Haptic.REJECT)
        }
    }

    fun onMode(m: AppMode) {
        if (m == AppMode.TABLE) { c.mode = AppMode.TABLE; c.setPreset(c.preset, false); notice = null; c.arNotice = null; c.fireHaptic(Haptic.CLICK); return }
        if (c.demoOn) c.setDemo(false)
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) enterAr()
        else permission.launch(Manifest.permission.CAMERA)
    }

    BackHandler(enabled = c.aboutOpen || c.demoOn || c.mode == AppMode.AR) {
        when {
            c.aboutOpen -> c.aboutOpen = false
            c.demoOn -> c.setDemo(false)
            else -> onMode(AppMode.TABLE)
        }
    }

    androidx.compose.runtime.DisposableEffect(c) { onDispose { c.shutdown() } }

    Box(Modifier.fillMaxSize().background(Tok.Bg)) {
        // No cross-fade: two live scenes would fight over the shared rig and frame driver.
        when (c.mode) {
            AppMode.TABLE -> TableScene(c, l.bitmaps)
            AppMode.AR -> ArScene(c, l.bitmaps, onFailure = { msg ->
                c.mode = AppMode.TABLE; notice = "$msg The 3D table still works."
            })
        }
        Hud(c, modeEnabled = !checkingAr, onMode = ::onMode)

        // AR guidance
        if (c.mode == AppMode.AR && !c.demoOn) {
            val msg = c.arNotice?.takeIf { it.isNotBlank() } ?: when (c.arPhase) {
                ArPhase.SEARCHING -> "Move the phone slowly to scan a flat surface"
                ArPhase.READY -> "Tap to place the table"
                ArPhase.PLACED -> "Pinch to scale, twist to rotate, tap a player"
            }
            Banner(msg, c.arPhase == ArPhase.READY, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 78.dp))
        }
        LaunchedEffect(notice) { val n = notice; if (n != null) { delay(7000); if (notice == n) notice = null } }
        var lastNotice by remember { mutableStateOf("") }
        if (notice != null) lastNotice = notice ?: ""
        AnimatedVisibility(notice != null, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 78.dp, start = 16.dp, end = 16.dp).noRipple { notice = null }) {
                Column(Modifier.glass().border(1.dp, Tok.Warn.copy(0.6f), RoundedCornerShape(Tok.Radius)).padding(14.dp)) {
                    Text(lastNotice, color = Tok.Text, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
        AnimatedVisibility(c.aboutOpen, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) { AboutSheet { c.aboutOpen = false } }
    }
}

@Composable
private fun Banner(text: String, hot: Boolean, modifier: Modifier) {
    Box(modifier.padding(horizontal = 16.dp).glass(radius = 22.dp).border(1.dp, if (hot) Tok.Primary else Tok.Border, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text, color = if (hot) Tok.Primary else Tok.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SplashScreen() {
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pp")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Tok.Bg, Color(0xFF0A1424), Tok.Bg)))) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(120.dp).scale(pulse), contentAlignment = Alignment.Center) {
                Box(Modifier.size(120.dp).clip(RoundedCornerShape(36.dp)).background(Brush.radialGradient(listOf(Tok.Primary.copy(0.28f), Color.Transparent))))
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(com.sjunka.spectrumar.R.drawable.ic_splash), null, Modifier.size(120.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Row {
                Text("SPECTRUM", color = Tok.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
                Spacer(Modifier.width(10.dp))
                Text("AR", color = Tok.Primary, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("Optical tracking, on your table", color = Tok.Muted, fontSize = 13.sp)
        }
        Text(
            "Independent concept prototype. Not affiliated with Genius Sports.", color = Tok.Muted, fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 28.dp, start = 32.dp, end = 32.dp),
        )
    }
}

@Composable
private fun ErrorScreen(msg: String) {
    Box(Modifier.fillMaxSize().background(Tok.Bg).padding(32.dp), contentAlignment = Alignment.Center) {
        Text(msg, color = Tok.Danger, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AboutSheet(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.62f)).noRipple(onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(max = 640.dp).glass(alpha = 0.97f).noRipple { }.padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("About Spectrum AR", color = Tok.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RoundButton(Icons24.Close, false, "Close") { onClose() }
            }
            Text(
                "An independent portfolio concept: optical tracking data as a 3D tactical board you can orbit, scrub and place on a real table in AR. It is not an official product and is not affiliated with Genius Sports.",
                color = Tok.Text, fontSize = 14.sp, lineHeight = 20.sp,
            )
            Sect("What you are seeing")
            Para("Real player and ball tracking at 25 Hz from one match, replayed live. Pitch control is computed on the phone from each player's estimated arrival time at every point of the pitch. What-if options rank pass targets by expected threat (xT) weighted by pass success probability, and you can drag a player to see the options change.")
            Sect("Controls")
            Para("One finger orbits, two fingers pan and pinch to zoom. Tap a player for stats. In what-if mode, drag a player. The demo button runs a hands-free tour. In AR, tap a detected surface to place the table, pinch to scale and twist to rotate.")
            Sect("Data credits")
            Para("Tracking data: Metrica Sports open sample data, Sample_Game_1 (github.com/metrica-sports/sample-data).")
            Para("Expected threat grid: Karun Singh, open xT model (karun.in/blog/expected-threat.html).")
            Sect("Honest notes")
            Para("The AI-style insights are local models on open data, not a live feed. AR mode needs an ARCore supported phone and camera permission; without them the app stays in 3D table mode.")
            Spacer(Modifier.height(4.dp))
            Text("Independent concept prototype. Not affiliated with Genius Sports.", color = Tok.Muted, fontSize = 11.sp)
        }
    }
}

@Composable private fun Sect(t: String) = Text(t.uppercase(), color = Tok.Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 6.dp))
@Composable private fun Para(t: String) = Text(t, color = Tok.Text.copy(alpha = 0.86f), fontSize = 13.sp, lineHeight = 19.sp)
