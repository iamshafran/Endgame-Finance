package com.endgamefinance.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the SQLCipher passphrase.
 *
 * Strategy (decided in Milestone 0, feeds Milestone 5's app lock):
 * a random 256-bit passphrase is generated once, encrypted with a
 * hardware-backed Android Keystore AES-GCM key, and the wrapped blob is stored
 * in [Context.getNoBackupFilesDir] so it can never leave the device via backup.
 * The plaintext passphrase exists only in memory while the DB is open.
 *
 * File format: [version:1][iv:12][ciphertext] — versioned so Milestone 5 can
 * layer PIN-mixing on top without a breaking migration.
 */
object DbKeyManager {

    private const val KEYSTORE_ALIAS = "endgame_db_master_key"
    private const val KEY_FILE_NAME = "db_key.bin"
    private const val FORMAT_VERSION: Byte = 1
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val PASSPHRASE_BYTES = 32

    fun getOrCreatePassphrase(context: Context): ByteArray {
        val keyFile = File(context.noBackupFilesDir, KEY_FILE_NAME)
        val keystoreKey = getOrCreateKeystoreKey()

        if (keyFile.exists()) {
            return unwrap(keyFile.readBytes(), keystoreKey)
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val wrapped = wrap(passphrase, keystoreKey)
        // Atomic write: never leave a torn key file behind
        val tmp = File(context.noBackupFilesDir, "$KEY_FILE_NAME.tmp")
        tmp.writeBytes(wrapped)
        if (!tmp.renameTo(keyFile)) {
            tmp.delete()
            error("Failed to persist database key file")
        }
        return passphrase
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun wrap(passphrase: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(passphrase)
        require(cipher.iv.size == GCM_IV_BYTES) { "Unexpected GCM IV length" }
        return byteArrayOf(FORMAT_VERSION) + cipher.iv + ciphertext
    }

    private fun unwrap(blob: ByteArray, key: SecretKey): ByteArray {
        require(blob.size > 1 + GCM_IV_BYTES) { "Corrupt database key file" }
        require(blob[0] == FORMAT_VERSION) { "Unknown key file version ${blob[0]}" }
        val iv = blob.copyOfRange(1, 1 + GCM_IV_BYTES)
        val ciphertext = blob.copyOfRange(1 + GCM_IV_BYTES, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
