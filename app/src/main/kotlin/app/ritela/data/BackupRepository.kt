package app.ritela.data

import android.util.Base64
import androidx.room.withTransaction
import app.ritela.domain.validateDayLog
import app.ritela.domain.validatePeriod
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class BackupCategory { PERIODS, DAYS, JOURNAL, SETTINGS }

data class BackupSelection(val categories: Set<BackupCategory> = BackupCategory.entries.toSet()) {
    init {
        require(categories.isNotEmpty())
    }
}

data class BackupData(
    val periods: List<PeriodEntity>,
    val logs: List<DayLogEntity>,
    val layout: app.ritela.domain.JournalLayout? = null,
    val settings: ProfileSettings? = null,
    val available: Set<BackupCategory> = buildSet {
        add(BackupCategory.PERIODS)
        add(BackupCategory.DAYS)
        if (layout != null) add(BackupCategory.JOURNAL)
        if (settings != null) add(BackupCategory.SETTINGS)
    }
) {
    fun selected(selection: BackupSelection): BackupData {
        val selected = available.intersect(selection.categories)
        require(selected.isNotEmpty())
        return BackupData(
            if (BackupCategory.PERIODS in selected) periods else emptyList(),
            if (BackupCategory.DAYS in selected) logs else emptyList(),
            if (BackupCategory.JOURNAL in selected) layout else null,
            if (BackupCategory.SETTINGS in selected) settings else null,
            selected
        )
    }
}

/** Portable backup with optional encryption. Conflicting records are never overwritten. */
class BackupRepository(
    private val database: RitelaDatabase,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend fun export(
        password: CharArray,
        selection: BackupSelection = BackupSelection()
    ): ByteArray = withContext(Dispatchers.IO) {
        try {
            val data = database.withTransaction {
                BackupData(
                    database.periods().snapshot(),
                    database.dayLogs().snapshot(),
                    database.journal().snapshot()?.let {
                        JournalCodec.decode(it.config)
                    } ?: app.ritela.domain.JournalLayout(),
                    database.profileSettings().snapshot() ?: ProfileSettings()
                ).selected(selection)
            }
            val plain = encode(data).toByteArray(Charsets.UTF_8)
            require(plain.size <= MAX_BYTES)
            if (password.isEmpty()) {
                return@withContext JSONObject().put("format", "ritela.backup.plain")
                    .put("version", 1).put("data", JSONObject(plain.toString(Charsets.UTF_8)))
                    .toString().toByteArray(Charsets.UTF_8).also { plain.fill(0) }
            }
            val random = SecureRandom()
            val salt = ByteArray(16).also(random::nextBytes)
            val iv = ByteArray(12).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            cipher.updateAAD(AAD)
            JSONObject().put("format", "ritela.backup").put("version", 1)
                .put("iterations", ITERATIONS).put("salt", base64(salt)).put("iv", base64(iv))
                .put("data", base64(cipher.doFinal(plain))).toString().toByteArray(Charsets.UTF_8)
                .also { plain.fill(0) }
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun preview(bytes: ByteArray, password: CharArray): BackupData =
        withContext(Dispatchers.IO) {
            try {
                require(bytes.size <= MAX_FILE_BYTES)
                val envelope = JSONObject(bytes.toString(Charsets.UTF_8))
                if (envelope.optString("format") == "ritela.backup.plain") {
                    require(integer(envelope, "version") == 1L)
                    val text = envelope.getJSONObject("data").toString()
                    require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
                    return@withContext decode(text).also(::validate)
                }
                require(
                    envelope.getString("format") == "ritela.backup" &&
                        integer(envelope, "version") == 1L
                )
                require(integer(envelope, "iterations") == ITERATIONS.toLong())
                val salt = unbase64(envelope.getString("salt"))
                val iv = unbase64(envelope.getString("iv"))
                require(salt.size == 16 && iv.size == 12)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
                cipher.updateAAD(AAD)
                val plain = cipher.doFinal(unbase64(envelope.getString("data")))
                try {
                    require(plain.size <= MAX_BYTES)
                    decode(plain.toString(Charsets.UTF_8)).also(::validate)
                } finally {
                    plain.fill(0)
                }
            } finally {
                password.fill('\u0000')
            }
        }

    suspend fun import(
        source: BackupData,
        selection: BackupSelection = BackupSelection(source.available)
    ) = withContext(Dispatchers.IO) {
        val data = source.selected(selection)
        validate(data)
        database.withTransaction {
            data.layout?.let { layout ->
                database.journal().save(JournalLayoutEntity(config = JournalCodec.encode(layout)))
            }
            data.settings?.let { database.profileSettings().save(it) }
            val existing = database.periods().snapshot().associateBy { it.id }
            for (record in data.periods.sortedBy { it.startDay }) {
                val previous = existing[record.id]
                if (previous != null) {
                    require(previous == record)
                } else {
                    require(database.periods().addIfSeparate(record))
                }
            }
            val logs = database.dayLogs().snapshot().associateBy { it.day }
            for (log in data.logs) {
                val previous = logs[log.day]
                require(previous == null || previous == log)
                if (previous == null) database.dayLogs().save(log)
            }
        }
    }

    private fun validate(data: BackupData) {
        require(data.available.isNotEmpty())
        data.settings?.let { require(it.valid()) }
        data.layout?.let { require(app.ritela.domain.validateJournalLayout(it)) }
        require(data.periods.size <= 10000 && data.logs.size <= 10000)
        val today = LocalDate.now(clock)
        val periods = data.periods.map { entity ->
            require(UUID.fromString(entity.id).toString() == entity.id)
            entity.toPeriod().also {
                require(
                    it.start.year in 1900..9999 && validatePeriod(it.start, it.end, today) == null
                )
                require(entity.createdAt >= 0 && entity.updatedAt >= entity.createdAt)
            }
        }.sortedBy { it.start }
        require(periods.map { it.id }.distinct().size == periods.size)
        require(periods.zipWithNext().none { (a, b) -> a.end == null || a.end >= b.start })
        require(data.logs.map { it.day }.distinct().size == data.logs.size)
        require(data.logs.all { validateDayLog(it.toLog(), today) && !it.toLog().empty })
    }

    private fun encode(data: BackupData): String {
        val periods = JSONArray()
        data.periods.forEach {
            periods.put(
                JSONObject().put("id", it.id).put("start", it.startDay)
                    .put("end", it.endDay ?: JSONObject.NULL)
                    .put("created", it.createdAt).put("updated", it.updatedAt)
            )
        }
        val logs = JSONArray()
        data.logs.forEach {
            logs.put(
                JSONObject().put("day", it.day)
                    .put("headache", it.headache ?: JSONObject.NULL)
                    .put("cramps", it.cramps ?: JSONObject.NULL)
                    .put("backache", it.backache ?: JSONObject.NULL)
                    .put("flow", it.flow ?: JSONObject.NULL).put("mood", it.mood ?: JSONObject.NULL)
                    .put(
                        "energy",
                        it.energy ?: JSONObject.NULL
                    ).put("sex", it.sex).put("note", it.note).put("custom", JSONObject(it.custom))
                    .put("calendarIcon", it.calendarIcon ?: JSONObject.NULL)
            )
        }
        val root = JSONObject().put("version", 3)
        if (BackupCategory.PERIODS in data.available) root.put("periods", periods)
        if (BackupCategory.DAYS in data.available) root.put("days", logs)
        if (BackupCategory.JOURNAL in data.available) {
            root.put("journal", JSONObject(JournalCodec.encode(requireNotNull(data.layout))))
        }
        if (BackupCategory.SETTINGS in data.available) {
            val settings = requireNotNull(data.settings)
            root.put(
                "settings",
                JSONObject().put("language", settings.language).put("theme", settings.theme)
            )
        }
        return root.toString()
    }

    private fun decode(text: String): BackupData {
        val root = JSONObject(text)
        val version = integer(root, "version")
        require(version in 1L..3L)
        val periods = if (version <
            3
        ) {
            root.getJSONArray("periods")
        } else {
            root.optJSONArray("periods") ?: JSONArray()
        }
        val logs = if (version <
            3
        ) {
            root.getJSONArray("days")
        } else {
            root.optJSONArray("days") ?: JSONArray()
        }
        require(periods.length() <= 10000 && logs.length() <= 10000)
        val available = buildSet {
            if (root.has("periods")) add(BackupCategory.PERIODS)
            if (root.has("days")) add(BackupCategory.DAYS)
            if (!root.isNull("journal")) add(BackupCategory.JOURNAL)
            if (version >= 3 && !root.isNull("settings")) add(BackupCategory.SETTINGS)
        }
        require(available.isNotEmpty())
        if (BackupCategory.PERIODS in available) require(root.get("periods") is JSONArray)
        if (BackupCategory.DAYS in available) require(root.get("days") is JSONArray)
        return BackupData(
            (0 until periods.length()).map { index ->
                val p = periods.getJSONObject(index)
                PeriodEntity(
                    p.getString("id"),
                    integer(p, "start"),
                    if (p.isNull("end")) null else integer(p, "end"),
                    integer(p, "created"),
                    integer(p, "updated")
                )
            },
            (0 until logs.length()).map { index ->
                val d = logs.getJSONObject(index)
                fun nullable(name: String) = if (d.isNull(name)) null else d.getString(name)
                DayLogEntity(
                    integer(d, "day"), nullable("headache"), nullable("cramps"),
                    nullable("backache"), nullable("flow"), nullable("mood"), nullable("energy"),
                    d.getString("sex"), d.getString("note"),
                    d.optJSONObject("custom")?.toString() ?: "{}",
                    if (d.isNull("calendarIcon")) null else d.getString("calendarIcon")
                )
            },
            if (root.isNull(
                    "journal"
                )
            ) {
                null
            } else {
                JournalCodec.decode(root.getJSONObject("journal").toString())
            },
            if (BackupCategory.SETTINGS in available) {
                val settings = root.getJSONObject("settings")
                ProfileSettings(
                    language = settings.getString("language"),
                    theme = settings.getString("theme")
                )
            } else {
                null
            },
            available
        )
    }

    private fun integer(json: JSONObject, name: String): Long {
        val value = json.get(name)
        require(value is Int || value is Long)
        return (value as Number).toLong()
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance(
                "PBKDF2WithHmacSHA256"
            ).generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun base64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unbase64(text: String) = Base64.decode(text, Base64.NO_WRAP)

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        const val MAX_FILE_BYTES = 3 * 1024 * 1024
        private const val ITERATIONS = 600000
        private val AAD = "ritela.backup:1".toByteArray(Charsets.UTF_8)
    }
}
