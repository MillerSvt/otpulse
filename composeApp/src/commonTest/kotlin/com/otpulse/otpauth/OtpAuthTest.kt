package com.otpulse.otpauth

import com.otpulse.totp.TotpAlgorithm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OtpAuthTest {
    @Test
    fun parsesCompleteUri() {
        val result = OtpAuth.parse(
            "otpauth://totp/Acme%20Co:alice%40example.com" +
                "?secret=JBSWY3DPEHPK3PXP&issuer=Acme%20Co&algorithm=SHA256&digits=8&period=60"
        )
        assertEquals(
            OtpAuthAccount("Acme Co", "alice@example.com", "JBSWY3DPEHPK3PXP", TotpAlgorithm.SHA256, 8, 60),
            assertIs<OtpAuthResult.Success>(result).account,
        )
    }

    @Test
    fun supportsAccountOnlyUnicodeAndDefaults() {
        val accountOnly = assertIs<OtpAuthResult.Success>(
            OtpAuth.parse("otpauth://totp/alice%40example.com?secret=JBSWY3DPEHPK3PXP")
        ).account
        assertEquals("", accountOnly.issuer)
        assertEquals("alice@example.com", accountOnly.accountName)
        assertEquals(TotpAlgorithm.SHA1, accountOnly.algorithm)
        assertEquals(6, accountOnly.digits)
        assertEquals(30, accountOnly.periodSeconds)

        val unicode = assertIs<OtpAuthResult.Success>(
            OtpAuth.parse("otpauth://totp/%D0%91%D0%B0%D0%BD%D0%BA:%D0%90%D0%BB%D0%B8%D1%81%D0%B0?secret=JBSWY3DPEHPK3PXP")
        ).account
        assertEquals("Банк", unicode.issuer)
        assertEquals("Алиса", unicode.accountName)
    }

    @Test
    fun validatesUriAndParameters() {
        assertFailure("https://totp/Test?secret=JBSWY3DPEHPK3PXP", OtpAuthError.UNSUPPORTED_SCHEME)
        assertFailure("otpauth://hotp/Test?secret=JBSWY3DPEHPK3PXP", OtpAuthError.HOTP_NOT_SUPPORTED)
        assertFailure("otpauth://totp/Test", OtpAuthError.MISSING_SECRET)
        assertFailure("otpauth://totp/Test?secret=bad!", OtpAuthError.INVALID_SECRET)
        assertFailure("otpauth://totp/Test?secret=JBSWY3DPEHPK3PXP&algorithm=MD5", OtpAuthError.UNSUPPORTED_ALGORITHM)
        assertFailure("otpauth://totp/Test?secret=JBSWY3DPEHPK3PXP&digits=7", OtpAuthError.INVALID_DIGITS)
        assertFailure("otpauth://totp/Test?secret=JBSWY3DPEHPK3PXP&period=0", OtpAuthError.INVALID_PERIOD)
        assertFailure("otpauth://totp/Test%ZZ?secret=JBSWY3DPEHPK3PXP", OtpAuthError.MALFORMED_URI)
    }

    @Test
    fun serializationRoundTripPreservesAccount() {
        val original = OtpAuthAccount(
            issuer = "Example / Банк",
            accountName = "alice+phone@example.com",
            secret = "jbsw y3dp-ehpk3pxp",
            algorithm = TotpAlgorithm.SHA512,
            digits = 8,
            periodSeconds = 45,
        )
        val restored = assertIs<OtpAuthResult.Success>(OtpAuth.parse(OtpAuth.serialize(original))).account
        assertEquals(original.copy(secret = "JBSWY3DPEHPK3PXP"), restored)
    }

    private fun assertFailure(uri: String, error: OtpAuthError) {
        assertEquals(error, assertIs<OtpAuthResult.Failure>(OtpAuth.parse(uri)).error)
    }
}
