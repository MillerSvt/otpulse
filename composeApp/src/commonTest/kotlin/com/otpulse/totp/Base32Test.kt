package com.otpulse.totp

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class Base32Test {
    @Test
    fun decodesNormalizedSecret() {
        val result = Base32.decode("JBSW Y3DP-EHPK3PXP")
        assertContentEquals(
            byteArrayOf(0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x21, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte()),
            assertIs<Base32Result.Success>(result).bytes,
        )
    }

    @Test
    fun rejectsEmptyAndInvalidInput() {
        assertEquals(Base32Error.EMPTY, assertIs<Base32Result.Failure>(Base32.decode("  ")).error)
        assertEquals(Base32Error.INVALID_CHARACTER, assertIs<Base32Result.Failure>(Base32.decode("ABC!" )).error)
    }
}
