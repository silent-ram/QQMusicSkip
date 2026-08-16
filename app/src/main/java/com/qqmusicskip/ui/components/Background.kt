package com.qqmusicskip.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * 全局根级背景。
 *
 * 三层结构：
 * 1. **底层**：固定彩色径向晕染（亮/暗各一套），叠加 8s 呼吸偏移
 * 2. **中层**（可选）：QQ 音乐封面 + 高斯模糊 + Cover 缩放
 * 3. **顶层**：降饱和蒙版（亮色 55% 白 / 暗色 60% 深蓝灰）
 *
 * @param cover 当前播放的封面图；为 null 时只显示底层晕染
 * @param dark  是否为深色模式
 */
@Composable
fun AnimatedBackground(
    cover: Bitmap?,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "bg-phase")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phase",
    )

    // 缓存 ImageBitmap，避免 Bitmap 引用相同但被替换时重新转换
    val coverImage = remember(cover) { cover?.asImageBitmap() }

    // 底层 + 中层 + 顶层
    Box(modifier = modifier.fillMaxSize()) {
        // 1. 底层渐变（vertical + 双 radial 晕染）
        BaseGradientLayer(phase = phase, dark = dark, modifier = Modifier.fillMaxSize())

        // 2. 中层封面（仅在 cover != null 时渲染）
        if (coverImage != null) {
            Image(
                bitmap = coverImage,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp)
                    .graphicsLayer { rotationZ = phase * 6f - 3f },
                contentScale = ContentScale.Crop,
            )
        }

        // 3. 顶层降饱和蒙版（必须盖在最上面以保证前景文字可读）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (dark) Color(0x991A1F3A) else Color(0x8CFFFFFF),
                ),
        )
    }
}

/**
 * 底层彩色渐变 + 双 radial 晕染，呼吸偏移。
 */
@Composable
private fun BaseGradientLayer(phase: Float, dark: Boolean, modifier: Modifier = Modifier) {
    // 垂直底色 + 双径向晕染，中心随 phase 缓慢漂移
    val baseTop = if (dark) Color(0xFF1A1F3A) else Color(0xFFDCEEFF)
    val baseBottom = if (dark) Color(0xFF142E2A) else Color(0xFFC8EEDA)
    val glowLeft = if (dark) Color(0x66A6C5FF) else Color(0x66B4DCFF)
    val glowRight = if (dark) Color(0x6686E5BB) else Color(0x66C8F0E0)
    val glowPurple = if (dark) Color(0x44CFA8F5) else Color(0x55B18CE8)

    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(baseTop, baseBottom),
                ),
            )
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(glowLeft, Color.Transparent),
                    center = Offset(200f + phase * 240f, 280f + phase * 120f),
                    radius = 900f,
                ),
            )
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(glowRight, Color.Transparent),
                    center = Offset(900f - phase * 200f, 1400f - phase * 100f),
                    radius = 1100f,
                ),
            )
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(glowPurple, Color.Transparent),
                    center = Offset(500f + phase * 160f, 900f - phase * 80f),
                    radius = 700f,
                ),
            ),
    )
}