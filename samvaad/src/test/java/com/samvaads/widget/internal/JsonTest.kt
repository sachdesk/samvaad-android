package com.samvaads.widget.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    @Test
    fun encodesPrimitivesAndCollections() {
        assertEquals("null", Json.encode(null))
        assertEquals("true", Json.encode(true))
        assertEquals("42", Json.encode(42))
        assertEquals("1.5", Json.encode(1.5))
        assertEquals("3", Json.encode(3.0))
        assertEquals("null", Json.encode(Double.NaN))
        assertEquals(
            """{"a":[1,"x",false],"b":{}}""",
            Json.encode(linkedMapOf("a" to listOf(1, "x", false), "b" to emptyMap<String, Any>())),
        )
    }

    @Test
    fun escapesStringsForJson() {
        assertEquals("\"a\\\"b\\\\c\"", Json.encode("a\"b\\c"))
        assertEquals("\"line\\nnext\\ttab\\r\"", Json.encode("line\nnext\ttab\r"))
        assertEquals("\"\\u0001\"", Json.encode("\u0001"))
    }

    @Test
    fun escapesLineAndParagraphSeparatorsForJs() {
        val out = Json.encode("a\u2028b\u2029c")
        assertEquals("\"a\\u2028b\\u2029c\"", out)
        assertFalse(out.contains('\u2028'))
        assertFalse(out.contains('\u2029'))
    }

    @Test
    fun escapesAngleBracketSoScriptTagsStayInert() {
        assertEquals("\"\\u003c/script>\"", Json.encode("</script>"))
    }

    @Test
    fun keepsNonAsciiAsIs() {
        assertEquals("\"\u0928\u092e\u0938\u094d\u0924\u0947 \uD83D\uDE00\"", Json.encode("\u0928\u092e\u0938\u094d\u0924\u0947 \uD83D\uDE00"))
    }

    @Test
    fun roundTrips() {
        val value = linkedMapOf(
            "type" to "message",
            "text" to "Order YB-1042 \"late\"\n\u2028ok",
            "n" to 7L,
            "list" to listOf(true, null, 2.5),
        )
        assertEquals(value, Json.parse(Json.encode(value)))
    }

    @Test
    fun parsesNumbersAndEscapes() {
        assertEquals(-12L, Json.parse("-12"))
        assertEquals(1.0e3, Json.parse("1e3"))
        assertEquals(0.25, Json.parse("0.25"))
        assertEquals("\u00e9/\u2028", Json.parse("\"\\u00e9\\/\\u2028\""))
        assertEquals(mapOf("a" to listOf<Any?>()), Json.parse(" { \"a\" : [ ] } "))
    }

    @Test
    fun rejectsMalformedInput() {
        val bad = listOf("", "{", "{\"a\":}", "[1,]", "tru", "\"abc", "{\"a\":1} x", "-", "{a:1}", "\"\\x\"")
        for (b in bad) {
            assertTrue("should reject: $b", runCatching { Json.parse(b) }.exceptionOrNull() is JsonException)
        }
    }

    @Test
    fun rejectsDeepNesting() {
        val deep = "[".repeat(100) + "]".repeat(100)
        assertTrue(runCatching { Json.parse(deep) }.exceptionOrNull() is JsonException)
        assertNull(Json.parse("null"))
    }
}
