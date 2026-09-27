package com.otpulse.totp

sealed interface Base32Result {
    data class Success(val bytes: ByteArray) : Base32Result
    data class Failure(val error: Base32Error) : Base32Result
}

enum class Base32Error { EMPTY, INVALID_CHARACTER, INVALID_PADDING }

object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun normalize(value: String): String = value
        .filterNot { it.isWhitespace() || it == '-' }
        .uppercase()

    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        var buffer = 0
        var bits = 0
        return buildString((bytes.size * 8 + 4) / 5) {
            bytes.forEach { byte ->
                buffer = (buffer shl 8) or (byte.toInt() and 0xff)
                bits += 8
                while (bits >= 5) {
                    bits -= 5
                    append(ALPHABET[(buffer shr bits) and 0x1f])
                }
            }
            if (bits > 0) append(ALPHABET[(buffer shl (5 - bits)) and 0x1f])
        }
    }

    fun decode(value: String): Base32Result {
        val normalized = normalize(value)
        if (normalized.isEmpty()) return Base32Result.Failure(Base32Error.EMPTY)
        val firstPadding = normalized.indexOf('=')
        val data = if (firstPadding >= 0) normalized.substring(0, firstPadding) else normalized
        if (firstPadding >= 0 && normalized.substring(firstPadding).any { it != '=' }) {
            return Base32Result.Failure(Base32Error.INVALID_PADDING)
        }

        var buffer = 0
        var bits = 0
        val output = ArrayList<Byte>((data.length * 5) / 8)
        for (character in data) {
            val valueIndex = ALPHABET.indexOf(character)
            if (valueIndex < 0) return Base32Result.Failure(Base32Error.INVALID_CHARACTER)
            buffer = (buffer shl 5) or valueIndex
            bits += 5
            if (bits >= 8) {
                bits -= 8
                output += ((buffer shr bits) and 0xff).toByte()
            }
        }
        if (bits > 0 && (buffer and ((1 shl bits) - 1)) != 0) {
            return Base32Result.Failure(Base32Error.INVALID_PADDING)
        }
        return Base32Result.Success(output.toByteArray())
    }
}
