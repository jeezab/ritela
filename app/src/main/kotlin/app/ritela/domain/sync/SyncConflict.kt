package app.ritela.domain.sync

/** Stable, non-sensitive failure codes; never attach decrypted records to diagnostics. */
enum class SyncConflict {
    CAPABILITIES_REQUIRED,
    AUTHOR_APPROVAL_REQUIRED,
    STALE_AUTHOR,
    CONFLICTING_GRANT,
    PACKET_ID_COLLISION,
    INVALID_PACKET,
    RESYNC_REQUIRED,
    INBOX_FULL
}

class SyncRejected(val reason: SyncConflict) : IllegalArgumentException(reason.name)
