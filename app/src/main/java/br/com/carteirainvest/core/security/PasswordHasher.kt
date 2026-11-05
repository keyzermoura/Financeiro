package br.com.carteirainvest.core.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PBKDF2 password hashing with a per-user 128-bit salt. The algorithm is encoded
 * in the stored hash so devices that only expose SHA-1 can still verify existing
 * credentials after an upgrade.
 */
@Singleton
class PasswordHasher @Inject constructor() {

    private val random = SecureRandom()

    fun generateSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(password: String, saltBase64: String): String {
        val algorithm = preferredAlgorithm()
        val iterations = ITERATIONS
        val hash = derive(password, Base64.decode(saltBase64, Base64.NO_WRAP), algorithm, iterations)
        return "$algorithm:$iterations:${Base64.encodeToString(hash, Base64.NO_WRAP)}"
    }

    fun verify(password: String, saltBase64: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 3) return false
        val algorithm = parts[0]
        val iterations = parts[1].toIntOrNull() ?: return false
        val expected = runCatching { Base64.decode(parts[2], Base64.NO_WRAP) }.getOrNull() ?: return false
        val actual = runCatching {
            derive(password, Base64.decode(saltBase64, Base64.NO_WRAP), algorithm, iterations)
        }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: String, salt: ByteArray, algorithm: String, iterations: Int): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun preferredAlgorithm(): String {
        return if (runCatching { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256") }.isSuccess) {
            "PBKDF2WithHmacSHA256"
        } else {
            // PBKDF2WithHmacSHA1 is available on API 24/25. The iteration count
            // still makes offline guessing expensive.
            "PBKDF2WithHmacSHA1"
        }
    }

    companion object {
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 120_000
        private const val KEY_BITS = 256
    }
}
