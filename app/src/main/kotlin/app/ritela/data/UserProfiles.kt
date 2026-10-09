package app.ritela.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import app.ritela.ui.PeriodViewModel
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class UserProfile(val id: String, val name: String)

/** Identity only: each profile owns a separate database. */
class ProfileRegistry(private val preferences: SharedPreferences) {
    private val catalog = preferences.getString("catalog", null)?.let(::JSONObject)
        ?: JSONObject().put("active", FIRST_ID).put(
            "users",
            JSONArray().put(JSONObject().put("id", FIRST_ID).put("name", "Я"))
        ).also { persist(it.toString()) }
    private val users = MutableStateFlow(readUsers(catalog))
    val profiles = users.asStateFlow()
    var activeId: String = catalog.getString("active")
        private set

    init {
        require(users.value.any { it.id == activeId })
    }

    @Synchronized fun add(name: String): UserProfile {
        val profile = UserProfile(UUID.randomUUID().toString(), validName(name))
        write(users.value + profile, activeId)
        return profile
    }

    @Synchronized fun rename(id: String, name: String) {
        require(users.value.any { it.id == id })
        val value = validName(name, id)
        write(users.value.map { if (it.id == id) it.copy(name = value) else it }, activeId)
    }

    @Synchronized fun select(id: String) {
        require(users.value.any { it.id == id })
        write(users.value, id)
    }

    private fun validName(name: String, excluding: String? = null): String {
        val value = name.trim()
        require(value.isNotEmpty() && value.length <= 80)
        require(users.value.none { it.id != excluding && it.name.equals(value, ignoreCase = true) })
        return value
    }

    private fun write(values: List<UserProfile>, active: String) {
        val json = JSONObject().put("active", active).put(
            "users",
            JSONArray().apply {
                values.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
            }
        )
        persist(json.toString())
        activeId = active
        users.value = values
    }

    // KTX edit returns Unit; switching must check the synchronous commit result.
    @android.annotation.SuppressLint("UseKtx")
    private fun persist(json: String) {
        val transaction = preferences.edit()
        transaction.putString("catalog", json)
        check(transaction.commit())
    }

    private fun readUsers(json: JSONObject): List<UserProfile> {
        val array = json.getJSONArray("users")
        return (0 until array.length()).map {
            val row = array.getJSONObject(it)
            UserProfile(row.getString("id"), row.getString("name")).also { profile ->
                require(UUID.fromString(profile.id).toString() == profile.id)
                require(profile.name.isNotBlank())
            }
        }.also { require(it.isNotEmpty() && it.map(UserProfile::id).distinct().size == it.size) }
    }

    companion object {
        const val FIRST_ID = "00000000-0000-0000-0000-000000000001"
    }
}

class ProfileSession(context: Context, val profileId: String) : ViewModelStoreOwner {
    val token = UUID.randomUUID().toString()
    override val viewModelStore = ViewModelStore()
    private val database = Room.databaseBuilder(
        context,
        RitelaDatabase::class.java,
        if (profileId == ProfileRegistry.FIRST_ID) "ritela.db" else "ritela-$profileId.db"
    ).addMigrations(
        RitelaDatabase.MIGRATION_1_2,
        RitelaDatabase.MIGRATION_2_3,
        RitelaDatabase.MIGRATION_3_4
    ).build()
    val periods = PeriodRepository(database.periods())
    val days = DayLogRepository(database.dayLogs())
    val journal = JournalRepository(database.journal())
    val settings = SettingsRepository(
        preferences = if (profileId == ProfileRegistry.FIRST_ID) {
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        } else {
            null
        },
        dao = database.profileSettings(),
        initialLanguage = if (profileId == ProfileRegistry.FIRST_ID &&
            Build.VERSION.SDK_INT >= 33
        ) {
            context.getSystemService(android.app.LocaleManager::class.java)
                .applicationLocales.toLanguageTags().substringBefore(',')
                .substringBefore('-').takeIf { it in listOf("ru", "en") } ?: "system"
        } else {
            "system"
        }
    )
    val backups = BackupRepository(database)
    val backupActive = MutableStateFlow(false)
    val model by lazy {
        ViewModelProvider(
            this,
            viewModelFactory {
                initializer { PeriodViewModel(periods, settings, days, journal) }
            }
        )[PeriodViewModel::class.java]
    }

    suspend fun close() {
        withContext(Dispatchers.Main.immediate) { viewModelStore.clear() }
        settings.close()
        withContext(Dispatchers.IO) { database.close() }
    }
}

class ProfileManager(private val context: Context) {
    val registry = ProfileRegistry(context.getSharedPreferences("users", Context.MODE_PRIVATE))
    private val active = MutableStateFlow(ProfileSession(context, registry.activeId))
    val session = active.asStateFlow()
    private val changing = MutableStateFlow(false)
    val switching = changing.asStateFlow()
    private val mutex = Mutex()

    suspend fun switchTo(id: String) = mutex.withLock {
        val previous = active.value
        if (previous.profileId == id) return@withLock
        check(!previous.backupActive.value && !previous.model.uiState.value.saving)
        require(registry.profiles.value.any { it.id == id })
        changing.value = true
        var next: ProfileSession? = null
        try {
            next = withContext(Dispatchers.IO) { ProfileSession(context, id) }
            next.settings.awaitReady()
            check(!previous.backupActive.value && !previous.model.uiState.value.saving)
            withContext(kotlinx.coroutines.NonCancellable) {
                withContext(Dispatchers.IO) { registry.select(id) }
                active.value = requireNotNull(next)
                previous.close()
            }
        } catch (error: Exception) {
            if (active.value === previous) {
                withContext(kotlinx.coroutines.NonCancellable) { next?.close() }
            }
            throw error
        } finally {
            changing.value = false
        }
    }
}
