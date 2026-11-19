package com.example.taptranslate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/** On-device Turkish -> English translation. The language pack downloads once, then works offline. */
class TranslationEngine : AutoCloseable {

    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.TURKISH)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
    )

    @Volatile
    private var ready = false

    /** Downloads the language pack if it isn't on the phone yet. Throws if that fails. */
    suspend fun prepare() {
        if (ready) return
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        ready = true
    }

    suspend fun translate(text: String): String {
        prepare()
        return translator.translate(text).await()
    }

    override fun close() {
        translator.close()
    }
}
