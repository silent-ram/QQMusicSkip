package com.qqmusicskip.ui.theme

import androidx.compose.ui.graphics.Color

// === 亮色系（淡蓝 + 薄荷绿 + 紫） ===
val SkyBlue = Color(0xFF5E9CF6)
val SkyBlueContainer = Color(0xFFDCE9FF)
val SkyBlueOnContainer = Color(0xFF0F2A55)
val MintGreen = Color(0xFF5DCFA1)
val MintGreenContainer = Color(0xFFD9F4E5)
val MintGreenOnContainer = Color(0xFF1F5C43)
val SoftPurple = Color(0xFFB18CE8)
val SoftPurpleContainer = Color(0xFFE9DCFA)
val SoftPurpleOnContainer = Color(0xFF3D1E6B)
val InkDark = Color(0xFF1A2530)
val InkSoft = Color(0xFF455565)

// === Glass 面板专用色 ===
// 亮色：淡蓝白（带主色调），不再用纯白，玻璃面板本身就有"音乐色感"
val GlassTintLight = Color(0xFFEAF1FF)
val GlassSurfaceLight = Color(0xA6FFFFFF)   // 65% 白
val GlassOutlineLight = Color(0xCCFFFFFF)   // 80% 白

// 暗色：深蓝紫（带主色调），不是死板的深蓝灰
val GlassTintDark = Color(0xFF2A2840)
val GlassSurfaceDark = Color(0x99202838)    // 60% 深蓝灰
val GlassOutlineDark = Color(0x40FFFFFF)    // 25% 白

// === 暗色系 ===
val SkyBlueDark = Color(0xFFA6C5FF)
val SkyBlueContainerDark = Color(0xFF2C4A7A)
val MintGreenDark = Color(0xFF86E5BB)
val MintGreenContainerDark = Color(0xFF1F5C43)
val SoftPurpleDark = Color(0xFFCFA8F5)
val InkLight = Color(0xFFE5EDF5)
val InkLightSoft = Color(0xFFB0BCC9)
val BackgroundDarkTop = Color(0xFF1A1F3A)    // 深紫蓝
val BackgroundDarkBottom = Color(0xFF142E2A) // 深青绿

// === 降饱和蒙版专用色 ===
// 之前用 55% 纯白太厚，把主色调盖死了。改用 30% 淡蓝白
val CoverDesatLight = Color(0x4DE0EBFF)      // 30% 淡蓝白
val CoverDesatDark = Color(0x991A1F3A)       // 60% 深紫蓝