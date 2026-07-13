package com.endgamefinance.data.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception("Wrong password or corrupted backup file")

/**
 * Backup file crypto per docs/security.md:
 * magic "EGF1" + format version + salt(16) + iv(12) + AES-256-GCM(gzip(json)).
 * PBKDF2-HMAC-SHA256, 210k iterations. Platform crypto only — no dependencies.
 */
object BackupCrypto {

    private val MAGIC = byteArrayOf(0x45, 0x47, 0x46, 0x31) // "EGF1"
    private const val FORMAT_VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 210_000
    private const val KEY_BITS = 256

    fun encryptTo(output: OutputStream, plaintext: ByteArray, password: CharArray) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val compressed = ByteArrayOutputStream().use { buffer ->
            GZIPOutputStream(buffer).use { it.write(plaintext) }
            buffer.toByteArray()
        }
        output.write(MAGIC)
        output.write(FORMAT_VERSION.toInt())
        output.write(salt)
        output.write(cipher.iv)
        output.write(cipher.doFinal(compressed))
        output.flush()
    }

    /** @throws WrongPasswordException on bad password (GCM tag failure). */
    fun decryptFrom(input: InputStream, password: CharArray): ByteArray {
        val header = ByteArray(MAGIC.size + 1 + SALT_BYTES + IV_BYTES)
        var read = 0
        while (read < header.size) {
            val n = input.read(header, read, header.size - read)
            require(n >= 0) { "Not an Endgame Finance backup (file too short)" }
            read += n
        }
        require(header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Not an Endgame Finance backup file"
        }
        require(header[MAGIC.size] == FORMAT_VERSION) {
            "Backup was made by a newer app version"
        }
        val salt = header.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + SALT_BYTES)
        val iv = header.copyOfRange(MAGIC.size + 1 + SALT_BYTES, header.size)
        val ciphertext = input.readBytes()

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val compressed = try {
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
        return GZIPInputStream(compressed.inputStream()).use { it.readBytes() }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
