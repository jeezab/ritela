package app.ritela.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/** Wire crypto uses JCA primitives; Android Keystore only wraps portable identity at rest. */
object PartnerCrypto {
    const val MAX_PACKET = 3 * 1024 * 1024
    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun bytes(value: String): ByteArray = Base64.getDecoder().decode(value)
    fun rsa(): KeyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(3072)
    }.generateKeyPair()
    fun ec(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(256)
    }.generateKeyPair()
    fun publicKey(encoded: String, algorithm: String = "RSA"): PublicKey =
        KeyFactory.getInstance(algorithm).generatePublic(X509EncodedKeySpec(bytes(encoded)))
    fun privateKey(encoded: String, algorithm: String = "RSA"): PrivateKey =
        KeyFactory.getInstance(algorithm).generatePrivate(PKCS8EncodedKeySpec(bytes(encoded)))
    fun id(publicKey: String): String =
        MessageDigest.getInstance("SHA-256").digest(bytes(publicKey))
            .joinToString("") { "%02x".format(it) }
    fun sign(key: PrivateKey, body: String): String = b64(
        Signature.getInstance(
            if (key.algorithm == "RSA") "SHA256withRSA" else "SHA256withECDSA"
        ).run {
            initSign(key)
            update(body.toByteArray())
            sign()
        }
    )
    fun verify(key: PublicKey, body: String, signature: String): Boolean = runCatching {
        Signature.getInstance(if (key.algorithm == "RSA") "SHA256withRSA" else "SHA256withECDSA")
            .run {
                initVerify(key)
                update(body.toByteArray())
                verify(bytes(signature))
            }
    }.getOrDefault(false)
    private val oaep =
        OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
    fun wrap(key: PublicKey, secret: ByteArray): ByteArray =
        Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            init(Cipher.ENCRYPT_MODE, key, oaep)
            doFinal(secret)
        }
    fun unwrap(key: PrivateKey, secret: ByteArray): ByteArray =
        Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            init(Cipher.DECRYPT_MODE, key, oaep)
            doFinal(secret)
        }
    fun random(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
    fun crypt(mode: Int, key: SecretKey, iv: ByteArray, aad: String, data: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, key, GCMParameterSpec(128, iv))
            updateAAD(aad.toByteArray())
            doFinal(data)
        }
    fun passwordKey(password: CharArray, salt: ByteArray): SecretKey {
        require(password.size in 8..256)
        val spec = PBEKeySpec(password, salt, 600000, 256)
        return try {
            SecretKeySpec(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
                "AES"
            )
        } finally {
            spec.clearPassword()
        }
    }
}

data class LocalPartnerKeys(
    val identityPublic: String,
    val identityPrivate: String,
    val deviceId: String,
    val devicePublic: String,
    val devicePrivate: String,
    val nonce: String
) {
    val identityId: String get() = PartnerCrypto.id(identityPublic)
    fun certificate(): String = PartnerCrypto.sign(
        PartnerCrypto.privateKey(identityPrivate),
        "$identityId:$deviceId:$devicePublic"
    )
    fun qr(name: String): String {
        val body = JSONObject().put("format", "ritela.pair").put("v", 1).put("name", name.take(80))
            .put("identity", identityPublic).put("device", deviceId).put("key", devicePublic)
            .put("certificate", certificate()).put("nonce", nonce).toString()
        return JSONObject().put("body", body).put(
            "signature",
            PartnerCrypto.sign(
                PartnerCrypto.privateKey(devicePrivate, "EC"),
                body
            )
        ).toString()
    }
    fun encode(): String =
        JSONObject().put("identityPublic", identityPublic).put("identityPrivate", identityPrivate)
            .put(
                "deviceId",
                deviceId
            ).put(
                "devicePublic",
                devicePublic
            ).put("devicePrivate", devicePrivate).put("nonce", nonce).toString()
    companion object {
        fun create(identity: KeyPair = PartnerCrypto.rsa()): LocalPartnerKeys {
            val device = PartnerCrypto.ec()
            return LocalPartnerKeys(
                PartnerCrypto.b64(identity.public.encoded),
                PartnerCrypto.b64(identity.private.encoded),
                UUID.randomUUID().toString(),
                PartnerCrypto.b64(device.public.encoded),
                PartnerCrypto.b64(device.private.encoded),
                UUID.randomUUID().toString()
            )
        }
        fun decode(value: String): LocalPartnerKeys = JSONObject(value).let {
            LocalPartnerKeys(
                it.getString("identityPublic"),
                it.getString("identityPrivate"),
                it.getString("deviceId"),
                it.getString("devicePublic"),
                it.getString("devicePrivate"),
                it.getString("nonce")
            )
        }
    }
}

class PartnerKeyVault(context: Context, private val profile: String) {
    private val file = AtomicFile(File(context.filesDir, "partner-$profile.keys"))
    private fun hasIdentity(): Boolean =
        file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
    private fun wrappingKey(): SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "ritela.partner.$profile"
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        if (file.baseFile.exists()) {
            throw java.security.KeyStoreException(
                "Identity wrapping key unavailable"
            )
        }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(
                        KeyProperties.BLOCK_MODE_GCM
                    ).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build()
            )
            generateKey()
        }
    }

    @Synchronized fun loadOrCreate(): LocalPartnerKeys {
        val key = wrappingKey()
        if (!hasIdentity()) return LocalPartnerKeys.create().also { save(it) }
        val data = file.readFully()
        return LocalPartnerKeys.decode(
            PartnerCrypto.crypt(
                Cipher.DECRYPT_MODE,
                key,
                data.copyOfRange(0, 12),
                profile,
                data.copyOfRange(12, data.size)
            ).toString(Charsets.UTF_8)
        )
    }

    @Synchronized fun save(keys: LocalPartnerKeys) {
        val iv = PartnerCrypto.random(12)
        val plain = keys.encode().toByteArray()
        val encrypted = try {
            iv +
                PartnerCrypto.crypt(Cipher.ENCRYPT_MODE, wrappingKey(), iv, profile, plain)
        } finally {
            plain.fill(0)
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
    }
    companion object {
        fun delete(context: Context, profile: String) {
            require(UUID.fromString(profile).toString() == profile)
            val identity = File(context.filesDir, "partner-$profile.keys")
            if (!identity.exists() && !File(identity.path + ".bak").exists()) return
            val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.deleteEntry("ritela.partner.$profile")
            AtomicFile(File(context.filesDir, "partner-$profile.keys")).delete()
        }
    }
}
