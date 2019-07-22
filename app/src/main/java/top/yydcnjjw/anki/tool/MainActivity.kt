package top.yydcnjjw.anki.tool

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private var wordInfo: WordInfo? = null
    private var ankiApi: AnkiDroidApiHelper? = null
    private val checkWord: String
        get() = edit.text.toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ankiApi = AnkiDroidApiHelper(this)

        preview.movementMethod = ScrollingMovementMethod.getInstance()

        input.setOnClickListener {
            // showPreviewWordInfo(edit.text.toString())
            showPreviewWordInfo(checkWord)
        }

        commit.setOnClickListener {
            showCommit()
        }
    }

    private fun showMultiWordsDialog(multiWords: List<Pair<String, String>>) {
        val builder = AlertDialog.Builder(this)
        builder
            .setTitle("multi words")
            .setAdapter(
                ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    multiWords.map { "${it.first} ${it.second}" })
            ) { _, i ->
                showPreviewWordInfo(multiWords[i].first, multiWords[i].second)
            }.create().show()
    }

    private fun showWordSuggestionsDialog(suggestions: List<String>) {
        val builder = AlertDialog.Builder(this)
        builder
            .setTitle("Suggestion")
            .setAdapter(
                ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    suggestions
                )
            ) { _, i ->
                showPreviewWordInfo(suggestions[i])
            }.create().show()
    }

    private fun showCommit() = GlobalScope.launch(Dispatchers.Main) {
        withOnClick(commit) {
            withProgressBar(progress) {
                if (!AnkiDroidApiHelper.isApiAvailable(applicationContext)) {
                    toast("anki api is not available")
                    return@withProgressBar
                }

                if (wordInfo == null) {
                    toast("word is null")
                    return@withProgressBar
                }

                var simples = wordInfo!!.wordSimple
                val descs = wordInfo!!.wordDescs
                if (simples.isEmpty()) {
                    simples = descs.map {
                        WordSimple(it.first, // type
                            it.second.map {
                                it.second // cnMean
                            })
                    }
                }

                val fields = listOf(
                    wordInfo!!.wordExpression,
                    wordInfo!!.wordPronounce,
                    wordInfo!!.wordKata,
                    wordInfo!!.wordTone,
                    "[sound:${wordInfo!!.wordAudio}]",
                    formatSimple(simples),
                    formatDescs(descs)
                )

                try {
                    if (ankiApi?.canAddNote("japanese(dict)", fields.first())!!) {
                        if (ankiApi?.addNote(
                                "japanese(dict)",
                                "Japanese_Word",
                                fields,
                                setOf("japanese(dict)")
                            )!!
                        ) {
                            toast("add success")
                        } else {
                            toast("add failure")
                        }
                    } else {
                        toast("Can not add note! Duplicate")
                    }
                } catch (e: Exception) {
                    preview.text = "${e.message}\n${e.stackTrace.contentToString()}"
                }
            }
        }
    }

    private fun showPreviewWordInfo(expression: String, pronounce: String? = null) =
        GlobalScope.launch(Dispatchers.Main) {
            withOnClick(input) {
                withProgressBar(progress) {
                    try {
                        wordInfo = HJDictService.getDict(expression, pronounce)

                        if (pronounce != null && checkWord == wordInfo?.wordExpression) {
                            // multi word handle
                            wordInfo?.wordExpression = "${wordInfo?.wordExpression}${wordInfo?.wordPronounce}"
                        }

                        preview.text = wordInfo!!.format()
                        edit.setText(wordInfo!!.wordExpression)
                    } catch (e: WordSuggestionException) {
                        showWordSuggestionsDialog(e.suggestions)
                    } catch (e: MultiWordsException) {
                        showMultiWordsDialog(e.multiWords)
                    }
                    catch (e: NotFoundException) {
                        preview.text = "${e.message}\n"
                    }
//                catch (e: FormatErrorException) {
//                    preview.text = "${e.message}\n${e.stackTrace.contentToString()}"
//                }
                    catch (e: Exception) {
                        preview.text = "${e.message}\n${e.stackTrace.contentToString()}"
                    }

                }
            }
        }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

suspend fun withProgressBar(progress: ProgressBar, func: suspend () -> Unit) {
    progress.visibility = View.VISIBLE
    func()
    progress.visibility = View.GONE
}

suspend fun withOnClick(view: View, func: suspend () -> Unit) {
    view.isEnabled = false
    view.isClickable = false
    func()
    view.isEnabled = true
    view.isClickable = true
}