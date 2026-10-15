package app.ritela.data.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Int flags are decoded strictly: corrupt values must not become Boolean true. */
@Entity(tableName = "sync_profile_state")
data class SyncProfileStateRow(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(defaultValue = "0") val internetEnabled: Int = 0
)

@Entity(tableName = "sync_authority")
data class SyncAuthorityRow(
    @PrimaryKey val id: Int = 1,
    val ownerIdentity: String? = null,
    val authorDevice: String? = null,
    @ColumnInfo(defaultValue = "0") val generation: Long = 0,
    @ColumnInfo(defaultValue = "0") val lastSequence: Long = 0,
    @ColumnInfo(defaultValue = "'UNINITIALIZED'") val state: String = "UNINITIALIZED",
    val transitionId: String? = null,
    val previousGrantDigest: String? = null,
    val grantJson: String? = null,
    val grantDigest: String? = null
)

@Entity(
    tableName = "sync_record_versions",
    primaryKeys = ["ownerIdentity", "entityType", "entityKey"]
)
data class SyncRecordVersionRow(
    val ownerIdentity: String,
    val entityType: String,
    val entityKey: String,
    val generation: Long,
    val revision: Long,
    @ColumnInfo(defaultValue = "0") val isDeleted: Int = 0,
    val recordDigest: String? = null
)

@Entity(tableName = "sync_links", primaryKeys = ["peerIdentity", "peerDevice"])
data class SyncLinkRow(
    val peerIdentity: String,
    val peerDevice: String,
    @ColumnInfo(defaultValue = "0") val automaticEnabled: Int = 0,
    @ColumnInfo(defaultValue = "0") val scopeEpoch: Long = 0,
    val scopeJson: String? = null,
    @ColumnInfo(defaultValue = "0") val protocolVersion: Int = 0,
    @ColumnInfo(defaultValue = "0") val acceptedOwnerGeneration: Long = 0,
    val acceptedAuthorDevice: String? = null,
    val acceptedGrantDigest: String? = null
)
