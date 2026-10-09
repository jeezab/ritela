package app.ritela.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PeriodEntity::class,
        DayLogEntity::class,
        JournalLayoutEntity::class,
        ProfileSettings::class,
        PartnerDirectoryRow::class, PartnerMessage::class
    ],
    version = 5,
    exportSchema = true
)
abstract class RitelaDatabase : RoomDatabase() {
    abstract fun periods(): PeriodDao
    abstract fun dayLogs(): DayLogDao
    abstract fun journal(): JournalDao
    abstract fun profileSettings(): ProfileSettingsDao

    abstract fun partner(): PartnerDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE partner_directory (id INTEGER NOT NULL, json TEXT NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE TABLE partner_messages (id TEXT NOT NULL, identity TEXT NOT NULL, device TEXT NOT NULL, outgoing INTEGER NOT NULL, kind TEXT NOT NULL, created INTEGER NOT NULL, received INTEGER NOT NULL, body TEXT NOT NULL, envelope TEXT NOT NULL, delivered INTEGER NOT NULL, opened INTEGER NOT NULL, PRIMARY KEY(id))"
                )
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE profile_settings (id INTEGER NOT NULL, language TEXT NOT NULL, " +
                        "theme TEXT NOT NULL, PRIMARY KEY(id))"
                )
            }
        }
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
