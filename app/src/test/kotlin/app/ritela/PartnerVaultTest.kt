package app.ritela

import android.content.Context
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.PartnerCrypto
import app.ritela.data.PartnerKeyVault
import java.io.File
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PartnerVaultTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun sealedIdentityPersistsAndRecoversAtomicBackupWithoutReplacingKeys() {
        val profile = UUID.randomUUID().toString()
        val key = SecretKeySpec(PartnerCrypto.random(32), "AES")
        val file = File(context.filesDir, "partner-$profile.keys")
        val vault = PartnerKeyVault(context, profile, { key })
        try {
            val original = vault.loadOrCreate()
            assertFalse(
                file.readBytes().toString(Charsets.UTF_8).contains(original.identityPrivate)
            )
            assertEquals(original, PartnerKeyVault(context, profile, { key }).loadOrCreate())
            assertTrue(file.renameTo(File(file.path + ".bak")))
            assertEquals(original, vault.loadOrCreate())
            assertTrue(file.exists())
        } finally {
            AtomicFile(file).delete()
        }
    }

    @Test fun corruptIdentityOrWrongWrappingKeyNeverGeneratesReplacementIdentity() {
        val profile = UUID.randomUUID().toString()
        val key = SecretKeySpec(PartnerCrypto.random(32), "AES")
        val file = File(context.filesDir, "partner-$profile.keys")
        val vault = PartnerKeyVault(context, profile, { key })
        try {
            val original = vault.loadOrCreate()
            val encrypted = file.readBytes()
            val wrong =
                PartnerKeyVault(context, profile, {
                    SecretKeySpec(PartnerCrypto.random(32), "AES")
                })
            assertTrue(runCatching { wrong.loadOrCreate() }.isFailure)
            assertEquals(original, vault.loadOrCreate())
            val damaged = encrypted.copyOf().also {
                it[it.lastIndex] =
                    (it.last().toInt() xor 1).toByte()
            }
            file.writeBytes(damaged)
            assertTrue(runCatching { vault.loadOrCreate() }.isFailure)
            org.junit.Assert.assertArrayEquals(damaged, file.readBytes())
        } finally {
            AtomicFile(file).delete()
        }
    }
}
