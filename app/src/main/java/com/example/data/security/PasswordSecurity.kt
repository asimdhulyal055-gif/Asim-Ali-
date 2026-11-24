package com.example.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Cryptographic password hashing and username normalization helper.
 * Never stores, logs, or returns plain-text passwords.
 */
object PasswordSecurity {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val INTERNAL_STAFF_EMAIL_DOMAIN = "staff.aacrave.erp"

    private val USERNAME_REGEX = Regex("^[a-zA-Z0-9._-]{3,32}$")
    private val secureRandom = SecureRandom()

    data class PasswordHashResult(
        val hashBase64: String,
        val saltBase64: String
    )

    fun normalizeUsername(raw: String): String = raw.trim()

    fun validateUsername(raw: String): String? {
        val clean = normalizeUsername(raw)
        if (!USERNAME_REGEX.matches(clean)) {
            return "Username must be 3–32 characters using letters, numbers, '.', '_' or '-'."
        }
        return null
    }

    fun deriveAuthEmail(username: String, explicitEmail: String?): String {
        val trimmedEmail = explicitEmail?.trim().orEmpty()
        if (trimmedEmail.isNotBlank()) {
            return trimmedEmail.lowercase(Locale.US)
        }
        val cleanUser = normalizeUsername(username).lowercase(Locale.US)
        return "$cleanUser@$INTERNAL_STAFF_EMAIL_DOMAIN"
    }

    fun isInternalDerivedEmail(email: String): Boolean =
        email.trim().lowercase(Locale.US).endsWith("@$INTERNAL_STAFF_EMAIL_DOMAIN")

    fun validatePassword(password: String): String? {
        if (password.isBlank()) {
            return "Login password is required."
        }
        if (password.length < 6) {
            return "Login password must be at least 6 characters."
        }
        if (password.any { it.isISOControl() }) {
            return "Login password contains invalid control characters."
        }
        return null
    }

    fun hashPassword(password: String): PasswordHashResult {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val derived = pbkdf2OrFallback(password.toCharArray(), salt)
        val encoder = Base64.getEncoder()
        return PasswordHashResult(
            hashBase64 = encoder.encodeToString(derived),
            saltBase64 = encoder.encodeToString(salt)
        )
    }

    fun verifyPassword(password: String, saltBase64: String, expectedHashBase64: String): Boolean {
        if (password.isBlank() || saltBase64.isBlank() || expectedHashBase64.isBlank()) {
            return false
        }
        return try {
            val decoder = Base64.getDecoder()
            val salt = decoder.decode(saltBase64)
            val expected = decoder.decode(expectedHashBase64)
            val actual = pbkdf2OrFallback(password.toCharArray(), salt)
            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        }
    }

    private fun pbkdf2OrFallback(chars: CharArray, salt: ByteArray): ByteArray {
        return try {
            val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        } catch (_: Exception) {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            md.update(String(chars).toByteArray(Charsets.UTF_8))
            md.digest()
        } finally {
            chars.fill('\u0000')
        }
    }
}
