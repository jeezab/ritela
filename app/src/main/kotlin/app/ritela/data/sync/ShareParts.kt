package app.ritela.data.sync

import app.ritela.data.PartnerCodec
import app.ritela.domain.sync.SyncLimits
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Splits one confirmed share into internet packets. A share that already fits is unchanged. */
internal object ShareParts {
    private val PART_BUDGET = SyncLimits.PROJECTED_BYTES - 2048
    private const val PART_MARKER = 180

    fun transport(content: JSONObject): Boolean = content.optInt("parts", 1) > 1 &&
        content.has("batch") && content.has("part")

    /** Null when the share is over one packet and has no array that can be split. */
    fun split(content: JSONObject, batch: String): List<JSONObject>? {
        require(UUID.fromString(batch).toString() == batch)
        if (utf8(content) <= PART_BUDGET) return listOf(JSONObject(content.toString()))
        if (content.has("strokes") && !content.has("scope")) {
            return marked(batch, packDoodle(content))
        }
        val doodle = content.optJSONObject("doodle")?.let { JSONObject(it.toString()) }
        val rest = JSONObject(content.toString()).apply { remove("doodle") }
        val pieces = if (utf8(rest) <= PART_BUDGET) {
            mutableListOf(rest)
        } else {
            packRecords(rest)?.toMutableList() ?: return null
        }
        if (doodle != null && !attachDoodle(pieces, doodle)) return null
        return marked(batch, pieces)
    }

    private fun packRecords(content: JSONObject): List<JSONObject>? {
        val dayItems = content.optJSONArray("days")?.let(PartnerCodec::objects).orEmpty()
        val periodItems = content.optJSONArray("periods")?.let(PartnerCodec::objects).orEmpty()
        if (dayItems.isEmpty() && periodItems.isEmpty()) return null
        val shell = JSONObject(content.toString()).apply {
            remove("days")
            remove("periods")
        }
        if (utf8(shell) + PART_MARKER >= PART_BUDGET) return null
        val pieces = mutableListOf<JSONObject>()
        var current = JSONObject(shell.toString())
        val days = JSONArray()
        val periods = JSONArray()
        fun measured(): Int {
            val trial = JSONObject(current.toString())
            if (days.length() > 0) trial.put("days", days)
            if (periods.length() > 0) trial.put("periods", periods)
            return utf8(trial)
        }
        fun rotate() {
            if (days.length() > 0) current.put("days", JSONArray(days.toString()))
            if (periods.length() > 0) current.put("periods", JSONArray(periods.toString()))
            pieces += current
            current = JSONObject().put(
                "scope",
                JSONObject(shell.getJSONObject("scope").toString())
            )
            clear(days)
            clear(periods)
        }
        fun add(array: JSONArray, item: JSONObject): Boolean {
            array.put(JSONObject(item.toString()))
            if (measured() + PART_MARKER <= PART_BUDGET) return true
            array.remove(array.length() - 1)
            val empty = days.length() == 0 && periods.length() == 0
            if (empty && pieces.isEmpty()) {
                rotate()
                return add(array, item)
            }
            if (empty) return false
            rotate()
            return add(array, item)
        }
        for (item in periodItems) if (!add(periods, item)) return null
        for (item in dayItems) if (!add(days, item)) return null
        if (days.length() > 0 || periods.length() > 0 || pieces.isEmpty()) rotate()
        if (pieces.size == 1 && utf8(pieces[0]) > PART_BUDGET) return null
        return pieces
    }

    private fun packDoodle(doodle: JSONObject): List<JSONObject>? {
        val chunks = strokeChunks(doodle) ?: return null
        val header = JSONObject(doodle.toString()).apply { remove("strokes") }
        val pieces = mutableListOf<JSONObject>()
        var current = JSONObject(header.toString())
        val strokes = JSONArray()
        fun fits(extra: JSONObject): Boolean {
            val trialStrokes = JSONArray(strokes.toString()).put(JSONObject(extra.toString()))
            val trial = JSONObject(current.toString()).put("strokes", trialStrokes)
            return utf8(trial) + PART_MARKER <= PART_BUDGET &&
                trialStrokes.length() <= SyncLimits.DOODLE_STROKES &&
                pointCount(trialStrokes) <= SyncLimits.DOODLE_POINTS
        }
        for (chunk in chunks) {
            if (!fits(chunk)) {
                if (strokes.length() == 0) return null
                current.put("strokes", JSONArray(strokes.toString()))
                pieces += current
                current = JSONObject(header.toString())
                clear(strokes)
                if (!fits(chunk)) return null
            }
            strokes.put(JSONObject(chunk.toString()))
        }
        if (strokes.length() > 0) {
            current.put("strokes", JSONArray(strokes.toString()))
            pieces += current
        }
        return pieces
    }

    private fun attachDoodle(pieces: MutableList<JSONObject>, doodle: JSONObject): Boolean {
        val chunks = strokeChunks(doodle) ?: return false
        val header = JSONObject(doodle.toString()).apply { remove("strokes") }
        val strokes = JSONArray()
        fun fits(target: JSONObject, extra: JSONObject): Boolean {
            val trialStrokes = JSONArray(strokes.toString()).put(JSONObject(extra.toString()))
            val trial = JSONObject(target.toString()).put(
                "doodle",
                JSONObject(header.toString()).put("strokes", trialStrokes)
            )
            return utf8(trial) + PART_MARKER <= PART_BUDGET &&
                trialStrokes.length() <= SyncLimits.DOODLE_STROKES &&
                pointCount(trialStrokes) <= SyncLimits.DOODLE_POINTS
        }
        for (chunk in chunks) {
            if (!fits(pieces.last(), chunk)) {
                if (strokes.length() > 0) {
                    pieces.last().put(
                        "doodle",
                        JSONObject(header.toString()).put(
                            "strokes",
                            JSONArray(strokes.toString())
                        )
                    )
                    clear(strokes)
                }
                val scope = pieces.last().getJSONObject("scope")
                pieces += JSONObject().put("scope", JSONObject(scope.toString()))
                if (!fits(pieces.last(), chunk)) return false
            }
            strokes.put(JSONObject(chunk.toString()))
        }
        if (strokes.length() > 0) {
            pieces.last().put(
                "doodle",
                JSONObject(header.toString()).put("strokes", JSONArray(strokes.toString()))
            )
        }
        return true
    }

    private fun strokeChunks(doodle: JSONObject): List<JSONObject>? {
        val strokes = doodle.optJSONArray("strokes") ?: return null
        val result = mutableListOf<JSONObject>()
        for (stroke in PartnerCodec.objects(strokes)) {
            val points = stroke.getJSONArray("points")
            if (points.length() == 0) return null
            val base = JSONObject(stroke.toString()).apply {
                remove("points")
                remove("join")
            }
            var offset = 0
            var continued = false
            while (offset < points.length()) {
                var count = minOf(SyncLimits.DOODLE_POINTS, points.length() - offset)
                val piece = JSONObject(base.toString())
                while (true) {
                    val subset = JSONArray()
                    for (index in 0 until count) {
                        subset.put(points.getJSONArray(offset + index))
                    }
                    piece.put("points", subset)
                    if (continued) piece.put("join", true) else piece.remove("join")
                    if (utf8(piece) + 1200 <= PART_BUDGET) break
                    if (count == 1) return null
                    count = maxOf(1, count * 3 / 4)
                }
                result += JSONObject(piece.toString())
                offset += count
                continued = true
            }
        }
        return result
    }

    private fun marked(batch: String, pieces: List<JSONObject>?): List<JSONObject>? {
        if (pieces.isNullOrEmpty()) return null
        if (pieces.size == 1) return pieces.takeIf { utf8(it[0]) <= PART_BUDGET }
        val copy = pieces.map { JSONObject(it.toString()) }
        copy.forEachIndexed { index, part ->
            part.put("batch", batch).put("part", index).put("parts", copy.size)
            if (utf8(part) > PART_BUDGET) return null
        }
        return copy
    }

    fun merge(parts: List<JSONObject>): JSONObject {
        val ordered = parts.sortedBy { it.getInt("part") }
        val expected = ordered.first().getInt("parts")
        require(expected == ordered.size && expected > 1)
        require(ordered.map { it.getInt("part") } == (0 until expected).toList())
        val batch = ordered.first().getString("batch")
        require(ordered.all { it.getString("batch") == batch && it.getInt("parts") == expected })
        if (ordered.first().has("strokes") && !ordered.first().has("scope")) {
            return joinedDoodle(ordered)
        }
        val result = JSONObject(ordered.first().toString())
        result.remove("batch")
        result.remove("part")
        result.remove("parts")
        result.remove("days")
        result.remove("periods")
        result.remove("doodle")
        val days = JSONArray()
        val periods = JSONArray()
        for (part in ordered) {
            append(days, part.optJSONArray("days"))
            append(periods, part.optJSONArray("periods"))
        }
        if (periods.length() > 0) result.put("periods", periods)
        if (days.length() > 0) result.put("days", days)
        val doodles = ordered.mapNotNull { it.optJSONObject("doodle") }
        if (doodles.isNotEmpty()) result.put("doodle", joinedDoodle(doodles))
        return result
    }

    private fun joinedDoodle(parts: List<JSONObject>): JSONObject {
        val head = JSONObject(parts.first().toString())
        head.remove("strokes")
        head.remove("batch")
        head.remove("part")
        head.remove("parts")
        val strokes = JSONArray()
        var open: JSONObject? = null
        for (part in parts) {
            val array = part.getJSONArray("strokes")
            for (index in 0 until array.length()) {
                val stroke = array.getJSONObject(index)
                if (stroke.optBoolean("join") && open != null) {
                    val points = open.getJSONArray("points")
                    val more = stroke.getJSONArray("points")
                    for (point in 0 until more.length()) points.put(more.getJSONArray(point))
                } else {
                    val copy = JSONObject(stroke.toString()).apply { remove("join") }
                    strokes.put(copy)
                    open = copy
                }
            }
        }
        return head.put("strokes", strokes)
    }

    private fun append(target: JSONArray, source: JSONArray?) {
        if (source == null) return
        for (index in 0 until source.length()) target.put(source.getJSONObject(index))
    }

    private fun clear(array: JSONArray) {
        while (array.length() > 0) array.remove(array.length() - 1)
    }

    private fun pointCount(strokes: JSONArray): Int {
        var total = 0
        for (index in 0 until strokes.length()) {
            total += strokes.getJSONObject(index).getJSONArray("points").length()
        }
        return total
    }

    private fun utf8(value: JSONObject) = value.toString().toByteArray().size
}
