package app.ritela.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profile_settings")
data class ProfileSettings(
    @PrimaryKey val id: Int = 1,
    val language: String = "system",
    val theme: String = ThemeMode.SYSTEM.name
) {
    fun valid(): Boolean = id == 1 && language in listOf("system", "ru", "en") &&
        ThemeMode.entries.any { it.name == theme }
}

@Dao
interface ProfileSettingsDao {
    @Query("SELECT * FROM profile_settings WHERE id = 1")
    fun observe(): Flow<ProfileSettings?>

    @Query("SELECT * FROM profile_settings WHERE id = 1")
    suspend fun snapshot(): ProfileSettings?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seed(settings: ProfileSettings)

    @Upsert
    suspend fun save(settings: ProfileSettings)

    @Query("UPDATE profile_settings SET language = :language WHERE id = 1")
    suspend fun setLanguage(language: String)

    @Query("UPDATE profile_settings SET theme = :theme WHERE id = 1")
    suspend fun setTheme(theme: String)
}
