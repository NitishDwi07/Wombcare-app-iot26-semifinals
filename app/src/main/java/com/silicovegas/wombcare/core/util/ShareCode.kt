package com.silicovegas.wombcare.core.util

/**
 * The patient's shareable identity, e.g. `WMB-4K7X2QF9`.
 *
 * Crockford base32 alphabet — the digits/letters chosen so nothing is ambiguous when a
 * mother reads a code aloud or a doctor retypes one off WhatsApp:
 *   - no `I`, `L` (look like 1), no `O` (looks like 0), no `U` (looks like V, and avoids
 *     accidental words).
 * 8 payload chars → 32^8 ≈ 1.1 × 10^12 codes, so guessing one is hopeless even before the
 * database rule that only a signed-in doctor may resolve a code at all.
 */
object ShareCode {

    const val PREFIX = "WMB-"
    private const val LENGTH = 8
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ" // Crockford, 32 chars

    /** Characters a human is likely to substitute, normalised before lookup. */
    private val NORMALISE = mapOf(
        'I' to '1', 'L' to '1',
        'O' to '0',
        'U' to 'V',
    )

    private val VALID = Regex("^${PREFIX}[${ALPHABET}]{$LENGTH}$")

    /**
     * Generate a candidate code. [random] is injected so the caller controls the source —
     * production passes a [java.security.SecureRandom]; tests pass a seeded one for
     * determinism. A candidate is not guaranteed unique; the caller claims it in a
     * transaction and retries on collision.
     */
    fun generate(random: java.util.Random): String {
        val sb = StringBuilder(PREFIX)
        repeat(LENGTH) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)])
        }
        return sb.toString()
    }

    /**
     * Canonicalise user input: trim, uppercase, add the prefix if the user omitted it, and
     * fold look-alike characters to their intended value. Returns null if the result isn't
     * a structurally valid code — the caller shows "check the code" without a database round
     * trip.
     */
    fun normalise(input: String): String? {
        var s = input.trim().uppercase().replace(" ", "").replace("-", "")
        // Drop the prefix if the user included it, so we don't end up with WMB-WMB...
        if (s.startsWith("WMB")) s = s.substring(3)
        s = buildString { s.forEach { append(NORMALISE[it] ?: it) } }
        val candidate = PREFIX + s
        return if (VALID.matches(candidate)) candidate else null
    }

    fun isValid(code: String): Boolean = VALID.matches(code)
}
