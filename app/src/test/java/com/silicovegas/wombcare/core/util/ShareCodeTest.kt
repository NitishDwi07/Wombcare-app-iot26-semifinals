package com.silicovegas.wombcare.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ShareCodeTest {

    @Test
    fun `generated code matches the WMB format`() {
        val code = ShareCode.generate(Random(42))
        assertTrue("'$code' should be valid", ShareCode.isValid(code))
        assertTrue(code.startsWith("WMB-"))
        assertEquals(12, code.length) // "WMB-" + 8
    }

    @Test
    fun `generation is deterministic for a given seed`() {
        assertEquals(ShareCode.generate(Random(7)), ShareCode.generate(Random(7)))
    }

    @Test
    fun `generated codes never contain ambiguous characters`() {
        val payloads = (0 until 500).map { ShareCode.generate(Random(it.toLong())).removePrefix("WMB-") }
        payloads.forEach { p ->
            assertFalse("'$p' must not contain I/L/O/U", p.any { it in "ILOU" })
        }
    }

    @Test
    fun `normalise folds look-alike characters a human would mistype`() {
        // A doctor types lowercase, with O-for-0 and I-for-1 and a stray space.
        assertEquals("WMB-40817VF9", ShareCode.normalise("wmb-4o8i7uf9"))
    }

    @Test
    fun `normalise accepts a code without the prefix`() {
        assertEquals("WMB-4K7X2QF9", ShareCode.normalise("4K7X2QF9"))
    }

    @Test
    fun `normalise rejects wrong-length input`() {
        assertNull(ShareCode.normalise("WMB-123"))
        assertNull(ShareCode.normalise("4K7X2QF9EXTRA"))
    }

    @Test
    fun `normalise round-trips a freshly generated code`() {
        repeat(50) { seed ->
            val code = ShareCode.generate(Random(seed.toLong()))
            assertEquals(code, ShareCode.normalise(code.lowercase()))
        }
    }
}
