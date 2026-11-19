package com.example.taptranslate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.taptranslate.OcrWord
import com.example.taptranslate.findWordAt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_ZOOM = 8f

/**
 * Maps between image pixels and on-screen pixels. The image is fitted ("contain") and centred
 * in the view, then scaled by [zoom] and shifted by [pan].
 */
private class ViewTransform(
    val imageW: Float,
    val imageH: Float,
    val viewW: Float,
    val viewH: Float,
    val zoom: Float,
    val pan: Offset,
) {
    val scale: Float = min(viewW / imageW, viewH / imageH) * zoom

    fun toView(p: Offset): Offset = Offset(
        viewW / 2f + pan.x + (p.x - imageW / 2f) * scale,
        viewH / 2f + pan.y + (p.y - imageH / 2f) * scale,
    )

    fun toImage(v: Offset): Offset = Offset(
        imageW / 2f + (v.x - viewW / 2f - pan.x) / scale,
        imageH / 2f + (v.y - viewH / 2f - pan.y) / scale,
    )
}

/** Keeps the image from being dragged off-screen. If it's smaller than the view, it stays centred. */
private fun clampPan(
    pan: Offset,
    imageW: Float,
    imageH: Float,
    viewW: Float,
    viewH: Float,
    zoom: Float,
): Offset {
    val scale = min(viewW / imageW, viewH / imageH) * zoom
    val maxX = max(0f, (imageW * scale - viewW) / 2f)
    val maxY = max(0f, (imageH * scale - viewH) / 2f)
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

/**
 * Shows [bitmap] with a faint outline around every OCR word. Pinch to zoom, drag to pan,
 * tap a word to report it through [onTap] (with the tap position in this view's coordinates).
 * Tapping empty space reports null.
 */
@Composable
fun TappableImage(
    bitmap: ImageBitmap,
    words: List<OcrWord>,
    selected: OcrWord?,
    onTap: (OcrWord?, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var pan by remember(bitmap) { mutableStateOf(Offset.Zero) }
    val currentWords by rememberUpdatedState(words)
    val currentOnTap by rememberUpdatedState(onTap)

    val imageW = bitmap.width.toFloat()
    val imageH = bitmap.height.toFloat()

    val primary = MaterialTheme.colorScheme.primary
    val outlineColor = primary.copy(alpha = 0.6f)
    val selectedFill = primary.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier
            .clipToBounds()
            .pointerInput(bitmap) {
                detectTapGestures { tap ->
                    val transform = ViewTransform(
                        imageW, imageH, size.width.toFloat(), size.height.toFloat(), zoom, pan,
                    )
                    val imagePoint = transform.toImage(tap)
                    // A finger is fatter than a word: allow ~10dp of slack around each outline.
                    val slack = 10.dp.toPx() / transform.scale
                    currentOnTap(findWordAt(currentWords, imagePoint, slack), tap)
                }
            }
            .pointerInput(bitmap) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val viewW = size.width.toFloat()
                    val viewH = size.height.toFloat()
                    val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                    val ratio = newZoom / zoom
                    // Keep the image point under the fingers where it was.
                    val c = Offset(centroid.x - viewW / 2f, centroid.y - viewH / 2f)
                    val newPan = c + panChange - (c - pan) * ratio
                    zoom = newZoom
                    pan = clampPan(newPan, imageW, imageH, viewW, viewH, newZoom)
                }
            },
    ) {
        val transform = ViewTransform(imageW, imageH, size.width, size.height, zoom, pan)
        val drawnW = imageW * transform.scale
        val drawnH = imageH * transform.scale

        drawImage(
            image = bitmap,
            dstOffset = IntOffset(
                (size.width / 2f + pan.x - drawnW / 2f).roundToInt(),
                (size.height / 2f + pan.y - drawnH / 2f).roundToInt(),
            ),
            dstSize = IntSize(drawnW.roundToInt(), drawnH.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )

        val thin = Stroke(width = 1.5.dp.toPx())
        val thick = Stroke(width = 2.5.dp.toPx())

        fun outlineOf(word: OcrWord): Path {
            val path = Path()
            word.corners.forEachIndexed { index, corner ->
                val v = transform.toView(corner)
                if (index == 0) path.moveTo(v.x, v.y) else path.lineTo(v.x, v.y)
            }
            path.close()
            return path
        }

        for (word in words) {
            if (word !== selected) {
                drawPath(outlineOf(word), color = outlineColor, style = thin)
            }
        }
        if (selected != null) {
            val path = outlineOf(selected)
            drawPath(path, color = selectedFill)
            drawPath(path, color = primary, style = thick)
        }
    }
}
