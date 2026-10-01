package com.matedroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomHeadersTest {

    @Test
    fun `typical gateway header names are valid`() {
        assertTrue(CustomHeaders.isValidName("X-API-Key"))
        assertTrue(CustomHeaders.isValidName("CF-Access-Client-Id"))
    }

    @Test
    fun `names with spaces, control or non-ASCII characters are rejected`() {
        assertFalse(CustomHeaders.isValidName(""))
        assertFalse(CustomHeaders.isValidName("X Api Key"))
        assertFalse(CustomHeaders.isValidName("X-Api-Key\n"))
        assertFalse(CustomHeaders.isValidName("X-Contraseña"))
    }

    @Test
    fun `values allow spaces and tabs but not newlines or non-ASCII`() {
        assertTrue(CustomHeaders.isValidValue(""))
        assertTrue(CustomHeaders.isValidValue("Bearer abc.def"))
        assertTrue(CustomHeaders.isValidValue("a\tb"))
        assertFalse(CustomHeaders.isValidValue("abc\r\nX-Injected: 1"))
        assertFalse(CustomHeaders.isValidValue("contraseña"))
    }

    @Test
    fun `normalize trims whitespace and drops rows without a name`() {
        val rows = listOf(
            " X-API-Key " to " secret ",
            "" to "orphan value",
            "   " to "blank name"
        )

        assertEquals(listOf("X-API-Key" to "secret"), CustomHeaders.normalize(rows))
    }

    @Test
    fun `a stray space around a pasted name is not reported as invalid`() {
        assertNull(CustomHeaders.firstInvalid(listOf("X-API-Key " to "secret")))
    }

    @Test
    fun `firstInvalid reports the first offending row`() {
        val rows = listOf(
            "X-Good" to "ok",
            "X Bad" to "ok",
            "X-Also-Bad" to "ñ"
        )

        assertEquals("X Bad" to "ok", CustomHeaders.firstInvalid(rows))
    }

    @Test
    fun `sanitize keeps valid headers and drops the rest`() {
        val stored = mapOf(
            "X-API-Key" to "secret",
            " X-Trimmed " to " value ",
            "X Bad" to "value",
            "X-Bad-Value" to "contraseña"
        )

        assertEquals(
            mapOf("X-API-Key" to "secret", "X-Trimmed" to "value"),
            CustomHeaders.sanitize(stored)
        )
    }
}
