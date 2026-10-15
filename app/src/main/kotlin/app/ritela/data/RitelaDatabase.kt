package app.ritela.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.ritela.data.sync.SyncAuthorityRow
import app.ritela.data.sync.SyncCapabilityRow
import app.ritela.data.sync.SyncChangeRow
import app.ritela.data.sync.SyncInboxRow
import app.ritela.data.sync.SyncJournalDao
import app.ritela.data.sync.SyncJournalStateRow
import app.ritela.data.sync.SyncLinkProgressRow
import app.ritela.data.sync.SyncLinkRow
import app.ritela.data.sync.SyncOutboxRow
import app.ritela.data.sync.SyncProfileStateRow
import app.ritela.data.sync.SyncProjectionRow
import app.ritela.data.sync.SyncQueueDao
import app.ritela.data.sync.SyncReceiveDao
import app.ritela.data.sync.SyncReceivedAuthorRow
import app.ritela.data.sync.SyncReceivedRecordRow
import app.ritela.data.sync.SyncReceivedStateRow
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
        SyncRecordVersionRow::class, SyncLinkRow::class,
        SyncChangeRow::class, SyncJournalStateRow::class, SyncLinkProgressRow::class,
        SyncOutboxRow::class, SyncProjectionRow::class,
        SyncCapabilityRow::class, SyncReceivedAuthorRow::class, SyncReceivedStateRow::class,
        SyncReceivedRecordRow::class, SyncInboxRow::class,
        app.ritela.data.sync.SyncDoodleReceiptRow::class,
        app.ritela.data.sync.SyncHandoverRow::class
    ],
    version = 9,
    exportSchema = true
)
abstract class RitelaDatabase : RoomDatabase() {
    abstract fun periods(): PeriodDao
    abstract fun dayLogs(): DayLogDao
    abstract fun journal(): JournalDao
    abstract fun profileSettings(): ProfileSettingsDao

    abstract fun partner(): PartnerDao
    abstract fun syncState(): SyncStateDao
    abstract fun syncJournal(): SyncJournalDao
    abstract fun syncQueue(): SyncQueueDao
    abstract fun syncReceive(): SyncReceiveDao

    companion object {
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_handover " +
                        "(id INTEGER NOT NULL, proposal TEXT NOT NULL, grant TEXT NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_doodle_receipts " +
                        "(id TEXT NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_capabilities (" +
                        "identity TEXT NOT NULL, " +
                        "device TEXT NOT NULL, " +
                        "context TEXT NOT NULL, " +
                        "offered INTEGER NOT NULL, " +
                        "accepted INTEGER NOT NULL, " +
                        "PRIMARY KEY(identity,device))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_received_author (" +
                        "identity TEXT NOT NULL, " +
                        "device TEXT NOT NULL, " +
                        "generation INTEGER NOT NULL, " +
                        "digest TEXT NOT NULL, " +
                        "grant TEXT NOT NULL, " +
                        "PRIMARY KEY(identity))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_received_state (" +
                        "identity TEXT NOT NULL, " +
                        "generation INTEGER NOT NULL, " +
                        "epoch INTEGER NOT NULL, " +
                        "sequence INTEGER NOT NULL, " +
                        "scope TEXT NOT NULL, snapshot TEXT NOT NULL, " +
                        "received INTEGER NOT NULL, " +
                        "PRIMARY KEY(identity))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_received_records (" +
                        "identity TEXT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "`key` TEXT NOT NULL, " +
                        "revision INTEGER NOT NULL, " +
                        "value TEXT, " +
                        "PRIMARY KEY(identity,type,`key`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_inbox (" +
                        "id TEXT NOT NULL, " +
                        "identity TEXT NOT NULL, " +
                        "device TEXT NOT NULL, " +
                        "digest TEXT NOT NULL, " +
                        "generation INTEGER NOT NULL, " +
                        "epoch INTEGER NOT NULL, " +
                        "sequence INTEGER NOT NULL, " +
                        "payload TEXT NOT NULL, " +
                        "received INTEGER NOT NULL, " +
                        "applied INTEGER NOT NULL, " +
                        "PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX index_sync_inbox_identity_generation_epoch_sequence " +
                        "ON sync_inbox(identity,generation,epoch,sequence)"
                )
                db.execSQL(
                    "CREATE INDEX index_sync_inbox_identity_applied_sequence " +
                        "ON sync_inbox(identity,applied,sequence)"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_changes (" +
                        "sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "changeId TEXT NOT NULL, " +
                        "entityType TEXT NOT NULL, entityKey TEXT NOT NULL, " +
                        "deleted INTEGER NOT NULL, " +
                        "payload TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX index_sync_changes_changeId ON sync_changes(changeId)"
                )
                db.execSQL(
                    "CREATE INDEX index_sync_changes_entityType_entityKey_sequence " +
                        "ON sync_changes(entityType,entityKey,sequence)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_journal_state (" +
                        "id INTEGER NOT NULL, floor INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_link_progress (peerIdentity TEXT NOT NULL, " +
                        "peerDevice TEXT NOT NULL, scopeEpoch INTEGER NOT NULL, " +
                        "cursor INTEGER NOT NULL, " +
                        "linkSequence INTEGER NOT NULL, policyDigest TEXT NOT NULL, " +
                        "requiresBaseline INTEGER NOT NULL, " +
                        "PRIMARY KEY(peerIdentity,peerDevice,scopeEpoch))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_projection_state " +
                        "(peerIdentity TEXT NOT NULL, " +
                        "peerDevice TEXT NOT NULL, scopeEpoch INTEGER NOT NULL, " +
                        "entityType TEXT NOT NULL, " +
                        "entityKey TEXT NOT NULL, digest TEXT NOT NULL, " +
                        "PRIMARY KEY(peerIdentity,peerDevice,scopeEpoch,entityType,entityKey))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_outbox (packetId TEXT NOT NULL, " +
                        "peerIdentity TEXT NOT NULL, peerDevice TEXT NOT NULL, " +
                        "scopeEpoch INTEGER NOT NULL, " +
                        "policyDigest TEXT NOT NULL, kind TEXT NOT NULL, " +
                        "fromCursor INTEGER NOT NULL, " +
                        "throughCursor INTEGER NOT NULL, linkSequence INTEGER NOT NULL, " +
                        "envelope BLOB NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL, " +
                        "nextAttempt INTEGER NOT NULL, leaseUntil INTEGER NOT " +
                        "NULL, leaseToken TEXT, " +
                        "errorCode TEXT, PRIMARY KEY(packetId))"
                )
                db.execSQL(
                    "CREATE INDEX index_sync_outbox_state_nextAttempt ON " +
                        "sync_outbox(state,nextAttempt)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX " +
                        "index_sync_outbox_peerIdentity_peerDevice_scopeEpoch_linkSequence " +
                        "ON sync_outbox(peerIdentity,peerDevice,scopeEpoch,linkSequence)"
                )
                db.execSQL("INSERT OR IGNORE INTO sync_journal_state (id,floor) VALUES (1,0)")
            }
        }

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
