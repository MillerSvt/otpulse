package com.otpulse.totp

import okio.ByteString.Companion.toByteString

enum class TotpAlgorithm { SHA1, SHA256, SHA512 }

sealed interface TotpResult {
    data class Success(val code: String) : TotpResult
    data class Failure(val error: TotpError) : TotpResult
}

enum class TotpError { INVALID_SECRET, INVALID_DIGITS, INVALID_PERIOD, INVALID_TIMESTAMP }

data class TotpPair(
    val counter: Long,
    val current: String,
    val next: String,
    val remainingMilliseconds: Long,
    val progress: Float,
)

object TotpEngine {
    fun generate(
        secret: ByteArray,
        timestampMilliseconds: Long,
        algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
        digits: Int = 6,
        periodSeconds: Int = 30,
    ): TotpResult {
        if (secret.isEmpty()) return TotpResult.Failure(TotpError.INVALID_SECRET)
        if (digits !in setOf(6, 8)) return TotpResult.Failure(TotpError.INVALID_DIGITS)
        if (periodSeconds <= 0) return TotpResult.Failure(TotpError.INVALID_PERIOD)
        if (timestampMilliseconds < 0) return TotpResult.Failure(TotpError.INVALID_TIMESTAMP)
        val counter = (timestampMilliseconds / 1_000L) / periodSeconds
        return TotpResult.Success(generateForCounter(secret, counter, algorithm, digits))
    }

    fun pair(
        secret: ByteArray,
        timestampMilliseconds: Long,
        algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
        digits: Int = 6,
        periodSeconds: Int = 30,
    ): TotpPair {
        require(secret.isNotEmpty())
        require(digits == 6 || digits == 8)
        require(periodSeconds > 0)
        val periodMs = periodSeconds * 1_000L
        val counter = timestampMilliseconds / periodMs
        val elapsed = timestampMilliseconds % periodMs
        val remaining = periodMs - elapsed
        return TotpPair(
            counter = counter,
            current = generateForCounter(secret, counter, algorithm, digits),
            next = generateForCounter(secret, counter + 1, algorithm, digits),
            remainingMilliseconds = remaining,
            progress = remaining.toFloat() / periodMs,
        )
    }

    private fun generateForCounter(
        secret: ByteArray,
        counter: Long,
        algorithm: TotpAlgorithm,
        digits: Int,
    ): String {
        val movingFactor = ByteArray(8) { index ->
            ((counter ushr (56 - index * 8)) and 0xff).toByte()
        }.toByteString()
        val key = secret.toByteString()
        val hash = when (algorithm) {
            TotpAlgorithm.SHA1 -> movingFactor.hmacSha1(key)
            TotpAlgorithm.SHA256 -> movingFactor.hmacSha256(key)
            TotpAlgorithm.SHA512 -> movingFactor.hmacSha512(key)
        }
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val modulus = if (digits == 6) 1_000_000 else 100_000_000
        return (binary % modulus).toString().padStart(digits, '0')
    }
}
