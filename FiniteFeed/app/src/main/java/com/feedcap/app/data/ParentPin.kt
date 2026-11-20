package com.feedcap.app.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The parent PIN. Only a salted PBKDF2 hash is stored, never the PIN. Wrong guesses lock the
 * PIN pad for longer each time, so 10,000 combinations can't be tried by hand.
 */
object ParentPin {
    const val LENGTH = 4
    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256
    private const val FREE_TRIES = 5
    private const val FIRST_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 15 * 60_000L

    fun isValid(pin: String) = pin.length == LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.getEncoder().encodeToString(key)
    }

    fun matches(pin: String, salt: String, hash: String): Boolean =
        isValid(pin) && MessageDigest.isEqual(hash(pin, salt).toByteArray(), hash.toByteArray())

    // ---- Recovery ----

    /** Letters and digits that can't be mistaken for each other (no 0/O, 1/I/L). */
    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val CODE_LENGTH = 12

    /** Wait before a forgotten PIN is reset when there's no recovery code. */
    const val RESET_DELAY_MS = 72 * 60 * 60 * 1000L

    /** A new recovery code, shown as XXXX-XXXX-XXXX. */
    fun newRecoveryCode(): String {
        val r = SecureRandom()
        val raw = (1..CODE_LENGTH).map { CODE_ALPHABET[r.nextInt(CODE_ALPHABET.length)] }.joinToString("")
        return raw.chunked(4).joinToString("-")
    }

    /** What's compared: upper case, no dashes or spaces. Typing it in lower case still works. */
    fun normalizeCode(input: String): String = input.uppercase().filter { it in CODE_ALPHABET }

    fun codeMatches(input: String, salt: String, hash: String): Boolean {
        val code = normalizeCode(input)
        return code.length == CODE_LENGTH && MessageDigest.isEqual(hash(code, salt).toByteArray(), hash.toByteArray())
    }

    /** A reset started at [startedAt] is finished at [now]. */
    fun resetDue(startedAt: Long, now: Long) = startedAt > 0 && now >= startedAt + RESET_DELAY_MS

    fun resetRemainingMs(startedAt: Long, now: Long) = (startedAt + RESET_DELAY_MS - now).coerceAtLeast(0)

    /** How long the PIN pad stays locked after this many wrong guesses in a row. */
    fun lockoutMs(failures: Int): Long {
        if (failures < FREE_TRIES) return 0
        val doublings = (failures - FREE_TRIES).coerceAtMost(10)
        return (FIRST_LOCKOUT_MS shl doublings).coerceAtMost(MAX_LOCKOUT_MS)
    }
}
