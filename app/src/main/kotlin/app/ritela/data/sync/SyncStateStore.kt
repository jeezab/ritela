package app.ritela.data.sync

import androidx.room.withTransaction
import app.ritela.data.RitelaDatabase
import app.ritela.domain.sync.AuthorAuthority
import app.ritela.domain.sync.AuthorityState
import app.ritela.domain.sync.OwnerGeneration
import app.ritela.domain.sync.RecordRevision
import app.ritela.domain.sync.SyncConsent
import app.ritela.domain.sync.SyncEntityType
import app.ritela.domain.sync.SyncLinkPolicy
import app.ritela.domain.sync.SyncRecordKey
import app.ritela.domain.sync.SyncRecordVersion

enum class SyncStateError { INVALID_METADATA, UNSUPPORTED_PROTOCOL, UNVERIFIED_AUTHORITY }

data class InactiveSyncMetadata(
    val consent: SyncConsent,
    val authority: AuthorAuthority,
    val links: List<SyncLinkPolicy>,
    val versions: List<SyncRecordVersion>
)

sealed interface SyncStateResult {
    data class Ready(val metadata: InactiveSyncMetadata) : SyncStateResult
    data class Unavailable(val error: SyncStateError) : SyncStateResult
}

/** Local metadata only. There is deliberately no opt-in, activation, key loader or transport. */
class SyncStateStore(private val database: RitelaDatabase) {
    suspend fun read(): SyncStateResult = database.withTransaction {
        val dao = database.syncState()
        dao.ensureDefaults()
        try {
            val profile = dao.profileStates().single()
            val row = dao.authorities().single()
            require(profile.id == 1 && row.id == 1)
            val consent = SyncConsent(flag(profile.internetEnabled))
            val authority = AuthorAuthority(
                row.ownerIdentity, row.authorDevice, row.generation, row.lastSequence,
                AuthorityState.valueOf(row.state), row.transitionId, row.previousGrantDigest,
                row.grantJson, row.grantDigest
            )
            // Structural validity never substitutes for crypto verification in a later stage.
            if (authority.state != AuthorityState.UNINITIALIZED) {
                return@withTransaction SyncStateResult.Unavailable(
                    SyncStateError.UNVERIFIED_AUTHORITY
                )
            }
            val links = mutableListOf<SyncLinkPolicy>()
            for (link in dao.links()) {
                flag(link.automaticEnabled)
                if (link.protocolVersion != 0 || link.scopeJson != null) {
                    return@withTransaction SyncStateResult.Unavailable(
                        SyncStateError.UNSUPPORTED_PROTOCOL
                    )
                }
                links += SyncLinkPolicy(
                    link.peerIdentity,
                    link.peerDevice,
                    flag(link.automaticEnabled),
                    link.scopeEpoch,
                    protocolVersion = link.protocolVersion,
                    acceptedOwnerGeneration = link.acceptedOwnerGeneration,
                    acceptedAuthorDevice = link.acceptedAuthorDevice,
                    acceptedGrantDigest = link.acceptedGrantDigest
                )
            }
            val versions = dao.versions().map {
                SyncRecordVersion(
                    SyncRecordKey(
                        it.ownerIdentity,
                        SyncEntityType.valueOf(it.entityType),
                        it.entityKey
                    ),
                    OwnerGeneration(it.generation),
                    RecordRevision(it.revision),
                    flag(it.isDeleted),
                    it.recordDigest
                )
            }
            SyncStateResult.Ready(InactiveSyncMetadata(consent, authority, links, versions))
        } catch (_: IllegalArgumentException) {
            SyncStateResult.Unavailable(SyncStateError.INVALID_METADATA)
        } catch (_: NoSuchElementException) {
            SyncStateResult.Unavailable(SyncStateError.INVALID_METADATA)
        }
    }

    private fun flag(value: Int): Boolean {
        require(value == 0 || value == 1)
        return value == 1
    }
}
