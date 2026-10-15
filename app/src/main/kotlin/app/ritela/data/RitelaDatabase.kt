package app.ritela.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.ritela.data.sync.SyncAuthorityRow
import app.ritela.data.sync.SyncLinkRow
import app.ritela.data.sync.SyncProfileStateRow
import app.ritela.data.sync.SyncRecordVersionRow
import app.ritela.data.sync.SyncStateDao

@Database(
    entities = [
        PeriodEntity::class,
        DayLogEntity::class,
        JournalLayoutEntity::class,
        ProfileSettings::class,
        PartnerDirectoryRow::class, PartnerMessage::class,
        SyncProfileStateRow::class, SyncAuthorityRow::class,
        SyncRecordVersionRow::class, SyncLinkRow::class
    ],
    version = 7,
    exportSchema = true
)
abstract class RitelaDatabase : RoomDatabase() {
    abstract fun periods(): PeriodDao
    abstract fun dayLogs(): DayLogDao
    abstract fun journal(): JournalDao
    abstract fun profileSettings(): ProfileSettingsDao

    abstract fun partner(): PartnerDao
    abstract fun syncState(): SyncStateDao

    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_profile_state (id INTEGER NOT NULL, " +
                        "internetEnabled INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_authority (id INTEGER NOT NULL, " +
                        "ownerIdentity TEXT, authorDevice TEXT, " +
                        "generation INTEGER NOT NULL DEFAULT 0, " +
                        "lastSequence INTEGER NOT NULL DEFAULT 0, " +
                        "state TEXT NOT NULL DEFAULT 'UNINITIALIZED', transitionId TEXT, " +
                        "previousGrantDigest TEXT, grantJson TEXT, " +
                        "grantDigest TEXT, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_record_versions (" +
                        "ownerIdentity TEXT NOT NULL, entityType TEXT NOT NULL, " +
                        "entityKey TEXT NOT NULL, generation INTEGER NOT NULL, " +
                        "revision INTEGER NOT NULL, isDeleted INTEGER NOT NULL DEFAULT 0, " +
                        "recordDigest TEXT, " +
                        "PRIMARY KEY(ownerIdentity, entityType, entityKey))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_links (peerIdentity TEXT NOT NULL, " +
                        "peerDevice TEXT NOT NULL, automaticEnabled INTEGER NOT NULL DEFAULT 0, " +
                        "scopeEpoch INTEGER NOT NULL DEFAULT 0, scopeJson TEXT, " +
                        "protocolVersion INTEGER NOT NULL DEFAULT 0, " +
                        "acceptedOwnerGeneration INTEGER NOT NULL DEFAULT 0, " +
                        "acceptedAuthorDevice TEXT, acceptedGrantDigest TEXT, " +
                        "PRIMARY KEY(peerIdentity, peerDevice))"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO sync_profile_state (id, internetEnabled) VALUES (1, 0)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO sync_authority (id, generation, lastSequence, state) " +
                        "VALUES (1, 0, 0, 'UNINITIALIZED')"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE partner_messages ADD COLUMN hiddenFromChat INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
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
