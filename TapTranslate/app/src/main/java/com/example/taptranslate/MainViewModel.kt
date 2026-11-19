package com.example.taptranslate

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State of the one-time Turkish -> English language pack download. */
enum class PackState { Checking, Ready, Failed }

/** A translation that may still be loading, or may have failed. */
sealed interface Tx {
    data object Loading : Tx
    data class Done(val text: String) : Tx
    data class Failed(val reason: String) : Tx
}

/** The word the user tapped, plus its translations as they arrive. */
data class Selection(
    val word: OcrWord,
    val wordTx: Tx = Tx.Loading,
    val lineTx: Tx = Tx.Loading,
    val meanings: Meanings = Meanings.Loading,
    /** null = not requested yet. */
    val paragraphTx: Tx? = null,
)

private const val MEANINGS_UNAVAILABLE =
    "Couldn't load meanings (they need internet). Tap the word again to retry."

private const val TRANSLATE_FAILED =
    "Couldn't translate. The Turkish language pack needs internet the first time, " +
        "so check your connection and tap the word again."

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var image by mutableStateOf<Bitmap?>(null)
        private set

    var words by mutableStateOf<List<OcrWord>>(emptyList())
        private set

    var reading by mutableStateOf(false)
        private set

    var selection by mutableStateOf<Selection?>(null)
        private set

    var pack by mutableStateOf(PackState.Checking)
        private set

    /** One-shot message for a snackbar. The UI calls [messageShown] once it has shown it. */
    var message by mutableStateOf<String?>(null)
        private set

    private val ocr = OcrEngine()
    private val translation = TranslationEngine()
    private val dictionary = DictionaryEngine()
    private var tapJob: Job? = null
    private var lookupJob: Job? = null

    init {
        preparePack()
    }

    fun preparePack() {
        pack = PackState.Checking
        viewModelScope.launch {
            pack = try {
                translation.prepare()
                PackState.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PackState.Failed
            }
        }
    }

    fun loadImage(uri: Uri) {
        if (reading) return
        tapJob?.cancel()
        selection = null
        reading = true
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageLoader.load(getApplication<Application>(), uri)
                }
                val found = ocr.readWords(bitmap)
                if (found.isEmpty()) {
                    message = "Couldn't find any text in that photo. Try getting closer, with good light."
                } else {
                    image = bitmap
                    words = found
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                message = "That photo is too large for this phone to read. Try a smaller one."
            } catch (e: Exception) {
                message = "Couldn't read that photo (${e.message ?: "unknown error"})."
            } finally {
                reading = false
            }
        }
    }

    fun closeImage() {
        tapJob?.cancel()
        lookupJob?.cancel()
        selection = null
        image = null
        words = emptyList()
    }

    /** Called when the user taps the photo. [word] is null if they tapped empty space. */
    fun onTap(word: OcrWord?) {
        tapJob?.cancel()
        lookupJob?.cancel()
        if (word == null) {
            selection = null
            return
        }
        selection = Selection(word)
        tapJob = viewModelScope.launch {
            launch {
                val tx = safeTranslate(word.clean)
                update(word) { it.copy(wordTx = tx) }
            }
            launch {
                val tx = safeTranslate(word.lineText)
                update(word) { it.copy(lineTx = tx) }
            }
            launch {
                val result = safeLookup(word.clean)
                update(word) { it.copy(meanings = result) }
            }
        }
    }

    /** Looks [term] up in the dictionary by hand, e.g. a base form the user typed in. */
    fun lookupMeanings(term: String) {
        val current = selection ?: return
        val query = term.trim()
        if (query.isEmpty()) return
        val word = current.word
        lookupJob?.cancel()
        selection = current.copy(meanings = Meanings.Loading)
        lookupJob = viewModelScope.launch {
            val result = safeLookup(query)
            update(word) { it.copy(meanings = result) }
        }
    }

    fun translateParagraph() {
        val current = selection ?: return
        if (current.paragraphTx != null) return
        val word = current.word
        selection = current.copy(paragraphTx = Tx.Loading)
        viewModelScope.launch {
            val tx = safeTranslate(word.blockText)
            update(word) { it.copy(paragraphTx = tx) }
        }
    }

    fun showMessage(text: String) {
        message = text
    }

    fun messageShown() {
        message = null
    }

    /** Applies [change] only if [word] is still the selected one (the user may have moved on). */
    private fun update(word: OcrWord, change: (Selection) -> Selection) {
        val current = selection
        if (current != null && current.word === word) {
            selection = change(current)
        }
    }

    private suspend fun safeTranslate(text: String): Tx = try {
        Tx.Done(translation.translate(text)).also { pack = PackState.Ready }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Tx.Failed(TRANSLATE_FAILED)
    }

    private suspend fun safeLookup(text: String): Meanings = try {
        dictionary.lookup(text)
    } catch (e: CancellationException) {
        throw e
    } catch (e: DictionaryHttpException) {
        Meanings.Unavailable(
            "The dictionary service answered with an error (HTTP ${e.code}). " +
                "The translation above still works; try again later.",
        )
    } catch (e: Exception) {
        Meanings.Unavailable(MEANINGS_UNAVAILABLE)
    }

    override fun onCleared() {
        ocr.close()
        translation.close()
    }
}
