package com.otpulse.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object OtpulseColors {
    val Navy950 = Color(0xFF070D18)
    val Navy900 = Color(0xFF091321)
    val Surface: Color
        @Composable get() = MaterialTheme.colorScheme.surface
    val SurfaceRaised: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val Border: Color
        @Composable get() = MaterialTheme.colorScheme.outline
    val Accent: Color
        @Composable get() = MaterialTheme.colorScheme.primary
    val Success: Color
        @Composable get() = MaterialTheme.colorScheme.tertiary
    val Text: Color
        @Composable get() = MaterialTheme.colorScheme.onBackground
    val TextMuted: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
}

object OtpulseSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object OtpulseTypography {
    val Code = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
    )
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4D8DFF),
    secondary = Color(0xFF4D8DFF),
    tertiary = Color(0xFF31D792),
    background = OtpulseColors.Navy950,
    surface = Color(0xFF151F2E),
    surfaceVariant = Color(0xFF1A2535),
    outline = Color(0xFF263448),
    onPrimary = Color.White,
    onBackground = Color(0xFFF4F7FC),
    onSurface = Color(0xFFF4F7FC),
    onSurfaceVariant = Color(0xFF9AA8BC),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2457C5),
    secondary = Color(0xFF2457C5),
    tertiary = Color(0xFF147A50),
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EDF5),
    outline = Color(0xFFCAD1DD),
    onPrimary = Color.White,
    onBackground = Color(0xFF171B23),
    onSurface = Color(0xFF171B23),
    onSurfaceVariant = Color(0xFF5D6675),
)

@Composable
fun OtpulseTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
