package com.qqmusicskip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 玻璃面板厚度档位。
 *
 * - [Default] 标准厚度，用于一般卡片
 * - [Thick]   厚一档，用于"封面背景"上需要更强对比的卡片
 * - [Small]   薄一档，用于状态横幅、筛选条等次要区域
 */
enum class GlassThickness { Default, Thick, Small }

/**
 * 把任意 Composable 装饰为玻璃面板的 Modifier 扩展。
 *
 * 实现原则：不依赖 RenderEffect（API 31+），改用 Brush 模拟磨砂，确保 minSdk 26 视觉一致。
 * 由 [dark] 决定亮/暗模式配色，调用方无需关心当前主题。
 */
fun Modifier.glassify(
    thickness: GlassThickness = GlassThickness.Default,
    cornerRadius: Dp = 20.dp,
    dark: Boolean = false,
    elevation: Dp = 8.dp,
): Modifier = composed {
    val surfaceAlpha = when (thickness) {
        GlassThickness.Default -> if (dark) 0.30f else 0.50f
        GlassThickness.Thick   -> if (dark) 0.60f else 0.82f
        GlassThickness.Small   -> if (dark) 0.22f else 0.38f
    }
    val outlineAlpha = when (thickness) {
        GlassThickness.Thick -> if (dark) 0.30f else 0.85f
        else                 -> if (dark) 0.18f else 0.65f
    }
    val baseColor = if (dark) Color(0xFF202838) else Color.White
    val shape = RoundedCornerShape(cornerRadius)
    this
        .shadow(elevation = elevation, shape = shape, clip = false)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    baseColor.copy(alpha = surfaceAlpha),
                    baseColor.copy(alpha = surfaceAlpha * 0.75f),
                ),
            ),
        )
        .border(
            width = 1.dp,
            color = Color.White.copy(alpha = outlineAlpha),
            shape = shape,
        )
}

/**
 * 标准玻璃面板卡片。内部使用 [Column] 排版，默认带 16dp 内边距。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    thickness: GlassThickness = GlassThickness.Default,
    cornerRadius: Dp = 20.dp,
    dark: Boolean = false,
    elevation: Dp = 8.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .glassify(thickness, cornerRadius, dark, elevation)
            .padding(contentPadding),
        content = content,
    )
}