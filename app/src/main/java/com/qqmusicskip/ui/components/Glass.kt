package com.qqmusicskip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qqmusicskip.ui.theme.GlassOutlineDark
import com.qqmusicskip.ui.theme.GlassOutlineLight
import com.qqmusicskip.ui.theme.GlassTintDark
import com.qqmusicskip.ui.theme.GlassTintLight
import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

val LocalLiquidBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

@Composable
fun LiquidGlassProvider(
    backdrop: LayerBackdrop,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLiquidBackdrop provides backdrop, content = content)
}

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
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var passivePressed by remember { mutableStateOf(false) }
    val feedbackScale by animateFloatAsState(
        targetValue = if ((pressed || passivePressed) && enabled) 0.965f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "glass-card-press",
    )
    val interactiveModifier = Modifier
        .graphicsLayer {
            scaleX = feedbackScale
            scaleY = feedbackScale
            alpha = if (enabled) 1f else 0.62f
        }
        .then(if (onClick != null) {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
        } else {
            // Non-clickable cards still provide tactile compression while allowing
            // the parent scroll container to keep handling the gesture.
            Modifier.pointerInput(enabled) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    passivePressed = true
                    waitForUpOrCancellation()
                    passivePressed = false
                }
            }
        })
    val backdrop = LocalLiquidBackdrop.current
    val surfaceAlpha = when (thickness) {
        GlassThickness.Default -> if (dark) 0.28f else 0.34f
        GlassThickness.Thick -> if (dark) 0.42f else 0.50f
        GlassThickness.Small -> if (dark) 0.20f else 0.27f
    }
    if (backdrop == null) {
        Column(
            modifier = modifier
                .then(interactiveModifier)
                .glassify(thickness, cornerRadius, dark, elevation)
                .padding(contentPadding),
            content = content,
        )
    } else {
        Column(
            modifier = modifier
                .then(interactiveModifier)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(cornerRadius) },
                    effects = {
                        vibrancy()
                        blur(if (thickness == GlassThickness.Thick) 8.dp.toPx() else 4.dp.toPx())
                        lens(cornerRadius.toPx() * 0.6f, cornerRadius.toPx() * 1.2f)
                    },
                    onDrawSurface = {
                        drawRect((if (dark) GlassTintDark else GlassTintLight).copy(alpha = surfaceAlpha))
                    },
                )
                .padding(contentPadding),
            content = content,
        )
    }
}
