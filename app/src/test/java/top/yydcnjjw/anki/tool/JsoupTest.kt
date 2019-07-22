package top.yydcnjjw.anki.tool

import android.util.Log
import org.jsoup.Jsoup
import org.jsoup.nodes.Entities
import org.jsoup.parser.Parser
import org.junit.Test

class JsoupTest {
    @Test
    fun Test1() {
        var doc = Jsoup.parse(Entities.escape("<p>\"<a>\"</p>"))
        println(doc)
        doc = Jsoup.parse("<p>&quot;&lt;a&gt;&quot;</p>")
        println(doc)
    }
}