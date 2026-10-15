package app.ritela

import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import app.ritela.domain.sync.AuthorAuthority
import app.ritela.domain.sync.AuthorityState
import app.ritela.domain.sync.OwnerGeneration
import app.ritela.domain.sync.RecordRevision
import app.ritela.domain.sync.SyncConsent
import app.ritela.domain.sync.SyncEntityType
import app.ritela.domain.sync.SyncLimits
import app.ritela.domain.sync.SyncLinkPolicy
import app.ritela.domain.sync.SyncRecordKey
import app.ritela.domain.sync.nextSyncCounter
import app.ritela.domain.sync.permitsAutomaticInternet
import app.ritela.domain.sync.validateAuthoritySuccessor
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncModelsTest {
    private val owner = "a".repeat(64)
    private val device = UUID.randomUUID().toString()
    private val digest = "b".repeat(64)
    private fun authority() = AuthorAuthority(
        owner,
        device,
        1,
        5,
        AuthorityState.ACTIVE,
        grantJson = "{}",
        grantDigest = digest
    )
    private fun rejected(block: () -> Unit) {
        assertTrue(runCatching(block).exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun countersRejectZeroNegativeAndOverflowWithoutWrapping() {
        assertEquals(2L, OwnerGeneration(1).next().value)
        assertEquals(2L, RecordRevision(1).next().value)
        assertEquals(1L, nextSyncCounter(0))
        rejected { OwnerGeneration(0) }
        rejected { RecordRevision(-1) }
        rejected { nextSyncCounter(-1) }
        rejected { OwnerGeneration(Long.MAX_VALUE).next() }
        rejected { RecordRevision(Long.MAX_VALUE).next() }
    }

    @Test fun authorNeedsStructuralGrantAndUninitializedCannotCarryIdentity() {
        assertEquals(AuthorityState.UNINITIALIZED, AuthorAuthority().state)
        rejected { AuthorAuthority(ownerIdentity = owner) }
        rejected { authority().copy(grantDigest = null) }
        rejected { authority().copy(authorDevice = "phone") }
        rejected { authority().copy(state = AuthorityState.UNINITIALIZED) }
        rejected { authority().copy(generation = 2) }
        rejected { authority().copy(state = AuthorityState.TRANSFER_PENDING) }
    }

    @Test fun transferPinsDeviceGenerationAndGrantAndCannotReactivateRetiredAuthor() {
        val current = authority()
        validateAuthoritySuccessor(current, current.copy(lastSequence = 6))
        rejected { validateAuthoritySuccessor(current, current.copy(lastSequence = 4)) }
        rejected {
            validateAuthoritySuccessor(
                current,
                current.copy(authorDevice = UUID.randomUUID().toString())
            )
        }
        rejected { validateAuthoritySuccessor(current, current.copy(grantDigest = "c".repeat(64))) }
        val pending = current.copy(
            state = AuthorityState.TRANSFER_PENDING,
            transitionId = UUID.randomUUID().toString()
        )
        validateAuthoritySuccessor(current, pending)
        val next = pending.copy(
            generation = 2,
            lastSequence = 0,
            state = AuthorityState.ACTIVE,
            authorDevice = UUID.randomUUID().toString(),
            previousGrantDigest = digest,
            grantJson = "{\"next\":true}",
            grantDigest = "c".repeat(64)
        )
        validateAuthoritySuccessor(pending, next)
        rejected { validateAuthoritySuccessor(pending, next.copy(generation = 3)) }
        rejected {
            validateAuthoritySuccessor(pending, next.copy(previousGrantDigest = "d".repeat(64)))
        }
        rejected {
            validateAuthoritySuccessor(
                pending,
                next.copy(transitionId = UUID.randomUUID().toString())
            )
        }
        rejected { validateAuthoritySuccessor(pending, current) }
        rejected {
            validateAuthoritySuccessor(current.copy(state = AuthorityState.RETIRED), current)
        }
    }

    @Test fun automaticAccessNeedsIndependentGlobalConsentAndExactScope() {
        val dormant = SyncLinkPolicy(owner, device)
        assertFalse(permitsAutomaticInternet(SyncConsent(true), dormant))
        rejected { dormant.copy(automaticEnabled = true) }
        val enabled = dormant.copy(
            protocolVersion = 2,
            scopeEpoch = 1,
            automaticEnabled = true,
            scope = ShareScope(setOf(ShareCategory.PERIODS))
        )
        assertFalse(permitsAutomaticInternet(SyncConsent(), enabled))
        assertTrue(permitsAutomaticInternet(SyncConsent(true), enabled))
        rejected { enabled.copy(scope = null) }
        rejected { enabled.copy(scope = ShareScope(setOf(ShareCategory.SETTINGS))) }
        rejected { enabled.copy(scope = ShareScope(setOf(ShareCategory.DIARY))) }
        rejected { enabled.copy(protocolVersion = 1) }
        rejected { enabled.copy(acceptedOwnerGeneration = 1) }
    }

    @Test fun keysPreservePeriodUuidAndCanonicalDayWithoutNamesAsIdentifiers() {
        assertEquals(device, SyncRecordKey(owner, SyncEntityType.PERIOD, device).key)
        assertEquals("0", SyncRecordKey(owner, SyncEntityType.DAY, "0").key)
        rejected { SyncRecordKey("Name", SyncEntityType.DAY, "0") }
        rejected { SyncRecordKey(owner, SyncEntityType.PERIOD, "Name") }
        rejected { SyncRecordKey(owner, SyncEntityType.DAY, "01") }
        rejected { SyncRecordKey(owner, SyncEntityType.DAY, Long.MAX_VALUE.toString()) }
        assertTrue(
            SyncRecordKey(owner, SyncEntityType.JOURNAL_TAG, "pain/LOW") !=
                SyncRecordKey(owner, SyncEntityType.JOURNAL_TAG, "energy/LOW")
        )
        rejected { SyncRecordKey(owner, SyncEntityType.JOURNAL_TAG, "LOW") }
        rejected { authority().copy(grantJson = "\u0430".repeat(SyncLimits.GRANT_BYTES)) }
    }

    @Test fun internetDoodleBudgetChecksBytesPointsAndCaptionWithoutChangingLegacyLimits() {
        fun valid(points: List<Int> = listOf(4000), bytes: Int = 96 * 1024) =
            SyncLimits.acceptsNewDoodle(points.size, points, 240, bytes)
        assertTrue(valid())
        assertFalse(valid(listOf(4001)))
        assertFalse(valid(bytes = 96 * 1024 + 1))
        assertFalse(valid(bytes = -1))
        assertFalse(valid(List(129) { 1 }))
        assertFalse(SyncLimits.acceptsNewDoodle(1, listOf(1), 241, 1))
        assertFalse(SyncLimits.acceptsNewDoodle(2, listOf(1), 0, 1))
        // Existing domain drawings still accept more points than the internet creation budget.
        val points = List(4001) { app.ritela.domain.DoodlePoint(0.5f, 0.5f) }
        assertEquals(
            4001,
            app.ritela.domain.Doodle(
                listOf(app.ritela.domain.DoodleStroke(points, 0, 0.01f))
            ).strokes.single().points.size
        )
    }
}
