package com.example.voicetester.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// 端末の見た目を固定したいので、ダイナミックカラーもライトテーマも使わない。
private val TerminalColors = darkColorScheme(
    primary = VvGreen,
    onPrimary = VvBg,
    secondary = VvDim,
    onSecondary = VvBg,
    background = VvBg,
    onBackground = VvInk,
    surface = VvPanel,
    onSurface = VvInk,
    surfaceVariant = VvPanel2,
    onSurfaceVariant = VvDim,
    outline = VvLine,
    outlineVariant = VvLineSoft,
    error = VvRed,
    onError = VvBg,
)

/** ラベルや数値は等幅、日本語の本文は既定の sans に振り分ける。 */
object VvType {
    val mark = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 2.2.sp)
    val label = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 1.4.sp)
    val value = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 1.0.sp)
    val nav = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.0.sp)
    val brand = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 17.sp, letterSpacing = 5.0.sp)
    val status = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.0.sp)
    val speak = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 17.sp, letterSpacing = 6.0.sp)
    val body = TextStyle(fontSize = 15.sp)
    val input = TextStyle(fontSize = 18.sp, lineHeight = 30.sp)
}

@Composable
fun VoiceTesterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalColors,
        typography = Typography(),
        content = content,
    )
}
