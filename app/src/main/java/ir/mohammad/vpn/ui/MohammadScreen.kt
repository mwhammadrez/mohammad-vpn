package ir.mohammad.vpn.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.mohammad.vpn.data.LogEntry
import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.data.TunnelError
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.ui.theme.Chakra
import ir.mohammad.vpn.ui.theme.ChakraSemi
import ir.mohammad.vpn.ui.theme.MohammadTheme
import ir.mohammad.vpn.ui.theme.Orbitron
import ir.mohammad.vpn.ui.theme.Palette
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MohammadScreen(
    state: TunnelState,
    traffic: Pair<Long, Long>,
    logs: List<LogEntry>,
    profiles: List<Profile>,
    selected: Profile?,
    pingMs: Long?,
    pingBusy: Boolean,
    testRunning: Boolean,
    testReport: String?,
    crashReport: String? = null,
    onDismissCrash: () -> Unit = {},
    onToggle: () -> Unit,
    onSelect: (Profile) -> Unit,
    onPing: () -> Unit,
    onSelfTest: () -> Unit,
) {
    MohammadTheme {
        val down = remember { mutableStateListOf<Float>() }
        val up = remember { mutableStateListOf<Float>() }
        val latest = rememberUpdatedState(traffic)

        LaunchedEffect(Unit) {
            var last = latest.value
            while (true) {
                delay(1_000)
                val now = latest.value
                down.add(((now.second - last.second).coerceAtLeast(0)) / 1024f)
                up.add(((now.first - last.first).coerceAtLeast(0)) / 1024f)
                if (down.size > 28) down.removeAt(0)
                if (up.size > 28) up.removeAt(0)
                last = now
            }
        }

        Box(Modifier.fillMaxSize().background(Palette.BgDeep).systemBarsPadding()) {
            Backdrop(state)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Header()
                if (crashReport != null) CrashCard(crashReport, onDismissCrash)
                Hero(state, selected, traffic, onToggle)
                Bento(down, up, traffic)
                Nodes(profiles, selected, state, onSelect)
                Actions(pingMs, pingBusy, testRunning, onPing, onSelfTest)
                if (testReport != null) Report(testReport)
                Console(logs)
            }
        }
    }
}

/* ------------------------------------------------------------------ chrome */

@Composable
private fun Backdrop(state: TunnelState) {
    val accent = statusColor(state)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.22f),
                radius = size.minDimension * 0.75f,
            ),
        )
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            for (i in 0 until 3) {
                val y = size.height * (0.28f + i * 0.22f)
                drawLine(
                    color = Palette.TextPrimary,
                    start = Offset(w * 0.12f, y),
                    end = Offset(w * if (i == 1) 0.62f else 0.88f, y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(7.dp).clip(CircleShape).background(Palette.Mint))
        Spacer(Modifier.width(8.dp))
        Text(
            "MOHAMMAD VPN",
            fontFamily = Orbitron,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 2.4.sp,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "v" + ir.mohammad.vpn.BuildConfig.VERSION_NAME,
            fontFamily = Chakra,
            fontSize = 10.sp,
            color = Palette.TextMuted,
        )
    }
}

/* -------------------------------------------------------------------- hero */

@Composable
private fun Hero(state: TunnelState, profile: Profile?, traffic: Pair<Long, Long>, onToggle: () -> Unit) {
    val accent = statusColor(state)
    Glass(shape = 26.dp, border = accent.copy(alpha = 0.28f)) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(profile)
                Spacer(Modifier.height(6.dp))
                Text(
                    statusLabel(state),
                    fontFamily = ChakraSemi,
                    fontSize = 11.sp,
                    letterSpacing = 2.2.sp,
                    color = accent,
                )
                Text(
                    sessionTimer(state),
                    fontFamily = Orbitron,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = Palette.TextPrimary,
                )
                Text(
                    "↓ ${human(traffic.second)}   ↑ ${human(traffic.first)}",
                    fontFamily = Chakra,
                    fontSize = 11.sp,
                    color = Palette.TextSecondary,
                )
                if (state is TunnelState.Failed) {
                    Text(
                        errorText(state.reason),
                        fontFamily = Chakra,
                        fontSize = 10.sp,
                        color = Palette.Rose,
                    )
                }
            }
            PowerDial(state, accent, onToggle)
        }
    }
}

@Composable
private fun Pill(profile: Profile?) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x14FFFFFF))
            .border(1.dp, Palette.BorderSubtle, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Emerald))
        Spacer(Modifier.width(8.dp))
        Text(
            profile?.let { "${it.protocol} · ${it.core}" } ?: "No profile",
            fontFamily = ChakraSemi,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = Palette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PowerDial(state: TunnelState, accent: Color, onClick: () -> Unit) {
    val connected = state is TunnelState.Connected
    val busy = state is TunnelState.Connecting || state is TunnelState.Disconnecting
    // Continuous animation only while a transition is in flight; an idle dial must
    // not schedule frames (it would force a 60 Hz redraw of the whole screen).
    val spin = rememberInfiniteTransition(label = "dial")
    val phase by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = LinearEasing)),
        label = "phase",
    )
    val breathe by spin.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe",
    )

    Box(
        modifier = Modifier
            .size(148.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Read the animated values only during a transition: an idle dial must not
            // schedule a redraw, otherwise the whole screen repaints at 60 Hz.
            val phaseNow = if (busy) phase else 0f
            val breatheNow = if (busy) breathe else 1f
            val c = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            // 60 micro ticks
            for (i in 0 until 60) {
                val angle = Math.toRadians(i * 6.0)
                val long = i % 5 == 0
                val rOuter = radius * 0.99f
                val rInner = radius * if (long) 0.90f else 0.94f
                drawLine(
                    color = if (connected) accent.copy(alpha = if (long) 0.9f else 0.45f)
                    else Palette.TextMuted.copy(alpha = if (long) 0.7f else 0.3f),
                    start = Offset(c.x + cos(angle).toFloat() * rInner, c.y + sin(angle).toFloat() * rInner),
                    end = Offset(c.x + cos(angle).toFloat() * rOuter, c.y + sin(angle).toFloat() * rOuter),
                    strokeWidth = if (long) 2f else 1f,
                    cap = StrokeCap.Round,
                )
            }

            // 28 segment LED ring
            val segments = 28
            val gap = 3.2f
            val sweep = 360f / segments
            val rLed = radius * 0.76f
            val lit = if (connected) segments else 0
            for (i in 0 until segments) {
                val start = -90f + i * sweep
                val on = i < lit
                val alpha = when {
                    on -> 1f
                    busy && ((i + (phaseNow / sweep).toInt()) % 4 == 0) -> breatheNow
                    else -> 0.16f
                }
                drawArc(
                    color = accent.copy(alpha = alpha),
                    startAngle = start,
                    sweepAngle = sweep - gap,
                    useCenter = false,
                    topLeft = Offset(c.x - rLed, c.y - rLed),
                    size = Size(rLed * 2, rLed * 2),
                    style = Stroke(width = if (on) 6.dp.toPx() else 4.dp.toPx(), cap = StrokeCap.Round),
                )
                if (on) {
                    drawArc(
                        color = accent.copy(alpha = 0.22f),
                        startAngle = start,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = Offset(c.x - rLed, c.y - rLed),
                        size = Size(rLed * 2, rLed * 2),
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }

            // inner energy ring
            val rIn = radius * 0.55f
            drawCircle(
                color = accent.copy(alpha = if (connected) 0.42f else 0.10f),
                radius = rIn,
                center = c,
                style = Stroke(width = 1.4.dp.toPx()),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = if (connected) 0.16f else 0.05f), Color.Transparent),
                    center = c,
                    radius = rIn,
                ),
                radius = rIn,
                center = c,
            )
        }
        // power glyph
        Canvas(Modifier.size(30.dp)) {
            val stroke = 2.4.dp.toPx()
            drawArc(
                color = if (connected || busy) accent else Palette.TextSecondary,
                startAngle = -60f,
                sweepAngle = 300f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawLine(
                color = if (connected || busy) accent else Palette.TextSecondary,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height * 0.46f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/* ------------------------------------------------------------------- bento */

@Composable
private fun Bento(down: List<Float>, up: List<Float>, traffic: Pair<Long, Long>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeedTile("DOWNLOAD", down, Palette.Mint, Modifier.weight(1f), up = false)
            SpeedTile("UPLOAD", up, Palette.Violet, Modifier.weight(1f), up = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TotalTile("DOWNLOADED", traffic.second, Palette.Mint, Modifier.weight(1f))
            TotalTile("UPLOADED", traffic.first, Palette.Violet, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SpeedTile(label: String, samples: List<Float>, accent: Color, modifier: Modifier, up: Boolean) {
    Glass(modifier = modifier, shape = 20.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontFamily = ChakraSemi, fontSize = 10.sp, letterSpacing = 1.4.sp, color = Palette.TextSecondary)
                Spacer(Modifier.weight(1f))
                Text(if (up) "▲▲" else "▼▼", fontSize = 9.sp, color = accent)
            }
            Sparkline(samples, accent, Modifier.fillMaxWidth().height(46.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format(Locale.US, "%.1f", samples.lastOrNull() ?: 0f),
                    fontFamily = Orbitron,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = Palette.TextPrimary,
                )
                Spacer(Modifier.width(4.dp))
                Text("KB/S", fontFamily = Chakra, fontSize = 9.sp, color = Palette.TextMuted)
            }
        }
    }
}

@Composable
private fun Sparkline(samples: List<Float>, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        val max = (samples.maxOrNull() ?: 0f).coerceAtLeast(8f)
        // baseline grid
        for (i in 0..3) {
            val y = size.height * i / 3f
            drawLine(Palette.TextMuted.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        if (samples.size < 2) return@Canvas
        val step = size.width / (samples.size - 1)
        val path = Path()
        val fill = Path()
        samples.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v / max) * size.height * 0.92f
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(
            fill,
            brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.26f), Color.Transparent)),
        )
        drawPath(path, accent, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun TotalTile(label: String, bytes: Long, accent: Color, modifier: Modifier) {
    val gb = bytes / 1_073_741_824.0
    Glass(modifier = modifier, shape = 20.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, fontFamily = ChakraSemi, fontSize = 10.sp, letterSpacing = 1.4.sp, color = Palette.TextSecondary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format(Locale.US, "%.2f", gb),
                    fontFamily = Orbitron,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    color = Palette.TextPrimary,
                )
                Spacer(Modifier.width(4.dp))
                Text("GB", fontFamily = Chakra, fontSize = 9.sp, color = Palette.TextMuted)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x1FFFFFFF)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((gb / 5.0).coerceIn(0.02, 1.0).toFloat())
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.55f)))),
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ nodes */

@Composable
private fun Nodes(profiles: List<Profile>, selected: Profile?, state: TunnelState, onSelect: (Profile) -> Unit) {
    Glass(shape = 22.dp) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("PROFILES", fontFamily = ChakraSemi, fontSize = 10.sp, letterSpacing = 1.6.sp, color = Palette.TextSecondary)
                Spacer(Modifier.weight(1f))
                Text("${profiles.size}", fontFamily = Chakra, fontSize = 10.sp, color = Palette.TextMuted)
            }
            Spacer(Modifier.height(8.dp))
            if (profiles.isEmpty()) {
                Text(
                    "No profile in the bundle.",
                    fontFamily = Chakra,
                    fontSize = 12.sp,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            profiles.forEach { profile ->
                val active = profile.id == selected?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = state !is TunnelState.Connected) { onSelect(profile) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (active) Palette.Emerald.copy(alpha = 0.18f) else Color(0x0FFFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            profile.protocol.take(1),
                            fontFamily = Orbitron,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (active) Palette.Mint else Palette.TextSecondary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile.name,
                            fontFamily = ChakraSemi,
                            fontSize = 13.sp,
                            color = Palette.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${profile.protocol} · ${profile.core}",
                            fontFamily = Chakra,
                            fontSize = 10.sp,
                            color = Palette.TextMuted,
                        )
                    }
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, if (active) Palette.Mint else Palette.TextMuted, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Mint))
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- actions */

@Composable
private fun Actions(
    pingMs: Long?,
    pingBusy: Boolean,
    testRunning: Boolean,
    onPing: () -> Unit,
    onSelfTest: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GhostButton(
            text = when {
                pingBusy -> "PINGING…"
                pingMs != null && pingMs >= 0 -> "PING ${pingMs}MS"
                pingMs != null -> "PING FAILED"
                else -> "TEST PING"
            },
            enabled = !pingBusy,
            onClick = onPing,
            modifier = Modifier.weight(1f),
        )
        GhostButton(
            text = if (testRunning) "RUNNING…" else "SELF-TEST",
            enabled = !testRunning,
            onClick = onSelfTest,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GhostButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x0DFFFFFF))
            .border(1.dp, if (enabled) Palette.BorderEmerald else Palette.BorderSubtle, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontFamily = ChakraSemi,
            fontSize = 11.sp,
            letterSpacing = 1.6.sp,
            color = if (enabled) Palette.Mint else Palette.TextMuted,
        )
    }
}

@Composable
private fun CrashCard(report: String, onDismiss: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Glass(shape = 16.dp, border = Palette.Rose.copy(alpha = 0.45f)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("LAST RUN CRASHED", fontFamily = ChakraSemi, fontSize = 10.sp, letterSpacing = 1.4.sp, color = Palette.Rose)
            Text(
                report.lineSequence().take(4).joinToString("\n"),
                fontFamily = Chakra,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = Palette.TextSecondary,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "COPY",
                    fontFamily = ChakraSemi,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = Palette.Mint,
                    modifier = Modifier.clickable { clipboard.setText(androidx.compose.ui.text.AnnotatedString(report)) },
                )
                Text(
                    "DISMISS",
                    fontFamily = ChakraSemi,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = Palette.TextMuted,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }
        }
    }
}

@Composable
private fun Report(report: String) {
    Glass(shape = 18.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("SELF-TEST", fontFamily = ChakraSemi, fontSize = 10.sp, letterSpacing = 1.4.sp, color = Palette.TextSecondary)
            Text(
                summarize(report),
                fontFamily = Chakra,
                fontSize = 11.sp,
                color = Palette.TextPrimary,
            )
        }
    }
}

@Composable
private fun Console(logs: List<LogEntry>) {
    val tail = logs.takeLast(40)
    Glass(shape = 18.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CONNECTION LOG", fontFamily = ChakraSemi, fontSize = 10.sp, letterSpacing = 1.4.sp, color = Palette.TextSecondary)
                Spacer(Modifier.weight(1f))
                Text("${logs.size}", fontFamily = Chakra, fontSize = 10.sp, color = Palette.TextMuted)
            }
            if (tail.isEmpty()) {
                Text("Waiting for events…", fontFamily = Chakra, fontSize = 10.sp, color = Palette.TextMuted)
            }
            tail.forEach { entry ->
                Text(
                    "${entry.time.takeLast(8)}  ${entry.message}",
                    fontFamily = Chakra,
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    color = when (entry.level) {
                        "E" -> Palette.Rose
                        "W" -> Palette.Amber
                        else -> Palette.TextMuted
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/* ----------------------------------------------------------------- helpers */

@Composable
private fun Glass(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.unit.Dp = 24.dp,
    border: Color = Palette.BorderSubtle,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(shape))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xCC0E1311), Color(0xCC0A0E0C)),
                ),
            )
            .border(1.dp, border, RoundedCornerShape(shape)),
    ) { content() }
}

private fun statusColor(state: TunnelState): Color = when (state) {
    is TunnelState.Connected -> Palette.Mint
    is TunnelState.Connecting, is TunnelState.Disconnecting -> Palette.Amber
    is TunnelState.Failed -> Palette.Rose
    else -> Palette.TextMuted
}

private fun statusLabel(state: TunnelState): String = when (state) {
    is TunnelState.Connected -> "CONNECTED"
    is TunnelState.Connecting -> "ENCRYPTING TUNNEL…"
    is TunnelState.Disconnecting -> "SHUTTING DOWN…"
    is TunnelState.Failed -> "FAILED"
    else -> "DISCONNECTED"
}

private fun errorText(reason: TunnelError): String = when (reason) {
    TunnelError.NoProfileSelected -> "No profile selected"
    TunnelError.VpnPermissionDenied -> "VPN permission denied"
    TunnelError.TunInterfaceFailed -> "Could not create the interface"
    TunnelError.CoreStartFailed -> "Core refused to start"
    TunnelError.CoreCrashed -> "Core stopped unexpectedly"
    TunnelError.InvalidProfile -> "The profile is not valid"
    TunnelError.NetworkUnavailable -> "Waiting for the network"
    TunnelError.PingFailed -> "Ping failed"
}

private fun human(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1_048_576 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1_073_741_824 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    else -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
}

@Composable
private fun sessionTimer(state: TunnelState): String {
    var seconds by remember { mutableStateOf(0L) }
    LaunchedEffect(state) {
        seconds = 0
        if (state is TunnelState.Connected) {
            while (true) {
                delay(1_000)
                seconds++
            }
        }
    }
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

private fun summarize(report: String): String {
    fun pick(key: String): String =
        Regex("\"$key\"\\s*:\\s*\"?([^,\"}]+)").find(report)?.groupValues?.get(1)?.trim() ?: "?"
    return "egress ${pick("conclusion")} · dns ${pick("ok")} · down ${pick("deltaDownBytes")}"
}
