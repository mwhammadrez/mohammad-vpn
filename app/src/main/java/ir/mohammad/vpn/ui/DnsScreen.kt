package ir.mohammad.vpn.ui

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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.mohammad.vpn.data.LogEntry
import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.data.ProxyState
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.ui.theme.Chakra
import ir.mohammad.vpn.ui.theme.ChakraSemi
import ir.mohammad.vpn.ui.theme.Orbitron
import ir.mohammad.vpn.ui.theme.Palette

/**
 * Mode 2: the DNS tunnel.
 *
 * Two ways to use it, because a DNS tunnel is a proxy by nature:
 *  - PROXY: the engine listens on 127.0.0.1 and apps that accept a proxy use it. No VpnService, no
 *    consent dialog, no routes — nothing else on the phone is affected.
 *  - FULL TUNNEL: the same engine behind the VPN service, so every app is carried.
 *
 * Kept visually separate from Mode 1 on purpose: the two paths fail for different reasons, and a user
 * who is staring at this screen should know which one they are looking at.
 */
@Composable
fun DnsScreen(
    dnsProfiles: List<Profile>,
    selected: Profile?,
    dnsMode: DnsMode,
    tunnelState: TunnelState,
    proxy: ProxyState.Proxy,
    logs: List<LogEntry>,
    onPickDnsMode: (DnsMode) -> Unit,
    onSelectProfile: (Profile) -> Unit,
    onToggle: () -> Unit,
    onCopyAddress: () -> Unit,
    onPaste: () -> Unit = {},
    importMessage: String? = null,
    topBar: @Composable () -> Unit = {},
) {
    val busy = tunnelState is TunnelState.Connecting ||
        tunnelState is TunnelState.Disconnecting ||
        proxy.starting
    val connected = proxy.running || tunnelState is TunnelState.Connected

    // Without the inset the header — the only way back to Mode 1/3 — sits under the status bar and
    // cannot be tapped. Mode 1 and Mode 3 already do this.
    Box(Modifier.fillMaxSize().background(Palette.BgDeep).systemBarsPadding()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        topBar()
        ModeSwitch(dnsMode, onPickDnsMode)

        Box(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            contentAlignment = Alignment.Center,
        ) {
            Orb(connected = connected, busy = busy, onClick = onToggle)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PowerGlyph(
                    connected = connected,
                    color = if (connected) Palette.Violet else Palette.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        proxy.starting -> "STARTING"
                        connected && proxy.running -> "PROXY UP"
                        connected -> "CONNECTED"
                        busy -> "CONNECTING"
                        tunnelState is TunnelState.Failed -> "FAILED"
                        else -> "DISCONNECTED"
                    },
                    fontFamily = ChakraSemi,
                    fontSize = 11.sp,
                    letterSpacing = 2.4.sp,
                    color = if (connected) Palette.Violet else Palette.TextSecondary,
                )
            }
        }

        Text(
            if (connected) {
                if (dnsMode == DnsMode.PROXY) "tap to stop the proxy" else "tap to disconnect"
            } else {
                if (dnsMode == DnsMode.PROXY) "tap to start the proxy" else "tap to connect"
            },
            fontFamily = Chakra,
            fontSize = 10.sp,
            color = Palette.TextMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        if (dnsMode == DnsMode.PROXY && proxy.running) {
            Glass(shape = 18.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("SOCKS5", fontFamily = ChakraSemi, fontSize = 9.sp, letterSpacing = 1.4.sp, color = Palette.TextSecondary)
                        Text(
                            proxy.address,
                            fontFamily = ChakraSemi,
                            fontSize = 15.sp,
                            letterSpacing = 0.6.sp,
                            color = Palette.TextPrimary,
                        )
                        Text("point apps that accept a proxy here", fontFamily = Chakra, fontSize = 9.sp, color = Palette.TextMuted)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Palette.BorderViolet, RoundedCornerShape(10.dp))
                            .clickable(onClick = onCopyAddress)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text("COPY", fontFamily = ChakraSemi, fontSize = 9.sp, letterSpacing = 1.2.sp, color = Palette.Violet)
                    }
                }
            }
        }

        if (proxy.error != null) {
            Text(
                proxy.error!!,
                fontFamily = Chakra,
                fontSize = 10.sp,
                color = Palette.Rose,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        ProfileList(dnsProfiles, selected, onSelectProfile, onPaste, importMessage)

        Console(logs)
    }
    }
}

@Composable
private fun ModeSwitch(mode: DnsMode, onPick: (DnsMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x0DFFFFFF))
            .border(1.dp, Palette.BorderSubtle, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DnsMode.entries.forEach { entry ->
            val active = entry == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) Palette.Violet.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onPick(entry) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.label,
                    fontFamily = ChakraSemi,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                    color = if (active) Palette.TextPrimary else Palette.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun Orb(connected: Boolean, busy: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "dns-orb")
    val breath by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "breath",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            for (i in 0 until 72) {
                val angle = Math.toRadians(i * 5.0)
                val long = i % 6 == 0
                val outer = radius * 0.97f
                val inner = radius * if (long) 0.89f else 0.93f
                drawLine(
                    color = if (connected) Palette.Violet.copy(alpha = if (long) 0.9f else 0.4f)
                    else Palette.TextMuted.copy(alpha = 0.25f),
                    start = androidx.compose.ui.geometry.Offset(
                        c.x + kotlin.math.cos(angle).toFloat() * inner,
                        c.y + kotlin.math.sin(angle).toFloat() * inner,
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        c.x + kotlin.math.cos(angle).toFloat() * outer,
                        c.y + kotlin.math.sin(angle).toFloat() * outer,
                    ),
                    strokeWidth = if (long) 2f else 1f,
                    cap = StrokeCap.Round,
                )
            }

            val ring = radius * 0.52f
            drawCircle(
                color = Palette.Violet.copy(alpha = if (connected) 0.55f else 0.16f),
                radius = ring,
                center = c,
                style = Stroke(width = (if (connected) 2.5f else 1.6f).dp.toPx()),
            )
            if (busy) {
                drawCircle(
                    color = Palette.Violet.copy(alpha = breath * 0.55f),
                    radius = ring + 10.dp.toPx(),
                    center = c,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Palette.Violet.copy(alpha = if (connected) 0.22f else 0.07f),
                        Color.Transparent,
                    ),
                    center = c,
                    radius = ring,
                ),
                radius = ring,
                center = c,
            )
        }
    }
}

/**
 * Drawn rather than typed: the display font has no power/stop glyph, and a missing glyph renders as a
 * hollow box — which looks like a rendering bug rather than an icon.
 */
@Composable
private fun PowerGlyph(connected: Boolean, color: Color) {
    Canvas(Modifier.size(34.dp)) {
        val stroke = 3.dp.toPx()
        if (connected) {
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.22f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.56f),
                style = Stroke(width = stroke),
            )
        } else {
            drawArc(
                color = color,
                startAngle = -60f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(stroke, stroke),
                size = androidx.compose.ui.geometry.Size(size.width - stroke * 2, size.height - stroke * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.46f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ProfileList(
    profiles: List<Profile>,
    selected: Profile?,
    onSelect: (Profile) -> Unit,
    onPaste: () -> Unit,
    importMessage: String?,
) {
    Glass(shape = 18.dp) {
        Column(Modifier.padding(vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "DNS PROFILES",
                    fontFamily = ChakraSemi,
                    fontSize = 9.sp,
                    letterSpacing = 1.4.sp,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(Palette.Violet.copy(alpha = 0.16f))
                        .border(1.dp, Palette.BorderViolet, RoundedCornerShape(9.dp))
                        .clickable(onClick = onPaste)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text("PASTE", fontFamily = ChakraSemi, fontSize = 9.sp, letterSpacing = 1.2.sp, color = Palette.Violet)
                }
            }
            if (importMessage != null) {
                Text(
                    importMessage,
                    fontFamily = Chakra,
                    fontSize = 9.sp,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(horizontal = 14.dp).padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            if (profiles.isEmpty()) {
                Text(
                    "No DNS profile yet — paste a dns:// link.",
                    fontFamily = Chakra,
                    fontSize = 10.sp,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            profiles.forEach { profile ->
                val active = profile.id == selected?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(profile) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(if (active) Palette.Violet else Palette.TextMuted),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile.name,
                            fontFamily = ChakraSemi,
                            fontSize = 12.sp,
                            color = Palette.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            profile.dns?.optString("domain").orEmpty(),
                            fontFamily = Chakra,
                            fontSize = 9.sp,
                            color = Palette.TextMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Console(logs: List<LogEntry>) {
    Glass(shape = 18.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "DNS LOG",
                fontFamily = ChakraSemi,
                fontSize = 9.sp,
                letterSpacing = 1.4.sp,
                color = Palette.TextSecondary,
            )
            Spacer(Modifier.height(2.dp))
            if (logs.isEmpty()) {
                Text("nothing yet", fontFamily = Chakra, fontSize = 9.sp, color = Palette.TextMuted)
            }
            logs.takeLast(6).forEach { entry ->
                Text(
                    "${entry.tag}  ${entry.message}",
                    fontFamily = Chakra,
                    fontSize = 9.sp,
                    color = if (entry.level == "E") Palette.Rose else Palette.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
