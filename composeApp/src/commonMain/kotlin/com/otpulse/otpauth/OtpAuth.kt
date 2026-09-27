package com.otpulse.otpauth

import com.otpulse.totp.Base32
import com.otpulse.totp.Base32Result
import com.otpulse.totp.TotpAlgorithm

data class OtpAuthAccount(
    val issuer: String,
    val accountName: String,
    val secret: String,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = 6,
    val periodSeconds: Int = 30,
)

sealed interface OtpAuthResult {
    data class Success(val account: OtpAuthAccount) : OtpAuthResult
    data class Failure(val error: OtpAuthError) : OtpAuthResult
}

enum class OtpAuthError {
    MALFORMED_URI,
    UNSUPPORTED_SCHEME,
    HOTP_NOT_SUPPORTED,
    MISSING_LABEL,
    MISSING_SECRET,
    INVALID_SECRET,
    UNSUPPORTED_ALGORITHM,
    INVALID_DIGITS,
    INVALID_PERIOD,
}

object OtpAuth {
    fun parse(uri: String): OtpAuthResult {
        val schemeSeparator = uri.indexOf("://")
        if (schemeSeparator <= 0) return OtpAuthResult.Failure(OtpAuthError.MALFORMED_URI)
        if (!uri.substring(0, schemeSeparator).equals("otpauth", ignoreCase = true)) {
            return OtpAuthResult.Failure(OtpAuthError.UNSUPPORTED_SCHEME)
        }

        val remainder = uri.substring(schemeSeparator + 3)
        val pathStart = remainder.indexOf('/')
        if (pathStart < 0) return OtpAuthResult.Failure(OtpAuthError.MALFORMED_URI)
        when (remainder.substring(0, pathStart).lowercase()) {
            "hotp" -> return OtpAuthResult.Failure(OtpAuthError.HOTP_NOT_SUPPORTED)
            "totp" -> Unit
            else -> return OtpAuthResult.Failure(OtpAuthError.MALFORMED_URI)
        }

        val pathAndQuery = remainder.substring(pathStart + 1)
        val queryStart = pathAndQuery.indexOf('?')
        val encodedLabel = if (queryStart >= 0) pathAndQuery.substring(0, queryStart) else pathAndQuery
        val query = if (queryStart >= 0) pathAndQuery.substring(queryStart + 1) else ""
        val label = percentDecode(encodedLabel) ?: return OtpAuthResult.Failure(OtpAuthError.MALFORMED_URI)
        if (label.isBlank()) return OtpAuthResult.Failure(OtpAuthError.MISSING_LABEL)
        val parameters = parseQuery(query) ?: return OtpAuthResult.Failure(OtpAuthError.MALFORMED_URI)

        val rawSecret = parameters["secret"] ?: return OtpAuthResult.Failure(OtpAuthError.MISSING_SECRET)
        val secret = Base32.normalize(rawSecret)
        if (Base32.decode(secret) !is Base32Result.Success) {
            return OtpAuthResult.Failure(OtpAuthError.INVALID_SECRET)
        }

        val algorithm = when (parameters["algorithm"]?.uppercase() ?: "SHA1") {
            "SHA1" -> TotpAlgorithm.SHA1
            "SHA256" -> TotpAlgorithm.SHA256
            "SHA512" -> TotpAlgorithm.SHA512
            else -> return OtpAuthResult.Failure(OtpAuthError.UNSUPPORTED_ALGORITHM)
        }
        val digits = parameters["digits"]?.toIntOrNull() ?: 6
        if (digits != 6 && digits != 8) return OtpAuthResult.Failure(OtpAuthError.INVALID_DIGITS)
        val period = parameters["period"]?.toIntOrNull() ?: 30
        if (period <= 0) return OtpAuthResult.Failure(OtpAuthError.INVALID_PERIOD)

        val separator = label.indexOf(':')
        val labelIssuer = if (separator >= 0) label.substring(0, separator).trim() else ""
        val accountName = if (separator >= 0) label.substring(separator + 1).trim() else label.trim()
        if (accountName.isEmpty()) return OtpAuthResult.Failure(OtpAuthError.MISSING_LABEL)

        return OtpAuthResult.Success(
            OtpAuthAccount(
                issuer = parameters["issuer"]?.trim().orEmpty().ifEmpty { labelIssuer },
                accountName = accountName,
                secret = secret,
                algorithm = algorithm,
                digits = digits,
                periodSeconds = period,
            )
        )
    }

    fun serialize(account: OtpAuthAccount): String {
        val label = if (account.issuer.isBlank()) account.accountName else "${account.issuer}:${account.accountName}"
        return buildString {
            append("otpauth://totp/")
            append(percentEncode(label))
            append("?secret=")
            append(percentEncode(Base32.normalize(account.secret)))
            if (account.issuer.isNotBlank()) {
                append("&issuer=")
                append(percentEncode(account.issuer))
            }
            append("&algorithm=")
            append(account.algorithm.name)
            append("&digits=")
            append(account.digits)
            append("&period=")
            append(account.periodSeconds)
        }
    }

    private fun parseQuery(query: String): Map<String, String>? {
        if (query.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (component in query.split('&')) {
            if (component.isEmpty()) continue
            val separator = component.indexOf('=')
            val key = percentDecode(if (separator >= 0) component.substring(0, separator) else component)
                ?: return null
            val value = percentDecode(if (separator >= 0) component.substring(separator + 1) else "")
                ?: return null
            result[key.lowercase()] = value
        }
        return result
    }

    private fun percentDecode(value: String): String? {
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            when (val character = value[index]) {
                '%' -> {
                    if (index + 2 >= value.length) return null
                    val high = value[index + 1].digitToIntOrNull(16) ?: return null
                    val low = value[index + 2].digitToIntOrNull(16) ?: return null
                    bytes += ((high shl 4) or low).toByte()
                    index += 3
                }
                '+' -> {
                    bytes += ' '.code.toByte()
                    index++
                }
                else -> {
                    bytes.addAll(character.toString().encodeToByteArray().toList())
                    index++
                }
            }
        }
        return try {
            bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
        } catch (_: Throwable) {
            null
        }
    }

    private fun percentEncode(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            val safe = (unsigned in 'a'.code..'z'.code) || (unsigned in 'A'.code..'Z'.code) ||
                (unsigned in '0'.code..'9'.code) || unsigned == '-'.code || unsigned == '.'.code ||
                unsigned == '_'.code || unsigned == '~'.code
            if (safe) append(unsigned.toChar()) else {
                append('%')
                append(HEX[unsigned ushr 4])
                append(HEX[unsigned and 0x0f])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
