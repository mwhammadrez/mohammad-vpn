package ir.mohammad.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.mohammad.vpn.ui.theme.Chakra
import ir.mohammad.vpn.ui.theme.ChakraSemi
import ir.mohammad.vpn.ui.theme.Orbitron
import ir.mohammad.vpn.ui.theme.Palette

/**
 * App header with the mode switch.
 *
 * The chevron is the only control up here: it opens a two-row sheet, and closing it leaves the active
 * mode's name under the title so the current environment is never ambiguous.
 */
@Composable
fun TopBar(
    mode: AppMode,
    version: String,
    menuOpen: Boolean,
    onToggleMenu: () -> Unit,
    onPickMode: (AppMode) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0x0FFFFFFF))
                    .clickable(onClick = onToggleMenu),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "‹",
                    fontFamily = ChakraSemi,
                    fontSize = 20.sp,
                    color = if (menuOpen) Palette.Mint else Palette.TextPrimary,
                    modifier = Modifier.rotate(if (menuOpen) -90f else 0f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(7.dp).clip(CircleShape).background(
                if (mode == AppMode.DNS) Palette.Violet else Palette.Mint,
            ))
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
            Text(version, fontFamily = Chakra, fontSize = 10.sp, color = Palette.TextMuted)
        }

        Text(
            if (mode == AppMode.DNS) "Mode 2 · DNS tunnel" else "Mode 1 · Xray",
            fontFamily = Chakra,
            fontSize = 10.sp,
            color = if (mode == AppMode.DNS) Palette.Violet else Palette.TextMuted,
            modifier = Modifier.padding(start = 42.dp, top = 2.dp),
        )

        if (menuOpen) {
            Spacer(Modifier.size(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.CardInner)
                    .border(1.dp, Palette.BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ModeRow(
                    title = "Mode 1",
                    subtitle = "Xray · REALITY · WebSocket",
                    active = mode == AppMode.XRAY,
                    accent = Palette.Mint,
                ) { onPickMode(AppMode.XRAY) }
                ModeRow(
                    title = "Mode 2",
                    subtitle = "DNS tunnel · slow, survives DNS-only networks",
                    active = mode == AppMode.DNS,
                    accent = Palette.Violet,
                ) { onPickMode(AppMode.DNS) }
            }
        }
    }
}

@Composable
private fun ModeRow(
    title: String,
    subtitle: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) accent else Palette.TextMuted),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontFamily = ChakraSemi,
                fontSize = 12.sp,
                color = if (active) Palette.TextPrimary else Palette.TextSecondary,
            )
            Text(subtitle, fontFamily = Chakra, fontSize = 9.sp, color = Palette.TextMuted)
        }
        if (active) {
            Text("ACTIVE", fontFamily = ChakraSemi, fontSize = 8.sp, letterSpacing = 1.2.sp, color = accent)
        }
    }
}
