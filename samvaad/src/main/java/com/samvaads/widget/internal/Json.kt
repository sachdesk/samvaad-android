package com.samvaads.widget.internal

/**
 * A tiny JSON encoder and parser for bridge messages. Plain Kotlin on purpose: the
 * bridge logic is unit-tested on the JVM, where Android's org.json is only a stub.
 *
 * Values are Kotlin types: null, Boolean, Number (parsed as Long or Double), String,
 * List<Any?> and Map<String, Any?>.
 */
internal object Json {

    /** Encode [value] as JSON. U+2028 / U+2029 are escaped so the output is also a valid JS literal. */
    fun encode(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    /** Parse one JSON document. Throws [JsonException] on anything malformed. */
    fun parse(text: String): Any? {
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.readValue(0)
        parser.skipWhitespace()
        if (!parser.atEnd()) throw JsonException("Unexpected trailing characters at ${parser.pos}")
        return value
    }

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> out.append(value.toString())
            is Double -> writeDouble(out, value)
            is Float -> writeDouble(out, value.toDouble())
            is Number -> writeDouble(out, value.toDouble())
            is String -> writeString(out, value)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) out.append(',')
                    first = false
                    writeString(out, k.toString())
                    out.append(':')
                    write(out, v)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (v in value) {
                    if (!first) out.append(',')
                    first = false
                    write(out, v)
                }
                out.append(']')
            }
            is Array<*> -> write(out, value.asList())
            else -> writeString(out, value.toString())
        }
    }

    private fun writeDouble(out: StringBuilder, d: Double) {
        if (d.isNaN() || d.isInfinite()) {
            out.append("null")
        } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            out.append(d.toLong().toString())
        } else {
            out.append(d.toString())
        }
    }

    private fun writeString(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                ' ' -> out.append("\\u2028")
                ' ' -> out.append("\\u2029")
                // "</script>" can never close anything here, but keep the output inert anyway.
                '<' -> out.append("\\u003c")
                else -> if (c < ' ') {
                    out.append("\\u").append(String.format("%04x", c.code))
                } else {
                    out.append(c)
                }
            }
        }
        out.append('"')
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWhitespace() {
            while (pos < s.length && (s[pos] == ' ' || s[pos] == '\n' || s[pos] == '\r' || s[pos] == '\t')) pos++
        }

        fun readValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw JsonException("Too deeply nested")
            if (atEnd()) throw JsonException("Unexpected end of input")
            return when (val c = s[pos]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (c == '-' || c in '0'..'9') readNumber() else throw JsonException("Unexpected '$c' at $pos")
            }
        }

        private fun readLiteral(word: String, value: Any?): Any? {
            if (!s.startsWith(word, pos)) throw JsonException("Unexpected token at $pos")
            pos += word.length
            return value
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw JsonException("Expected a key at $pos")
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                map[key] = readValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw JsonException("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            val list = ArrayList<Any?>()
            pos++ // [
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                skipWhitespace()
                list.add(readValue(depth + 1))
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw JsonException("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("Unterminated string")
                val c = s[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd()) throw JsonException("Unterminated escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw JsonException("Bad unicode escape")
                                val hex = s.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: throw JsonException("Bad unicode escape")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw JsonException("Bad escape '\\$e'")
                        }
                    }
                    c < ' ' -> throw JsonException("Control character in string")
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Number {
            val start = pos
            if (peek() == '-') pos++
            while (!atEnd() && s[pos] in '0'..'9') pos++
            var isDouble = false
            if (!atEnd() && s[pos] == '.') {
                isDouble = true
                pos++
                while (!atEnd() && s[pos] in '0'..'9') pos++
            }
            if (!atEnd() && (s[pos] == 'e' || s[pos] == 'E')) {
                isDouble = true
                pos++
                if (!atEnd() && (s[pos] == '+' || s[pos] == '-')) pos++
                while (!atEnd() && s[pos] in '0'..'9') pos++
            }
            val text = s.substring(start, pos)
            if (text == "-" || text.isEmpty()) throw JsonException("Bad number at $start")
            return if (isDouble) {
                text.toDoubleOrNull() ?: throw JsonException("Bad number at $start")
            } else {
                text.toLongOrNull() ?: text.toDoubleOrNull() ?: throw JsonException("Bad number at $start")
            }
        }

        private fun peek(): Char = if (atEnd()) throw JsonException("Unexpected end of input") else s[pos]

        private fun expect(c: Char) {
            if (peek() != c) throw JsonException("Expected '$c' at $pos")
            pos++
        }
    }

    private const val MAX_DEPTH = 32
}

internal class JsonException(message: String) : IllegalArgumentException(message)
