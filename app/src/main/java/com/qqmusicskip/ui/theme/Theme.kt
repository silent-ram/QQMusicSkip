package com.qqmusicskip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = SkyBlue,
    onPrimary = Color.White,
    primaryContainer = SkyBlueContainer,
    onPrimaryContainer = SkyBlueOnContainer,
    secondary = MintGreen,
    onSecondary = Color.White,
    secondaryContainer = MintGreenContainer,
    onSecondaryContainer = MintGreenOnContainer,
    tertiary = SoftPurple,
    onTertiary = Color.White,
    tertiaryContainer = SoftPurpleContainer,
    onTertiaryContainer = SoftPurpleOnContainer,
    background = Color.Transparent,
    onBackground = InkDark,
    surface = GlassSurfaceLight,
    onSurface = InkDark,
    surfaceVariant = Color(0x59EAF1FF),     // 35% 淡蓝白
    onSurfaceVariant = InkSoft,
    outline = GlassOutlineLight,
    outlineVariant = Color(0x4DEAF1FF),     // 30% 淡蓝白
    error = Color(0xFFE55C5C),
    onError = Color.White,
    errorContainer = Color(0xFFFCE4E4),
    onErrorContainer = Color(0xFF601410),
)

private val DarkScheme = darkColorScheme(
    primary = SkyBlueDark,
    onPrimary = SkyBlueContainerDark,
    primaryContainer = SkyBlueContainerDark,
    onPrimaryContainer = SkyBlueDark,
    secondary = MintGreenDark,
    onSecondary = MintGreenContainerDark,
    secondaryContainer = MintGreenContainerDark,
    onSecondaryContainer = MintGreenDark,
    tertiary = SoftPurpleDark,
    onTertiary = Color(0xFF3D1E6B),
    tertiaryContainer = Color(0xFF5A3587),
    onTertiaryContainer = SoftPurpleDark,
    background = Color.Transparent,
    onBackground = InkLight,
    surface = GlassSurfaceDark,
    onSurface = InkLight,
    surfaceVariant = Color(0x592A2840),    // 35% 深蓝紫
    onSurfaceVariant = InkLightSoft,
    outline = GlassOutlineDark,
    outlineVariant = Color(0x3D2A2840),   // 24% 深蓝紫
    error = Color(0xFFFF8A80),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF6B1F1F),
    onErrorContainer = Color(0xFFFFD7D4),
)

@Composable
fun QQmusicskipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography,
        content = content,
    )
}