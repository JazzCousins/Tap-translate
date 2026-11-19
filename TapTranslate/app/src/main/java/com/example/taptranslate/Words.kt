package com.example.taptranslate

import androidx.compose.ui.geometry.Offset

/**
 * One word found by OCR.
 *
 * [corners] are in image-pixel coordinates (top-left, top-right, bottom-right, bottom-left),
 * so tilted text still gets a properly tilted hit area.
 */
data class OcrWord(
    val text: String,
    val corners: List<Offset>,
    val lineText: String,
    val blockText: String,
) {
    /** The word without surrounding punctuation, e.g. "evlerimizden," -> "evlerimizden". */
    val clean: String
        get() = text.trim { !it.isLetterOrDigit() }
}

/**
 * Returns the word closest to [point] (image-pixel coordinates), or null if nothing is
 * within [maxDistance] pixels. A point inside a word's outline has distance 0.
 */
fun findWordAt(words: List<OcrWord>, point: Offset, maxDistance: Float): OcrWord? {
    var best: OcrWord? = null
    var bestDistance = Float.MAX_VALUE
    for (word in words) {
        val d = distanceToPolygon(point, word.corners)
        if (d <= maxDistance && d < bestDistance) {
            best = word
            bestDistance = d
        }
    }
    return best
}

private fun distanceToPolygon(p: Offset, poly: List<Offset>): Float {
    if (poly.size < 3) return Float.MAX_VALUE
    if (pointInPolygon(p, poly)) return 0f
    var best = Float.MAX_VALUE
    for (i in poly.indices) {
        val d = distanceToSegment(p, poly[i], poly[(i + 1) % poly.size])
        if (d < best) best = d
    }
    return best
}

private fun pointInPolygon(p: Offset, poly: List<Offset>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[j]
        if ((a.y > p.y) != (b.y > p.y) &&
            p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val lengthSquared = abx * abx + aby * aby
    val t = if (lengthSquared == 0f) {
        0f
    } else {
        (((p.x - a.x) * abx + (p.y - a.y) * aby) / lengthSquared).coerceIn(0f, 1f)
    }
    val dx = p.x - (a.x + t * abx)
    val dy = p.y - (a.y + t * aby)
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
