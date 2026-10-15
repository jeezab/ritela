package app.ritela.domain.sync

import java.util.UUID

internal const val INVALID_SYNC_METADATA = "Invalid sync metadata"

internal fun syncDigest(value: String?): Boolean =
    value != null && value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

internal fun syncIdentity(value: String?): Boolean = syncDigest(value)

internal fun syncUuid(value: String?): Boolean = value != null && runCatching {
    UUID.fromString(value).toString() == value
}.getOrDefault(false)

fun nextSyncCounter(value: Long): Long {
    require(value >= 0 && value < Long.MAX_VALUE) { INVALID_SYNC_METADATA }
    return value + 1
}

/** Consent is necessary, but transport trust and crypto verification are still required. */
fun permitsAutomaticInternet(consent: SyncConsent, link: SyncLinkPolicy): Boolean =
    consent.internetEnabled && link.automaticEnabled && link.protocolVersion == 2

/** Validate a proposed local transition only; this does not execute a distributed handover. */
fun validateAuthoritySuccessor(previous: AuthorAuthority, next: AuthorAuthority) {
    require(previous.generation > 0 && next.generation > 0) { INVALID_SYNC_METADATA }
    require(previous.ownerIdentity == next.ownerIdentity) { INVALID_SYNC_METADATA }
    if (next.generation == previous.generation) {
        require(
            previous.authorDevice == next.authorDevice &&
                previous.grantDigest == next.grantDigest &&
                previous.grantJson == next.grantJson &&
                previous.previousGrantDigest == next.previousGrantDigest &&
                next.lastSequence >= previous.lastSequence
        ) { INVALID_SYNC_METADATA }
        val allowed = when (previous.state) {
            AuthorityState.ACTIVE -> setOf(
                AuthorityState.ACTIVE,
                AuthorityState.TRANSFER_PENDING,
                AuthorityState.RETIRED,
                AuthorityState.RECOVERY_REQUIRED
            )

            AuthorityState.TRANSFER_PENDING -> setOf(
                AuthorityState.TRANSFER_PENDING,
                AuthorityState.RETIRED,
                AuthorityState.RECOVERY_REQUIRED
            )

            AuthorityState.RETIRED -> setOf(AuthorityState.RETIRED)

            AuthorityState.RECOVERY_REQUIRED -> setOf(AuthorityState.RECOVERY_REQUIRED)

            AuthorityState.UNINITIALIZED -> emptySet()
        }
        require(next.state in allowed) { INVALID_SYNC_METADATA }
        require(
            previous.transitionId == null || previous.transitionId == next.transitionId
        ) { INVALID_SYNC_METADATA }
    } else {
        require(
            next.generation == nextSyncCounter(previous.generation) &&
                previous.state == AuthorityState.TRANSFER_PENDING &&
                next.state == AuthorityState.ACTIVE &&
                next.authorDevice != previous.authorDevice &&
                next.previousGrantDigest == previous.grantDigest &&
                next.transitionId == previous.transitionId && next.lastSequence == 0L
        ) { INVALID_SYNC_METADATA }
    }
}
