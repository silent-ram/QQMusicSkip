package com.qqmusicskip.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.qqmusicskip.ui.theme.CoverDesatDark
import com.qqmusicskip.ui.theme.CoverDesatLight

/**
 * 全局根级背景。
 *
 * 三层结构：
 * 1. **底层**：固定彩色径向晕染（亮/暗各一套），叠加 8s 呼吸偏移
 * 2. **中层**（可选）：QQ 音乐封面 + 高斯模糊 + 透明度 0.65
 * 3. **顶层**：降饱和蒙版——亮色 30% 淡蓝白（不再用 55% 纯白，避免盖死主色调）
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
            animation = tween(durationMillis = 48_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val coverImage = remember(cover) { cover?.asImageBitmap() }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. 底层渐变（淡蓝→薄荷绿 / 深紫蓝→深青绿 + 彩色晕染呼吸）
        BaseGradientLayer(phase = phase, dark = dark, modifier = Modifier.fillMaxSize())

        // 2. 中层封面缓慢交叉淡化，切歌时不会瞬间换底图。
        Crossfade(
            targetState = coverImage,
            modifier = Modifier.fillMaxSize(),
            animationSpec = tween(durationMillis = 1_600),
            label = "background-cover-fade",
        ) { image ->
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(42.dp)
                        .alpha(0.46f)
                        .graphicsLayer {
                            scaleX = 1.04f
                            scaleY = 1.04f
                            translationX = kotlin.math.sin(phase * (2f * Math.PI)).toFloat() * 8f
                            translationY = kotlin.math.cos(phase * (2f * Math.PI)).toFloat() * 5f
                        },
                    contentScale = ContentScale.Crop,
                )
            }
        }

        // 3. 顶层降饱和蒙版（关键：薄一点，让主色调透出来）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (dark) CoverDesatDark else CoverDesatLight,
                ),
        )
    }
}

/**
 * 底层彩色渐变 + 双 radial 晕染，呼吸偏移。
 *
 * 饱和度比之前高一些，确保降饱和蒙版之后还能看到淡蓝薄荷绿主色调。
 */
@Composable
private fun BaseGradientLayer(phase: Float, dark: Boolean, modifier: Modifier = Modifier) {
    val baseTop = if (dark) Color(0xFF1A1F3A) else Color(0xFFCAE3FF)
    val baseBottom = if (dark) Color(0xFF142E2A) else Color(0xFFBCEBD2)
    val glowLeft = if (dark) Color(0x99A6C5FF) else Color(0x99B4DCFF)
    val glowRight = if (dark) Color(0x9986E5BB) else Color(0x99C8F0E0)
    val glowPurple = if (dark) Color(0x66CFA8F5) else Color(0x77B18CE8)

    Canvas(modifier = modifier.fillMaxSize()) {
        // 一个 Canvas 统一绘制全局背景，减少多层 Box/background 的重组和合成开销。
        drawRect(Brush.verticalGradient(listOf(baseTop, baseBottom)))
        val w = size.width
        val h = size.height
        val orbit = phase * (2f * Math.PI)
        drawCircle(
            brush = Brush.radialGradient(listOf(glowLeft, Color.Transparent)),
            radius = maxOf(w, h) * 0.72f,
            center = Offset(w * 0.18f + kotlin.math.sin(orbit).toFloat() * w * 0.08f, h * 0.18f),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(glowRight, Color.Transparent)),
            radius = maxOf(w, h) * 0.82f,
            center = Offset(w * 0.80f + kotlin.math.cos(orbit).toFloat() * w * 0.06f, h * 0.72f),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(glowPurple, Color.Transparent)),
            radius = maxOf(w, h) * 0.54f,
            center = Offset(w * 0.52f, h * 0.48f + kotlin.math.sin(orbit).toFloat() * h * 0.04f),
        )
    }
}
