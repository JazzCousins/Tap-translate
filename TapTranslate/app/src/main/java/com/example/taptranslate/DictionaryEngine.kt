package com.example.taptranslate

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** All the senses the dictionary lists under one part of speech (noun, verb, ...). */
data class MeaningGroup(val partOfSpeech: String, val senses: List<String>)

/** The dictionary side of a tap: every listed meaning, or why there aren't any. */
sealed interface Meanings {
    data object Loading : Meanings
    data class Found(val term: String, val groups: List<MeaningGroup>) : Meanings
    data class NotFound(val term: String) : Meanings
    data class Unavailable(val reason: String) : Meanings
}

/** The dictionary server answered, but with an error (rate limit, outage, blocked request...). */
class DictionaryHttpException(val code: Int) : IOException("Dictionary returned HTTP $code")

private const val ENDPOINT ="https://en.wiktionary.org/api/rest_v1/page/definition/"

// Wikimedia asks API clients to identify themselves. Add a contact (email or URL) in the
// parentheses if you plan to share this app or make heavy use of it.
private const val USER_AGENT = "TapTranslate/1.0 (Android; personal Turkish-learning app)"

private val TURKISH: Locale = Locale.forLanguageTag("tr-TR")

/** Grammar words that show up in "form of" senses, e.g. "first-person singular possessive of ev". */
private val FORM_WORDS = Regex(
    "\\b(possessive|plural|person|accusative|dative|locative|ablative|genitive|instrumental|" +
        "tense|participle|infinitive|aorist|converb|inflection|form|negative|causative|passive|" +
        "reciprocal|reflexive|imperative|optative|conditional|evidential|gerund|verbal noun)\\b",
    RegexOption.IGNORE_CASE,
)
private val ENDS_WITH_OF_WORD = Regex("\\bof\\s+(\\p{L}+)\\s*[:.]?\\s*$")

/**
 * If this entry is an inflected form ("first-person singular possessive of ev"), returns the
 * base word ("ev") so the UI can offer to look that up. Best effort: null when it isn't one.
 */
fun Meanings.Found.baseForm(): String? {
    for (group in groups) {
        for (sense in group.senses) {
            if (!FORM_WORDS.containsMatchIn(sense)) continue
            val base = ENDS_WITH_OF_WORD.find(sense)?.groupValues?.get(1) ?: continue
            if (!base.equals(term, ignoreCase = true)) return base
        }
    }
    return null
}

/**
 * Looks Turkish words up on English Wiktionary, which lists every sense of a word
 * (several per part of speech). Needs internet; ML Kit still translates offline.
 */
class DictionaryEngine {

    /** Empty list = "we asked, and there's no Turkish entry". */
    private val cache = ConcurrentHashMap<String, List<MeaningGroup>>()

    /**
     * Tries the word as tapped, then its Turkish-lowercased form ("Ev" -> "ev"), then the part
     * before an apostrophe ("İstanbul'da" -> "İstanbul"). Throws IOException if the dictionary
     * can't be reached.
     */
    suspend fun lookup(word: String): Meanings {
        val terms = candidates(word)
        for (term in terms) {
            val groups = fetch(term)
            if (groups != null) return Meanings.Found(term, groups)
        }
        return Meanings.NotFound(terms.firstOrNull() ?: word)
    }

    private fun candidates(word: String): List<String> {
        val out = LinkedHashSet<String>()
        val trimmed = word.trim()
        out.add(trimmed)
        out.add(trimmed.lowercase(TURKISH))
        val apostrophe = trimmed.indexOfFirst { it == '\'' || it == '’' }
        if (apostrophe > 0) {
            val stem = trimmed.substring(0, apostrophe)
            out.add(stem)
            out.add(stem.lowercase(TURKISH))
        }
        return out.filter { it.isNotEmpty() }
    }

    private suspend fun fetch(term: String): List<MeaningGroup>? = withContext(Dispatchers.IO) {
        val cached = cache[term]
        if (cached != null) {
            if (cached.isEmpty()) null else cached
        } else {
            val groups = download(term)
            cache[term] = groups ?: emptyList()
            groups
        }
    }

    /** Returns the Turkish entries for [term], or null if there are none. */
    private fun download(term: String): List<MeaningGroup>? {
        val encoded = URLEncoder.encode(term, "UTF-8").replace("+", "%20")
        val connection = URL(ENDPOINT + encoded).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (code != HttpURLConnection.HTTP_OK) throw DictionaryHttpException(code)
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val groups = parse(body)
            return if (groups.isEmpty()) null else groups
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The response is an object keyed by language code; each value is a list of entries with
     * `language`, `partOfSpeech` and `definitions[].definition` (HTML). We keep only Turkish.
     */
    private fun parse(body: String): List<MeaningGroup> {
        try {
            val root = JSONObject(body)
            val groups = ArrayList<MeaningGroup>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val entries = root.optJSONArray(key) ?: continue
                for (i in 0 until entries.length()) {
                    val entry = entries.optJSONObject(i) ?: continue
                    val isTurkish = key == "tr" || entry.optString("language").equals("Turkish", ignoreCase = true)
                    if (!isTurkish) continue
                    val definitions = entry.optJSONArray("definitions") ?: continue
                    val senses = ArrayList<String>()
                    for (j in 0 until definitions.length()) {
                        val definition = definitions.optJSONObject(j) ?: continue
                        val text = plainText(definition.optString("definition"))
                        if (text.isNotEmpty()) senses.add(text)
                    }
                    if (senses.isNotEmpty()) {
                        val partOfSpeech = entry.optString("partOfSpeech").ifEmpty { "meaning" }
                        groups.add(MeaningGroup(partOfSpeech, senses))
                    }
                }
            }
            return groups
        } catch (e: JSONException) {
            throw IOException("Unexpected dictionary response", e)
        }
    }

    private fun plainText(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("\\s+"), " ")
            .trim()
}
