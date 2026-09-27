package com.otpulse.totp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TotpEngineTest {
    private val timestamps = listOf(59L, 1_111_111_109L, 1_111_111_111L, 1_234_567_890L, 2_000_000_000L, 20_000_000_000L)

    @Test
    fun rfc6238AppendixB_sha1() {
        verifyRfc(
            "12345678901234567890".encodeToByteArray(),
            TotpAlgorithm.SHA1,
            listOf("94287082", "07081804", "14050471", "89005924", "69279037", "65353130"),
        )
    }

    @Test
    fun rfc6238AppendixB_sha256() {
        verifyRfc(
            "12345678901234567890123456789012".encodeToByteArray(),
            TotpAlgorithm.SHA256,
            listOf("46119246", "68084774", "67062674", "91819424", "90698825", "77737706"),
        )
    }

    @Test
    fun rfc6238AppendixB_sha512() {
        verifyRfc(
            "1234567890123456789012345678901234567890123456789012345678901234".encodeToByteArray(),
            TotpAlgorithm.SHA512,
            listOf("90693936", "25091201", "99943326", "93441116", "38618901", "47863826"),
        )
    }

    @Test
    fun periodBoundaryIsDerivedFromAbsoluteClock() {
        val secret = "boundary-test".encodeToByteArray()
        val before = TotpEngine.pair(secret, 29_999)
        val boundary = TotpEngine.pair(secret, 30_000)
        val after = TotpEngine.pair(secret, 30_001)
        assertEquals(before.next, boundary.current)
        assertEquals(boundary.current, after.current)
        assertEquals(1, boundary.counter)
        assertEquals(30_000, boundary.remainingMilliseconds)
    }

    @Test
    fun supportsSixDigitsAndLeadingZero() {
        val result = TotpEngine.generate(
            secret = "12345678901234567890".encodeToByteArray(),
            timestampMilliseconds = 1_111_111_109_000,
            digits = 8,
        )
        assertEquals("07081804", assertIs<TotpResult.Success>(result).code)
    }

    @Test
    fun validatesAllPublicInputs() {
        assertEquals(
            TotpError.INVALID_SECRET,
            assertIs<TotpResult.Failure>(TotpEngine.generate(byteArrayOf(), 0)).error,
        )
        assertEquals(
            TotpError.INVALID_DIGITS,
            assertIs<TotpResult.Failure>(TotpEngine.generate(byteArrayOf(1), 0, digits = 7)).error,
        )
        assertEquals(
            TotpError.INVALID_PERIOD,
            assertIs<TotpResult.Failure>(TotpEngine.generate(byteArrayOf(1), 0, periodSeconds = 0)).error,
        )
        assertEquals(
            TotpError.INVALID_TIMESTAMP,
            assertIs<TotpResult.Failure>(TotpEngine.generate(byteArrayOf(1), -1)).error,
        )
    }

    @Test
    fun supportsDifferentPeriodsAndLargeTimestamps() {
        val secret = "period-test-secret".encodeToByteArray()
        val before = TotpEngine.pair(secret, 59_999, periodSeconds = 60)
        val boundary = TotpEngine.pair(secret, 60_000, periodSeconds = 60)
        assertEquals(before.next, boundary.current)
        assertIs<TotpResult.Success>(
            TotpEngine.generate(secret, 20_000_000_000L * 1_000L, periodSeconds = 45)
        )
    }

    private fun verifyRfc(secret: ByteArray, algorithm: TotpAlgorithm, expected: List<String>) {
        timestamps.zip(expected).forEach { (timestamp, code) ->
            val result = TotpEngine.generate(secret, timestamp * 1_000, algorithm, digits = 8)
            assertEquals(code, assertIs<TotpResult.Success>(result).code)
        }
    }
}
