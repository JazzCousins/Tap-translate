package com.example.taptranslate

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** On-device text recognition. Turkish uses the Latin script, so the Latin model covers it. */
class OcrEngine : AutoCloseable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Returns every word in the image, each with its outline and the line/paragraph it sits in. */
    suspend fun readWords(bitmap: Bitmap): List<OcrWord> {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val words = ArrayList<OcrWord>()
        for (block in result.textBlocks) {
            val blockText = block.text.replace('\n', ' ')
            for (line in block.lines) {
                for (element in line.elements) {
                    // Skip things that are only punctuation (dashes, bullets, stray marks).
                    if (element.text.none { it.isLetterOrDigit() }) continue

                    val points = element.cornerPoints
                    val corners: List<Offset> = if (points != null && points.size == 4) {
                        points.map { Offset(it.x.toFloat(), it.y.toFloat()) }
                    } else {
                        val box = element.boundingBox ?: continue
                        box.toCorners()
                    }
                    words.add(OcrWord(element.text, corners, line.text, blockText))
                }
            }
        }
        return words
    }

    override fun close() {
        recognizer.close()
    }
}

private fun Rect.toCorners(): List<Offset> = listOf(
    Offset(left.toFloat(), top.toFloat()),
    Offset(right.toFloat(), top.toFloat()),
    Offset(right.toFloat(), bottom.toFloat()),
    Offset(left.toFloat(), bottom.toFloat()),
)
