package com.matedroid.domain

/**
 * User-defined HTTP headers sent with every TeslamateAPI request
 * (Settings → Connection → "Custom HTTP Headers").
 *
 * OkHttp rejects a header name or value containing a character HTTP does not allow by
 * throwing IllegalArgumentException from inside the request interceptor. On an enqueued
 * call that exception escapes on OkHttp's dispatcher thread and kills the app, on every
 * request, so a single stray space in a pasted header name would make the app unusable.
 * Headers are therefore checked here before they are saved, and filtered again before
 * they reach OkHttp in case something invalid is already on disk.
 *
 * Values follow OkHttp's own check. Names are stricter than OkHttp, which accepts any
 * visible ASCII: they must be an RFC 9110 token, so a pasted `X-API-Key:` is caught here
 * instead of going out as a malformed header that HTTP/2 servers reject outright.
 */
object CustomHeaders {
    /** RFC 9110 `tchar`: the symbols allowed in a header name besides ASCII letters and digits. */
    private const val TOKEN_SYMBOLS = "!#$%&'*+-.^_`|~"

    /**
     * A header name is an RFC 9110 token: one or more ASCII letters, digits or [TOKEN_SYMBOLS].
     * That rules out spaces, control characters and separators such as `:` `/` `(` `)`.
     */
    fun isValidName(name: String): Boolean =
        name.isNotEmpty() && name.all {
            it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in TOKEN_SYMBOLS
        }

    /** A header value is visible ASCII plus space and tab; it may be empty. */
    fun isValidValue(value: String): Boolean =
        value.all { it == '\t' || it in ' '..'~' }

    /**
     * Normalises the rows typed in Settings into what gets stored: surrounding whitespace is
     * trimmed, as it is never meaningful in a header and is the easiest mistake to make when
     * pasting, and rows with a blank name are dropped as unfinished.
     */
    fun normalize(rows: List<Pair<String, String>>): List<Pair<String, String>> =
        rows.map { (name, value) -> name.trim() to value.trim() }
            .filter { (name, _) -> name.isNotEmpty() }

    /** The first row, after [normalize], that OkHttp would reject, or null if all are valid. */
    fun firstInvalid(rows: List<Pair<String, String>>): Pair<String, String>? =
        normalize(rows).firstOrNull { (name, value) -> !isValidName(name) || !isValidValue(value) }

    /**
     * The headers that are safe to hand to OkHttp. Entries are trimmed first, since headers
     * saved before validation existed may still carry stray whitespace; anything still
     * invalid is skipped.
     */
    fun sanitize(headers: Map<String, String>): Map<String, String> =
        normalize(headers.toList())
            .filter { (name, value) -> isValidName(name) && isValidValue(value) }
            .toMap()
}
