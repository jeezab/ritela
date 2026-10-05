package app.ritela

import android.app.Application
import androidx.room.Room
import app.ritela.data.BackupRepository
import app.ritela.data.DayLogRepository
import app.ritela.data.PeriodRepository
import app.ritela.data.RitelaDatabase
import app.ritela.data.SettingsRepository

class RitelaApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, RitelaDatabase::class.java, "ritela.db")
            .addMigrations(RitelaDatabase.MIGRATION_1_2).build()
    }
    val periods by lazy { PeriodRepository(database.periods()) }
    val settings by lazy { SettingsRepository(getSharedPreferences("settings", MODE_PRIVATE)) }
    val days by lazy { DayLogRepository(database.dayLogs()) }
    val backups by lazy { BackupRepository(database) }
}
