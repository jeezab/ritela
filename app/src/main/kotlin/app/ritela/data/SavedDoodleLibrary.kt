package app.ritela.data

import android.content.Context
import android.util.AtomicFile
import app.ritela.domain.Doodle
import app.ritela.domain.SavedDoodle
import app.ritela.domain.SavedDoodles
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Local drawings for one profile. Not part of cycle records or the encrypted packet store. */
class SavedDoodleLibrary(context: Context, profile: String) {
    private val file = AtomicFile(location(context, profile))
    private val mutex = Mutex()
    val items = MutableStateFlow(load())

    suspend fun save(doodle: Doodle, now: Long = System.currentTimeMillis()): Boolean =
        update { current ->
            if (current.size >= SavedDoodles.LIMIT) {
                null
            } else {
                SavedDoodles.save(current, doodle, now, UUID.randomUUID().toString())
            }
        }

    suspend fun delete(id: String) = update { SavedDoodles.delete(it, id) }

    suspend fun use(id: String) = update { SavedDoodles.use(it, id) }

    private suspend fun update(change: (List<SavedDoodle>) -> List<SavedDoodle>?): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val next = change(items.value) ?: return@withLock false
                val output = file.startWrite()
                try {
                    output.write(encode(next).toByteArray())
                    file.finishWrite(output)
                } catch (error: Exception) {
                    file.failWrite(output)
                    throw error
                }
                items.value = next
                true
            }
        }

    private fun load(): List<SavedDoodle> = runCatching {
        if (!file.baseFile.exists()) return emptyList()
        decode(file.readFully().toString(Charsets.UTF_8))
    }.getOrDefault(emptyList())

    companion object {
        fun location(context: Context, profile: String): File {
            require(UUID.fromString(profile).toString() == profile)
            return File(context.filesDir, "partner-$profile.doodles")
        }

        fun encode(items: List<SavedDoodle>): String = JSONObject().put("v", 1).put(
            "items",
            JSONArray(
                items.map { item ->
                    JSONObject().put("id", item.id).put("uses", item.uses)
                        .put("savedAt", item.savedAt)
                        .put("doodle", PartnerCodec.doodle(item.doodle))
                }
            )
        ).toString()

        fun decode(raw: String): List<SavedDoodle> {
            val root = JSONObject(raw)
            require(root.getInt("v") == 1)
            val array = root.getJSONArray("items")
            require(array.length() <= SavedDoodles.LIMIT)
            return SavedDoodles.sorted(
                PartnerCodec.objects(array).map { item ->
                    SavedDoodle(
                        item.getString("id"),
                        PartnerCodec.doodle(item.getJSONObject("doodle")),
                        item.getInt("uses"),
                        item.getLong("savedAt")
                    )
                }
            )
        }
    }
}
