package com.tapin.student.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink     = Color(0xFF0A0A0A)
val Ink80   = Color(0xFF171717)
val Ink60   = Color(0xFF404040)
val Ink40   = Color(0xFF737373)
val Ink20   = Color(0xFFE5E5E5)
val Ink10   = Color(0xFFF5F5F5)
val Ink5    = Color(0xFFFAFAFA)
val Paper   = Color(0xFFFFFFFF)
val Danger  = Color(0xFFE5484D)
val Success = Color(0xFF30A46C)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    secondary = Ink80,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Ink10,
    onSurfaceVariant = Ink60,
    outline = Ink20,
    error = Danger
)

private val Sans = FontFamily.SansSerif

private val Type = Typography(
    displayLarge   = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold,     fontSize = 40.sp, letterSpacing = (-1.2).sp),
    headlineLarge  = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold,     fontSize = 28.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold,     fontSize = 22.sp, letterSpacing = (-0.4).sp),
    titleLarge     = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.2).sp),
    titleMedium    = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = (-0.1).sp),
    bodyLarge      = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal,   fontSize = 15.sp),
    bodyMedium     = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal,   fontSize = 13.sp),
    bodySmall      = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal,   fontSize = 12.sp),
    labelLarge     = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium,   fontSize = 13.sp, letterSpacing = 0.1.sp),
    labelMedium    = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium,   fontSize = 11.sp),
    labelSmall     = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium,   fontSize = 10.sp, letterSpacing = 0.4.sp)
)

private val Shape = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(10.dp),
    large      = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun TapInTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Type,
        shapes = Shape,
        content = content
    )
}
