package com.qqmusicskip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qqmusicskip.ui.theme.GlassOutlineDark
import com.qqmusicskip.ui.theme.GlassOutlineLight
import com.qqmusicskip.ui.theme.GlassTintDark
import com.qqmusicskip.ui.theme.GlassTintLight

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
 * 设计原则：
 * 1. 用 `Brush` 模拟磨砂（不依赖 RenderEffect），保证 minSdk 26 视觉一致
 * 2. elevation 4dp + clip=false（避免裁切内部文字）
 * 3. 主题色调用 GlassTint（淡蓝白/深蓝紫），玻璃面板本身带主色调
 * 4. alpha 全面提高（0.65/0.45/0.85），让玻璃面板在背景上清晰可见
 */
fun Modifier.glassify(
    thickness: GlassThickness = GlassThickness.Default,
    cornerRadius: Dp = 20.dp,
    dark: Boolean = false,
    elevation: Dp = 4.dp,
): Modifier = composed {
    val surfaceAlpha = when (thickness) {
        GlassThickness.Default -> if (dark) 0.45f else 0.65f
        GlassThickness.Thick   -> if (dark) 0.65f else 0.85f
        GlassThickness.Small   -> if (dark) 0.30f else 0.50f
    }
    val outlineAlpha = when (thickness) {
        GlassThickness.Thick -> if (dark) 0.30f else 0.90f
        else                 -> if (dark) 0.25f else 0.80f
    }
    val baseColor = if (dark) GlassTintDark else GlassTintLight
    val outlineColor = if (dark) GlassOutlineDark else GlassOutlineLight
    val outlineColorOverride = outlineColor.copy(alpha = outlineAlpha)
    val shape = RoundedCornerShape(cornerRadius)
    this
        .shadow(elevation = elevation, shape = shape, clip = false)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    baseColor.copy(alpha = surfaceAlpha),
                    baseColor.copy(alpha = surfaceAlpha * 0.78f),
                ),
            ),
        )
        .border(
            width = 1.dp,
            color = outlineColorOverride,
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
    elevation: Dp = 4.dp,
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