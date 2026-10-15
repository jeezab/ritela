package app.ritela.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import app.ritela.data.PartnerCrypto
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.json.JSONObject

class RelayKeys(
    val handle: String,
    val apiPublic: String,
    val apiPrivate: String,
    val hpke: KeysetHandle
) {
    override fun toString() = "RelayKeys(redacted)"
}

/** Separate from identity export; no invocation/key generation on profile open. */
class RelayKeyVault(
    context: Context,
    private val profile: String,
    private val wrappingOverride: (() -> SecretKey)? = null
) {
    private val file = AtomicFile(File(context.filesDir, "relay-$profile.keys"))
    init {
        require(UUID.fromString(profile).toString() == profile)
    }
    private fun exists() = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
    private fun wrapping(): SecretKey {
        wrappingOverride?.let { return it() }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("ritela.relay.$profile", null) as? SecretKey)?.let { return it }
        check(!exists()) { "Relay wrapping key unavailable" }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    "ritela.relay.$profile",
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_NONE
                    ).setKeySize(256).build()
            )
            generateKey()
        }
    }

    @Synchronized fun load(): RelayKeys? {
        if (!exists()) return null
        com.google.crypto.tink.hybrid.HybridConfig.register()
        val bytes = file.readFully()
        require(bytes.size in 29..65536)
        val plain = PartnerCrypto.crypt(
            Cipher.DECRYPT_MODE,
            wrapping(),
            bytes.copyOfRange(0, 12),
            "ritela.relay:$profile:1",
            bytes.copyOfRange(12, bytes.size)
        )
        return try {
            val value = JSONObject(plain.toString(Charsets.UTF_8))
            require(value.getInt("v") == 1)
            RelayKeys(
                value.getString("handle"),
                value.getString("public"),
                value.getString("private"),
                TinkJsonProtoKeysetFormat.parseKeyset(
                    value.getString("hpke"),
                    InsecureSecretKeyAccess.get()
                )
            )
        } finally {
            plain.fill(0)
        }
    }

    @Synchronized fun create(consentEnabled: Boolean): RelayKeys {
        check(consentEnabled)
        load()?.let { return it }
        val pair = PartnerCrypto.ec()
        val keys = RelayKeys(
            UUID.randomUUID().toString(),
            PartnerCrypto.b64(pair.public.encoded),
            PartnerCrypto.b64(pair.private.encoded),
            PartnerEnvelopeCrypto.newKeyset()
        )
        val value = JSONObject().put(
            "v",
            1
        ).put("handle", keys.handle).put("public", keys.apiPublic)
            .put(
                "private",
                keys.apiPrivate
            ).put(
                "hpke",
                TinkJsonProtoKeysetFormat.serializeKeyset(keys.hpke, InsecureSecretKeyAccess.get())
            ).toString().toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapping())
        cipher.updateAAD("ritela.relay:$profile:1".toByteArray())
        val encrypted = try {
            cipher.iv + cipher.doFinal(value)
        } finally {
            value.fill(0)
        }
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (
            error: Exception
        ) {
            file.failWrite(stream)
            throw error
        }
        return keys
    }

    companion object {
        fun delete(context: Context, profile: String) {
            require(UUID.fromString(profile).toString() == profile)
            val file = AtomicFile(File(context.filesDir, "relay-$profile.keys"))
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry("ritela.relay.$profile")
            file.delete()
        }
    }
}
