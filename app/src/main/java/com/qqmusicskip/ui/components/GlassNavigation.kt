package com.qqmusicskip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

/**
 * 玻璃化顶栏：透明背景 + 底部 1px 渐变描边，标题居中、SemiBold 字重。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
) {
    val onBg = MaterialTheme.colorScheme.onBackground
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = onBg,
            navigationIconContentColor = onBg,
            actionIconContentColor = onBg,
        ),
        modifier = modifier.drawBehind {
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        onBg.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                ),
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        },
    )
}

/**
 * 玻璃化底栏：自实现的 Row + 顶部 1px 渐变描边。
 *
 * 用 Row + weight(1f) 等宽分配，避免 SpaceEvenly 在 4 个 item 时把第一个/最后一个推出容器。
 * 不用 NavigationBar / NavigationBarItem 是为了避开 Material 3 BOM 2026 的 unresolved
 * 引用问题，同时给玻璃面板更精准的视觉控制。
 */
@Composable
fun GlassBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val surface = MaterialTheme.colorScheme.surface
    val backdrop = LocalLiquidBackdrop.current
    Row(
        modifier = modifier.fillMaxWidth().height(72.dp).then(
            if (backdrop == null) {
                Modifier.background(surface)
            } else {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(24.dp) },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(12.dp.toPx(), 24.dp.toPx())
                    },
                    onDrawSurface = { drawRect(surface.copy(alpha = 0.36f)) },
                )
            },
        )
            .drawBehind {
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            onBg.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * 玻璃化底部导航项：selected 时使用主色容器作为发光指示器背景。
 *
 * 关键修正：内边距减小（8dp 而不是 16dp），避免 4 个 item 总宽度超过容器。
 * 调用方需在外层 GlassBottomBar 内使用 weight(1f)。
 */
@Composable
fun RowScope.GlassNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val indicatorColor = if (selected) cs.primaryContainer else Color.Transparent
    val contentColor = if (selected) cs.primary else cs.onSurfaceVariant
    Column(
        modifier = modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(indicatorColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.padding(bottom = 2.dp)) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides contentColor,
            ) {
                icon()
            }
        }
        Text(
            text = label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}
