package com.otpulse.backup

internal sealed interface JsonValue {
    data class ObjectValue(val fields: Map<String, JsonValue>) : JsonValue
    data class ArrayValue(val values: List<JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class NumberValue(val value: String) : JsonValue
    data class BooleanValue(val value: Boolean) : JsonValue
    data object NullValue : JsonValue
}

internal class JsonBackupParser(private val source: String) {
    private var index = 0

    fun parseArray(): List<JsonValue.ObjectValue>? {
        return try {
            val root = parseValue() as? JsonValue.ArrayValue ?: return null
            skipWhitespace()
            if (index != source.length) return null
            root.values.map { it as? JsonValue.ObjectValue ?: return null }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        require(index < source.length)
        return when (source[index]) {
            '{' -> parseObject()
            '[' -> parseArrayValue()
            '"' -> JsonValue.StringValue(parseString())
            't' -> parseLiteral("true", JsonValue.BooleanValue(true))
            'f' -> parseLiteral("false", JsonValue.BooleanValue(false))
            'n' -> parseLiteral("null", JsonValue.NullValue)
            else -> parseNumber()
        }
    }

    private fun parseObject(): JsonValue.ObjectValue {
        require(source[index++] == '{')
        skipWhitespace()
        val fields = linkedMapOf<String, JsonValue>()
        if (consume('}')) return JsonValue.ObjectValue(fields)
        while (true) {
            skipWhitespace()
            require(index < source.length && source[index] == '"')
            val key = parseString()
            skipWhitespace()
            require(consume(':'))
            fields[key] = parseValue()
            skipWhitespace()
            if (consume('}')) break
            require(consume(','))
        }
        return JsonValue.ObjectValue(fields)
    }

    private fun parseArrayValue(): JsonValue.ArrayValue {
        require(source[index++] == '[')
        skipWhitespace()
        val values = mutableListOf<JsonValue>()
        if (consume(']')) return JsonValue.ArrayValue(values)
        while (true) {
            values += parseValue()
            skipWhitespace()
            if (consume(']')) break
            require(consume(','))
        }
        return JsonValue.ArrayValue(values)
    }

    private fun parseString(): String {
        require(source[index++] == '"')
        return buildString {
            while (index < source.length) {
                when (val character = source[index++]) {
                    '"' -> return@buildString
                    '\\' -> {
                        require(index < source.length)
                        append(
                            when (val escaped = source[index++]) {
                                '"', '\\', '/' -> escaped
                                'b' -> '\b'
                                'f' -> '\u000C'
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                'u' -> {
                                    require(index + 4 <= source.length)
                                    val code = source.substring(index, index + 4).toIntOrNull(16)
                                        ?: throw IllegalArgumentException("Invalid unicode escape")
                                    index += 4
                                    code.toChar()
                                }
                                else -> throw IllegalArgumentException("Invalid escape")
                            }
                        )
                    }
                    else -> {
                        require(character.code >= 0x20)
                        append(character)
                    }
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }
    }

    private fun parseNumber(): JsonValue.NumberValue {
        val start = index
        if (index < source.length && source[index] == '-') index++
        require(index < source.length && source[index].isDigit())
        if (source[index] == '0') index++ else while (index < source.length && source[index].isDigit()) index++
        if (index < source.length && source[index] == '.') {
            index++
            require(index < source.length && source[index].isDigit())
            while (index < source.length && source[index].isDigit()) index++
        }
        if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
            index++
            if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
            require(index < source.length && source[index].isDigit())
            while (index < source.length && source[index].isDigit()) index++
        }
        return JsonValue.NumberValue(source.substring(start, index))
    }

    private fun <T : JsonValue> parseLiteral(literal: String, value: T): T {
        require(source.startsWith(literal, index))
        index += literal.length
        return value
    }

    private fun consume(expected: Char): Boolean {
        if (index >= source.length || source[index] != expected) return false
        index++
        return true
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) index++
    }
}
