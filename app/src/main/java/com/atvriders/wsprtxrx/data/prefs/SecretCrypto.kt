package com.atvriders.wsprtxrx.data.prefs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts/decrypts a secret (the QRZ password) at rest using an AES/GCM key held in the
 * AndroidKeystore, so the plaintext never lands in DataStore (which can otherwise be
 * swept into backups / device transfer).
 *
 * The ciphertext is stored as base64( iv (12 bytes) || ciphertext+tag ).
 *
 * Every operation is wrapped so a failure NEVER crashes the app: [encrypt] returns null
 * (caller then declines to persist the secret) and [decrypt] returns null (caller then
 * treats the stored value as empty). This is deliberately fail-safe — losing the ability
 * to persist a password is acceptable; crashing or leaking plaintext is not.
 */
interface SecretCrypto {
    /** Returns base64(iv||ciphertext) for [plaintext], or null on any failure. */
    fun encrypt(plaintext: String): String?

    /** Returns the plaintext for a base64(iv||ciphertext) [stored] value, or null on any failure. */
    fun decrypt(stored: String): String?

    companion object {
        /** No-op implementation used in unit tests / non-Android environments. */
        val NONE: SecretCrypto = object : SecretCrypto {
            override fun encrypt(plaintext: String): String? = null
            override fun decrypt(stored: String): String? = null
        }
    }
}

/** AndroidKeystore-backed AES/GCM implementation. */
class KeystoreSecretCrypto(
    private val alias: String = DEFAULT_ALIAS,
) : SecretCrypto {

    override fun encrypt(plaintext: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ct, 0, combined, iv.size, ct.size)
        Base64.encodeToString(combined, Base64.NO_WRAP)
    }.getOrNull()

    override fun decrypt(stored: String): String? = runCatching {
        val combined = Base64.decode(stored, Base64.NO_WRAP)
        if (combined.size <= IV_LEN) return@runCatching null
        val iv = combined.copyOfRange(0, IV_LEN)
        val ct = combined.copyOfRange(IV_LEN, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "wspr_qrz_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LEN = 12
        private const val GCM_TAG_BITS = 128
    }
}
