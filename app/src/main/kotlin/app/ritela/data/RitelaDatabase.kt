package app.ritela.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PeriodEntity::class, DayLogEntity::class, JournalLayoutEntity::class],
    version = 3,
    exportSchema = true
)
abstract class RitelaDatabase : RoomDatabase() {
    abstract fun periods(): PeriodDao
    abstract fun dayLogs(): DayLogDao
    abstract fun journal(): JournalDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE day_logs ADD COLUMN custom TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE day_logs ADD COLUMN calendarIcon TEXT")
                db.execSQL(
                    "CREATE TABLE journal_layout (id INTEGER NOT NULL, config TEXT NOT NULL, PRIMARY KEY(id))"
                )
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS day_logs (day INTEGER NOT NULL, " +
                        "headache TEXT, cramps TEXT, backache TEXT, flow TEXT, mood TEXT, " +
                        "energy TEXT, sex TEXT NOT NULL, note TEXT NOT NULL, PRIMARY KEY(day))"
                )
            }
        }
    }
}
