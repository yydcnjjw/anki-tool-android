package top.yydcnjjw.anki.tool

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.core.text.buildSpannedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder

fun formatSimple(simples: List<WordSimple>) = buildString {
    append("<dl>")
    simples.forEach {
        append("<dt>${it.type}</dt>")
        append("<dd>")
        append("<ul>")
        it.means.forEach {
            append("<li><span>${it}<span></li>")
        }
        append("</ul>")
        append("</dd>")
    }
    append("</dl>")
}

fun formatDescs(descs: List<WordDesc>) = buildString {
    append("<dl>")
    descs.forEach {
        append("<dt>${it.first}</dt>")
        append("<dd>")
        append("<ul>")
        it.second.forEach {
            append("<li>")
            append("<span>${it.first}</span><span>${it.second}</span>")

            append("<ul>")

            it.third.forEach {
                append("<li>")
                append("${it.first} \\ ${it.second} [sound:${it.third}]")
                append("</li>")
            }

            append("</ul>")

            append("</li>")
        }
        append("</ul>")
        append("</dd>")
    }
    append("</dl>")
}

typealias WordDescMean = Triple<String, String, List<Triple<String, String, String>>>
typealias WordDesc = Pair<String, List<WordDescMean>>

data class WordSimple(
    val type: String,
    val means: List<String>
)

fun SpannableStringBuilder.append(s: String, face: Int) {
    val styleSpan = StyleSpan(face)
    val start = length
    val end = start + s.length
    append(s)
    setSpan(styleSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
}


data class WordInfo(
    var wordExpression: String,
    val wordPronounce: String,
    val wordKata: String,
    val wordTone: String,
    val wordAudio: String,
    // [{type
    //   [mean]}]
    val wordSimple: List<WordSimple>,
    // [{type
    //   [jp mean
    //    cn mean
    //    [{sentence_jp
    //      sentence_cn
    //      sentence_audio}]]}]
    val wordDescs: List<WordDesc>
) {
    fun format() = buildSpannedString {
        append("$wordExpression\n")
        append("$wordPronounce $wordKata $wordTone\n")

        if (wordSimple.isEmpty()) {
            append("No simple\n")
        } else {
            append("Simple:\n")
            wordSimple.forEach {
                append("${it.type}\n")
                it.means.forEach {
                    append("  - ")
                    append(it, Typeface.BOLD)
                    append("\n")
                }
            }
        }

        append("\n")
        append("Descs:\n")
        wordDescs.forEach {
            append("${it.first}\n")
            it.second.forEach {
                append("  - ")
                append(it.second, Typeface.BOLD)
                append("\t${it.first}\n")
                it.third.forEach {
                    append("    - ${it.first}\t${it.second}\n")
                }
            }
        }
    }
}


object HJDictService {

    private const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/69.0.3497.81 Safari/537.36"

    private val COOKIES = mapOf(
        "HJ_UID" to "0f406091-be97-6b64-f1fc-f7b2470883e9",
        "HJ_CST" to "1",
        "HJ_CSST_3" to "1",
        "TRACKSITEMAP" to "3%2C",
        "HJ_SID" to "393c85c7-abac-f408-6a32-a1f125d7e8c6",
        "_REF" to "",
        "HJ_SSID_3" to "4a460f19-c0ae-12a7-8e86-6e360f69ec9b",
        "_SREF_3" to "",
        "HJ_CMATCH" to "1"
    )

    private const val API_URL_BASE = "http://www.hjdict.com"
    private const val API_URL_JP_TO_JC = "$API_URL_BASE/jp/jc/"

    private const val CSS_NOT_FOUND = "div.word-notfound-inner"
    private const val CSS_WORD_SUGGESTIONS = "div.word-suggestions > ul > li > a"
    private const val CSS_MULTI_WORD = "header.word-details-header > ul > li"
    private const val CSS_WORD_BLOCK = "section.word-details-content > div.word-details-pane"

    private const val CSS_WORD_INFO =
        "header.word-details-pane-header > " +
                "div.word-info"

    private const val CSS_WORD_INFO_TEXT =
        "div.word-text > " +
                "h2"
    // dep CSS_WORD_INFO
    private const val CSS_WORD_INFO_PRONOUNCE =
        "div.pronounces > " +
                "span"

    private const val CSS_WORD_SIMPLE = "header.word-details-pane-header > div.simple"
    private const val CSS_WORD_DETAILS = "div.word-details-item-content > section.detail-groups > dl"

    // for cache
    private var expression: String = ""
    private var document: Document? = null

    suspend fun getDict(
        expression: String,
        pronounce: String? = null
    ): WordInfo {

        if (expression != HJDictService.expression) {
            HJDictService.expression = expression
            document = getHtmlDoc(expression)
        }

        val body = document!!.body()
        if (notFound(body)) {
            throw NotFoundException()
        }

        val suggestions = wordSuggestionsCheck(body)
        if (suggestions.isNotEmpty()) {
            throw WordSuggestionException(suggestions)
        }

        val multiWords = getMultiWords(body)

        if (multiWords.isNotEmpty() && (pronounce == null)) {
            throw MultiWordsException(multiWords)
        }

        val wordBlocks = getWordBlocks(body)

        return getDictResult(wordBlocks.find {
            if (pronounce == null) {
                true
            } else {
                it.second == expression && it.third == pronounce
            }
        }?.first ?: throw NotFoundException())
    }

    private fun notFound(elem: Element): Boolean {
        return elem.select(CSS_NOT_FOUND).isNotEmpty()
    }

    private fun wordSuggestionsCheck(elem: Element): List<String> =
        elem.select(CSS_WORD_SUGGESTIONS).map {
            URLDecoder.decode(it.attr("href"), "UTF-8").split("/").last()
        }

    /**
     * @return Pair(expression, pronounce)
     */
    private fun getMultiWords(elem: Element):
            List<Pair<String, String>> {
        val multiWords = elem.select(CSS_MULTI_WORD)
        return multiWords.map {
            Pair(
                // TODO to const
                getText(it.selectFirst("h2")),
                getText(it.selectFirst("div > span"))
            )
        }
    }

    /**
     * @return Pair(block, expression, pronounce)
     */
    private fun getWordBlocks(elem: Element):
            List<Triple<Element, String, String>> {
        val wordBlocks = elem.select(CSS_WORD_BLOCK)
        return if (wordBlocks.isNotEmpty()) {
            wordBlocks.map {
                val wordInfo = it.selectFirst(CSS_WORD_INFO) ?: throw FormatErrorException()
                Triple(
                    it,
                    getText(wordInfo.selectFirst(CSS_WORD_INFO_TEXT)),
                    getText(wordInfo.selectFirst(CSS_WORD_INFO_PRONOUNCE))
                )
            }
        } else {
            throw FormatErrorException()
        }
    }

    private fun getDictResult(elem: Element): WordInfo {
        val wordInfoElem = elem.selectFirst(CSS_WORD_INFO) ?: throw FormatErrorException()
        val wordExpression = getText(wordInfoElem.selectFirst(CSS_WORD_INFO_TEXT))

        val wordPronounces = wordInfoElem.select(CSS_WORD_INFO_PRONOUNCE)
        if (wordPronounces.isEmpty()) {
            throw FormatErrorException()
        }

        var wordPronounce = ""
        var wordKata = ""
        var wordTone = ""
        var wordAudio = ""
        for ((i, e) in wordPronounces.withIndex()) {
            when {
                i == 0 -> wordPronounce = getText(e)
                e.hasClass("pronounce-value-jp") -> wordTone = getText(e)
                e.hasClass("word-audio") -> wordAudio = e.attr("data-src")
                else -> wordKata = getText(e)
            }
        }

        val wordSimpleBlock = elem.selectFirst(CSS_WORD_SIMPLE) ?: throw FormatErrorException()

        val wordSimpleTypes = wordSimpleBlock.select("h2")
        val wordSimpleDetails = wordSimpleBlock.select("ul")
        val wordSimple = mutableListOf<WordSimple>()
        for ((i, detail) in wordSimpleDetails.withIndex()) {
            val type = if (wordSimpleTypes.size > i) {
                getText(wordSimpleTypes[i])
            } else ""

            val detailLiList = detail.select("li")
            wordSimple.add(WordSimple(type, detailLiList.map {
                Regex("\\d+\\.").replaceFirst(getText(it), "")
            }))
        }

        return WordInfo(
            wordExpression,
            wordPronounce,
            wordKata,
            wordTone,
            wordAudio,
            wordSimple,
            elem.select(CSS_WORD_DETAILS).map {
                val wordType = getText(it.selectFirst("dt"))
                val wordMeans = it.select("dd")
                val wordDescMeans = mutableListOf<WordDescMean>()
                for (mean in wordMeans) {
                    val detail = mean.select("h3 > p")
                    if (detail.size < 2) {
                        throw FormatErrorException()
                    }
                    var jpMean = getText(detail[0])
                    var cnMean = getText(detail[1])

                    // fix: hj bug text is not escape
                    if (jpMean.isEmpty() && detail[0].childNodeSize() != 0) {
                        jpMean = detail[0].children().joinToString { it.tagName() }
                    }
                    if (cnMean.isEmpty() && detail[1].childNodeSize() != 0) {
                        cnMean = detail[1].children().joinToString { it.tagName() }
                    }

                    if (cnMean.isEmpty() && jpMean.isEmpty()) {
                        continue
                    }

                    wordDescMeans.add(Triple(jpMean, cnMean, mean.select("ul > li").map {
                        val sentenceDesc = it.select("p")
                        if (sentenceDesc.size != 2) {
                            throw FormatErrorException()
                        }
                        Triple(
                            getText(sentenceDesc[0]),
                            getText(sentenceDesc[1]),
                            (sentenceDesc[0].selectFirst("span") ?: throw FormatErrorException()).attr("data-src")
                        )
                    }))
                }
                Pair(wordType, wordDescMeans)
            })
    }

    private suspend fun getHtmlDoc(expression: String): Document =
        withContext(Dispatchers.IO) {
            val connection = Jsoup.connect(API_URL_JP_TO_JC + expression)
            connection.userAgent(USER_AGENT)
            connection.cookies(COOKIES)
            connection.get()
        }

    private fun getText(elem: Element?): String = if (elem != null) elem.text() else ""
}


class NotFoundException(message: String = "Not found") : Exception(message)
class FormatErrorException(message: String = "Format Error") : Exception(message)
class MultiWordsException(
    val multiWords: List<Pair<String, String>>
) : Exception()

class WordSuggestionException(
    val suggestions: List<String> // URL
) : Exception()