package app.ritela.data.sync

import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerCrypto
import app.ritela.domain.ShareCategory
import app.ritela.domain.sync.SyncLimits
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** V2 application payload. Manual envelopes retain the existing authenticated JCA suite.
 * This is not the future HPKE internet wrapper and provides no forward secrecy.
 */
object SyncEnvelopeCodec {
    const val SUITE = "manual-rsa-oaep-aesgcm-ecdsa-v1"
    fun capabilities(): JSONObject = JSONObject().put("protocol", 2).put("suite", SUITE)
        .put("maxEnvelope", SyncLimits.ENVELOPE_BYTES)

    fun validateCapabilities(value: JSONObject) {
        require(value.getInt("protocol") == 2 && value.getString("suite") == SUITE)
        require(value.getInt("maxEnvelope") >= SyncLimits.ENVELOPE_BYTES)
    }

    fun encode(draft: SyncDraft, generation: Long, grant: String): JSONObject =
        JSONObject(draft.payload).put("protocol", 2).put("generation", generation)
            .put("grant", grant).put("epoch", draft.scopeEpoch)
            .put("sequence", draft.linkSequence).put("kind", draft.kind)

    internal fun decode(value: JSONObject): SyncWirePacket {
        require(value.toString().toByteArray().size <= SyncLimits.PROJECTED_BYTES)
        require(value.getInt("protocol") == 2)
        val scope = PartnerCodec.scope(value.getJSONObject("scope"))
        require(scope == scope.normalized() && scope.categories.isNotEmpty())
        require(
            ShareCategory.DIARY !in scope.categories && ShareCategory.SETTINGS !in scope.categories
        )
        require(scope.sections.all { it == "note" })
        val generation = counter(value, "generation")
        val grant = value.getString("grant").also { require(it.matches(Regex("[0-9a-f]{64}"))) }
        val epoch = counter(value, "epoch")
        val sequence = counter(value, "sequence").also { require(it < Long.MAX_VALUE) }
        val kind = value.getString("kind").also { require(it in setOf("BASELINE", "DELTA")) }
        val part = value.optInt("part", 0)
        val count = value.optInt("parts", 1).also { require(it in 1..64) }
        require(part in 0 until count && sequence > part)
        if (kind == "DELTA") require(count == 1)
        val batch = value.optString("batch", "").also {
            if (count > 1) require(UUID.fromString(it).toString() == it)
        }
        val records = value.getJSONArray("records")
        require(records.length() <= SyncLimits.DELTA_OPERATIONS)
        val seen = mutableSetOf<Pair<String, String>>()
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            require(record.toString().toByteArray().size <= SyncLimits.OPERATION_BYTES)
            val type = record.getString("type")
            val key = record.getString("key")
            require(seen.add(type to key))
            when (type) {
                "PERIOD" -> require(UUID.fromString(key).toString() == key)

                "DAY" -> require(
                    key.toLongOrNull()?.let {
                        it in -25567L..376199L && it.toString() == key
                    } == true
                )

                "LAYOUT", "FORECAST" -> require(key == "current")

                else -> error("Unsupported sync record")
            }
            require(counter(record, "revision") == sequence - part)
            val deleted = record.getBoolean("deleted")
            require(deleted == record.isNull("value"))
            when (type) {
                "PERIOD" -> require(ShareCategory.PERIODS in scope.categories)
                "FORECAST" -> require(ShareCategory.FORECASTS in scope.categories)
                "DAY", "LAYOUT" -> require(scope.tags.isNotEmpty() || "note" in scope.sections)
            }
            if (!deleted) {
                val body = record.getJSONObject("value")
                val fields = when (type) {
                    "PERIOD" -> setOf("id", "start", "end", "updated")
                    "DAY" -> setOf("day", "sections", "note")
                    else -> null
                }
                if (fields != null) require(body.keys().asSequence().toSet() == fields)
                if (type == "PERIOD") require(body.getString("id") == key)
                if (type == "DAY") require(body.getLong("day").toString() == key)
                if (type == "DAY") {
                    require(body.getString("note").isEmpty() || "note" in scope.sections)
                    val sections = body.getJSONObject("sections")
                    sections.keys().forEach { section ->
                        require(
                            PartnerCodec.strings(sections.getJSONArray(section)).all {
                                it in scope.tags[section].orEmpty()
                            }
                        )
                    }
                }
                if (type == "LAYOUT") {
                    body.keys().forEach { section ->
                        val tags = body.getJSONObject(section).getJSONObject("tags")
                        require(
                            tags.keys().asSequence().all {
                                it in scope.tags[section].orEmpty()
                            }
                        )
                    }
                }
            }
        }
        return SyncWirePacket(
            generation, grant, epoch, sequence, kind, batch, part, count, records,
            canonicalJson(PartnerCodec.scope(scope))
        )
    }

    internal fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    internal fun counter(value: JSONObject, key: String): Long {
        val raw = value.get(key)
        require(raw is Int || raw is Long)
        return (raw as Number).toLong().also { require(it > 0) }
    }

    internal fun signed(raw: String, privateKey: String): String = PartnerCrypto.sign(
        PartnerCrypto.privateKey(privateKey, "EC"),
        raw
    )
}

internal class SyncWirePacket(
    val generation: Long,
    val grant: String,
    val epoch: Long,
    val sequence: Long,
    val kind: String,
    val batch: String,
    val part: Int,
    val parts: Int,
    val records: JSONArray,
    val scope: String
) {
    val firstSequence get() = sequence - part
}
