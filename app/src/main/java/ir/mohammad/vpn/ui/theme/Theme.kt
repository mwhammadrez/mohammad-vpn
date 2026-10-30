package ir.mohammad.vpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.mohammad.vpn.R

/** Cyber-emerald palette. Values mirror the design system's custom properties. */
object Palette {
    val BgDeep = Color(0xFF050807)
    val Card = Color(0xD10E1311)
    val CardInner = Color(0xFF0A0E0C)
    val BorderSubtle = Color(0x12FFFFFF)
    val BorderEmerald = Color(0x472DF8C5)

    val Mint = Color(0xFF34F5C5)
    val Emerald = Color(0xFF10B981)
    val EmeraldDark = Color(0xFF059669)

    val Violet = Color(0xFFA78BFA)
    val Indigo = Color(0xFF7C3AED)

    val Amber = Color(0xFFF59E0B)
    val Rose = Color(0xFFF43F5E)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
}

val Orbitron = FontFamily(Font(R.font.orbitron, FontWeight.Normal))
val Chakra = FontFamily(Font(R.font.chakra_regular, FontWeight.Normal))
val ChakraSemi = FontFamily(Font(R.font.chakra_semibold, FontWeight.SemiBold))

private val Typography = Typography(
    displayLarge = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 1.sp),
    titleLarge = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp),
    titleMedium = TextStyle(fontFamily = ChakraSemi, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 1.4.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, color = Palette.TextPrimary),
    bodyMedium = TextStyle(fontSize = 13.sp, color = Palette.TextSecondary),
    bodySmall = TextStyle(fontSize = 11.sp, color = Palette.TextMuted),
    labelSmall = TextStyle(fontFamily = Chakra, fontSize = 10.sp, letterSpacing = 1.2.sp),
)

private val Colors = darkColorScheme(
    primary = Palette.Mint,
    onPrimary = Palette.BgDeep,
    secondary = Palette.Violet,
    background = Palette.BgDeep,
    onBackground = Palette.TextPrimary,
    surface = Palette.CardInner,
    onSurface = Palette.TextPrimary,
    error = Palette.Rose,
)

@Composable
fun MohammadTheme(content: @Composable () -> Unit) {
    // The design is dark-only; ignore the system setting deliberately.
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = Colors, typography = Typography, content = content)
}
