package app.ritela.data.sync

import app.ritela.data.JournalCodec
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import app.ritela.domain.historySections
import org.json.JSONArray
import org.json.JSONObject

internal data class ProjectedChange(
    val type: String,
    val key: String,
    val revision: Long,
    val body: String?
)

/** Projects only the frozen explicit scope. No receiver-side prediction or broad diary grant. */
class ShareProjector(private val forecasts: (suspend () -> JSONObject)? = null) {
    internal suspend fun project(
        rows: List<SyncChangeRow>,
        scope: ShareScope,
        baseline: Boolean
    ): List<ProjectedChange> {
        require(
            ShareCategory.DIARY !in scope.categories && ShareCategory.SETTINGS !in scope.categories
        )
        val diary = scope.tags.isNotEmpty() || "note" in scope.sections
        val result = rows.mapNotNull { row ->
            val body: String? = when {
                row.entityType == "PERIOD" && ShareCategory.PERIODS in scope.categories ->
                    if (row.deleted) null else canonicalJson(JSONObject(row.payload))

                row.entityType == "DAY" && diary ->
                    if (row.deleted) null else day(JSONObject(row.payload), scope)

                row.entityType == "LAYOUT" && diary -> labels(row.payload, scope)

                else -> return@mapNotNull null
            }
            ProjectedChange(row.entityType, row.entityKey, row.sequence, body)
        }.toMutableList()
        if (ShareCategory.FORECASTS in scope.categories &&
            (baseline || rows.any { it.entityType in setOf("PERIOD", "FORECAST") })
        ) {
            // The provider must use the owner's existing FORECASTS-only preview in one DB snapshot.
            val snapshot =
                forecasts?.invoke() ?: throw SyncPreparationException("FORECAST_UNAVAILABLE")
            val projected = JSONObject()
            for (key in listOf(
                "periods", "forecasts", "cycleDay", "duration", "phase", "asOf", "calculatedAt",
                "algorithm", "cycleLength"
            )) {
                if (snapshot.has(key)) projected.put(key, snapshot.get(key))
            }
            require((projected.optJSONArray("periods")?.length() ?: 0) <= 1)
            result += ProjectedChange(
                "FORECAST",
                "current",
                rows.maxOfOrNull { it.sequence } ?: 0,
                canonicalJson(projected)
            )
        }
        return result
    }

    private fun day(source: JSONObject, scope: ShareScope): String? {
        val selected = JSONObject()
        val sections = source.getJSONObject("sections")
        for (section in sections.keys().asSequence().sorted()) {
            val allowed = scope.tags[section].orEmpty()
            val values = sections.getJSONArray(section)
            val tags = (0 until values.length()).map {
                values.getString(it)
            }.filter { it in allowed }
            if (tags.isNotEmpty()) selected.put(section, JSONArray(tags.sorted()))
        }
        val note = if ("note" in scope.sections) source.getString("note") else ""
        if (selected.length() == 0 && note.isEmpty()) return null
        return canonicalJson(
            JSONObject().put("day", source.getLong("day"))
                .put("sections", selected).put("note", note)
        )
    }

    private fun labels(raw: String, scope: ShareScope): String? {
        val labels = JSONObject()
        JournalCodec.decode(raw).historySections().forEach { section ->
            val allowed = scope.tags[section.id].orEmpty()
            val tags = JSONObject()
            section.tags.filter { it.id in allowed }.forEach { tags.put(it.id, it.title) }
            if (tags.length() > 0) {
                labels.put(
                    section.id,
                    JSONObject().put("title", section.title).put("tags", tags)
                )
            }
        }
        return if (labels.length() == 0) null else canonicalJson(labels)
    }
}

internal class SyncPreparationException(val code: String) : IllegalStateException(code)

internal fun canonicalJson(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"

    is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") {
        JSONObject.quote(it) + ":" + canonicalJson(value.get(it))
    }

    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") {
        canonicalJson(value.get(it))
    }

    is String -> JSONObject.quote(value)

    else -> value.toString()
}
