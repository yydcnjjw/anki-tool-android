package top.yydcnjjw.anki.tool

import android.content.Context
import com.ichi2.anki.api.AddContentApi
import com.ichi2.anki.api.NoteInfo
import javax.xml.xpath.XPathExpression

class AnkiDroidApiHelper(
    context: Context
) {
    val mApi = AddContentApi(context.applicationContext)

    companion object {
        fun isApiAvailable(context: Context) =
            AddContentApi.getAnkiDroidPackageName(context) != null
    }

    fun addNote(modelName: String, deckName: String, field: List<String>, tag: Set<String>): Boolean {
        val modelId = getModelId(modelName)
        return mApi.addNote(
            modelId,
            getDeckId(deckName)!!,
            field.toTypedArray(),
            tag
        ) != null
    }

    fun canAddNote(modelName: String, firstField: String): Boolean =
        mApi.findDuplicateNotes(
            getModelId(modelName),
            firstField
        ).isEmpty()


    private fun getModelId(modelName: String): Long = try {
        mApi.modelList.filterValues { it == modelName }
            .entries.first().key
    } catch (e: Exception) {
        throw NotFoundException("model is not found")
    }


    private fun getDeckId(deckName: String): Long? = try {
        mApi.deckList.filterValues {
            it == deckName
        }.entries.first().key
    } catch (e: Exception) {
        throw NotFoundException("deck is not found")
    }
}