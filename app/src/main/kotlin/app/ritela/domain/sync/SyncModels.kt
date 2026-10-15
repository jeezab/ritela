package app.ritela.domain.sync

import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import app.ritela.domain.validJournalId

@JvmInline
value class OwnerGeneration(val value: Long) {
    init {
        require(value > 0) { INVALID_SYNC_METADATA }
    }
    fun next(): OwnerGeneration = OwnerGeneration(nextSyncCounter(value))
}

@JvmInline
value class RecordRevision(val value: Long) {
    init {
        require(value > 0) { INVALID_SYNC_METADATA }
    }
    fun next(): RecordRevision = RecordRevision(nextSyncCounter(value))
}

enum class AuthorityState { UNINITIALIZED, ACTIVE, TRANSFER_PENDING, RETIRED, RECOVERY_REQUIRED }
enum class SyncEntityType { PERIOD, DAY, JOURNAL_SECTION, JOURNAL_TAG, FORECAST }

data class SyncConsent(val internetEnabled: Boolean = false)

/** A structural grant reference, not proof that a signature or transfer was verified. */
data class AuthorAuthority(
    val ownerIdentity: String? = null,
    val authorDevice: String? = null,
    val generation: Long = 0,
    val lastSequence: Long = 0,
    val state: AuthorityState = AuthorityState.UNINITIALIZED,
    val transitionId: String? = null,
    val previousGrantDigest: String? = null,
    val grantJson: String? = null,
    val grantDigest: String? = null
) {
    init {
        require(generation >= 0 && lastSequence >= 0) { INVALID_SYNC_METADATA }
        if (generation == 0L) {
            require(
                state in setOf(AuthorityState.UNINITIALIZED, AuthorityState.RECOVERY_REQUIRED) &&
                    lastSequence == 0L && ownerIdentity == null && authorDevice == null &&
                    transitionId == null && previousGrantDigest == null &&
                    grantJson == null && grantDigest == null
            ) { INVALID_SYNC_METADATA }
        } else {
            require(state != AuthorityState.UNINITIALIZED) { INVALID_SYNC_METADATA }
            require(syncIdentity(ownerIdentity) && syncUuid(authorDevice)) { INVALID_SYNC_METADATA }
            require(syncDigest(grantDigest) && !grantJson.isNullOrBlank()) { INVALID_SYNC_METADATA }
            require(grantJson.length <= SyncLimits.GRANT_BYTES) { INVALID_SYNC_METADATA }
            require(transitionId == null || syncUuid(transitionId)) { INVALID_SYNC_METADATA }
            require(previousGrantDigest == null || syncDigest(previousGrantDigest)) {
                INVALID_SYNC_METADATA
            }
            require(generation == 1L || previousGrantDigest != null) { INVALID_SYNC_METADATA }
            require(state != AuthorityState.TRANSFER_PENDING || transitionId != null) {
                INVALID_SYNC_METADATA
            }
        }
    }
}

data class SyncRecordKey(val ownerIdentity: String, val type: SyncEntityType, val key: String) {
    init {
        require(syncIdentity(ownerIdentity)) { INVALID_SYNC_METADATA }
        require(
            when (type) {
                SyncEntityType.PERIOD -> syncUuid(key)

                SyncEntityType.DAY -> key.toLongOrNull()?.let {
                    it.toString() == key && it in -25567L..376199L
                } == true

                SyncEntityType.JOURNAL_SECTION, SyncEntityType.JOURNAL_TAG -> validJournalId(key)

                SyncEntityType.FORECAST -> key == "current"
            }
        ) { INVALID_SYNC_METADATA }
    }
}

data class SyncRecordVersion(
    val key: SyncRecordKey,
    val generation: OwnerGeneration,
    val revision: RecordRevision,
    val deleted: Boolean = false,
    val recordDigest: String? = null
) {
    init {
        require(recordDigest == null || syncDigest(recordDigest)) { INVALID_SYNC_METADATA }
    }
}

/** The scope is already explicitly confirmed and frozen by PartnerSharing.remembered. */
data class SyncLinkPolicy(
    val peerIdentity: String,
    val peerDevice: String,
    val automaticEnabled: Boolean = false,
    val scopeEpoch: Long = 0,
    val scope: ShareScope? = null,
    val protocolVersion: Int = 0,
    val acceptedOwnerGeneration: Long = 0,
    val acceptedAuthorDevice: String? = null,
    val acceptedGrantDigest: String? = null
) {
    init {
        require(syncIdentity(peerIdentity) && syncUuid(peerDevice)) { INVALID_SYNC_METADATA }
        require(scopeEpoch >= 0 && acceptedOwnerGeneration >= 0) { INVALID_SYNC_METADATA }
        require(protocolVersion == 0 || protocolVersion == 2) { INVALID_SYNC_METADATA }
        if (protocolVersion == 0) {
            require(
                !automaticEnabled && scopeEpoch == 0L && scope == null &&
                    acceptedOwnerGeneration == 0L && acceptedAuthorDevice == null &&
                    acceptedGrantDigest == null
            ) { INVALID_SYNC_METADATA }
        } else {
            require(scopeEpoch > 0 && scope != null && scope == scope.normalized()) {
                INVALID_SYNC_METADATA
            }
            require(
                ShareCategory.SETTINGS !in scope.categories &&
                    ShareCategory.DIARY !in scope.categories &&
                    (!automaticEnabled || scope.categories.isNotEmpty())
            ) { INVALID_SYNC_METADATA }
            if (acceptedOwnerGeneration == 0L) {
                require(acceptedAuthorDevice == null && acceptedGrantDigest == null) {
                    INVALID_SYNC_METADATA
                }
            } else {
                require(syncUuid(acceptedAuthorDevice) && syncDigest(acceptedGrantDigest)) {
                    INVALID_SYNC_METADATA
                }
            }
        }
    }
}
