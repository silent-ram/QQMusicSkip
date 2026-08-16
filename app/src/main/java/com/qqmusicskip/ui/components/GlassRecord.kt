package com.qqmusicskip.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 连续旋转、可手拨的 3D 玻璃唱片。 */
@Composable
fun GlassRecord(
    cover: Bitmap?,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val image = remember(cover) { cover?.asImageBitmap() }
    val release = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var spinRotation by remember { mutableFloatStateOf(0f) }
    var dragRotation by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var previousFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (previousFrameNanos != 0L) {
                    val elapsedSeconds = (frameNanos - previousFrameNanos) / 1_000_000_000f
                    spinRotation = (spinRotation + elapsedSeconds * 360f / 14f) % 360f
                }
                previousFrameNanos = frameNanos
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(218.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            scope.launch { release.stop(); release.snapTo(0f) }
                        },
                        onDrag = { _, amount ->
                            dragRotation += amount.x * 0.62f
                        },
                        onDragEnd = {
                            val releasedAt = dragRotation
                            dragRotation = 0f
                            scope.launch {
                                release.snapTo(releasedAt)
                                release.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                                )
                            }
                        },
                    )
                }
                .graphicsLayer {
                    rotationX = 7f
                    rotationY = -13f
                    rotationZ = spinRotation + dragRotation + release.value
                    cameraDistance = 14f * density
                    shape = CircleShape
                    clip = false
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF454B58), Color(0xFF10131A), Color(0xFF020307)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                )
                drawCircle(Color.White.copy(alpha = 0.20f), radius, style = Stroke(1.5.dp.toPx()))
                repeat(11) { index ->
                    drawCircle(
                        color = Color.White.copy(alpha = if (index % 2 == 0) 0.075f else 0.035f),
                        radius = radius * (0.28f + index * 0.058f),
                        style = Stroke(0.8.dp.toPx()),
                    )
                }
                drawCircle(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.13f to Color.White.copy(alpha = 0.28f),
                        0.29f to Color.Transparent,
                        0.68f to Color.Transparent,
                        0.84f to Color.White.copy(alpha = 0.14f),
                        1f to Color.Transparent,
                    ),
                    radius = radius * 0.985f,
                )
            }
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "专辑封面",
                    modifier = Modifier.fillMaxSize(0.58f).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White.copy(alpha = 0.82f), radius = size.minDimension * 0.019f)
                drawCircle(Color.Black.copy(alpha = 0.48f), radius = size.minDimension * 0.008f)
            }
        }
    }
}
