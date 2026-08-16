package com.qqmusicskip.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = onBg,
            navigationIconContentColor = onBg,
            actionIconContentColor = onBg,
        ),
        modifier = modifier.drawBehind {
            // 底部 1px 渐变描边（中间实两端淡）
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
 * 玻璃化底栏：透明背景 + 顶部 1px 渐变描边，selected 项使用发光指示器。
 *
 * 调用方直接传入 NavigationBarItem 列表。
 */
@Composable
fun GlassBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val onBg = MaterialTheme.colorScheme.onBackground
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = modifier.drawBehind {
            // 顶部 1px 渐变描边
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
        },
        content = content,
    )
}

/**
 * 玻璃化底部导航项：selected 时使用主色 + 容器色作为发光指示器。
 */
@Composable
fun GlassNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
) {
    val cs = MaterialTheme.colorScheme
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = cs.onPrimaryContainer,
            selectedTextColor = cs.primary,
            indicatorColor = cs.primaryContainer,
            unselectedIconColor = cs.onSurfaceVariant,
            unselectedTextColor = cs.onSurfaceVariant,
        ),
    )
}