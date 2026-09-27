package com.otpulse.bluetooth

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeyboardReportEncoderTest {
    @Test
    fun encodes123456AndMandatoryEnter() {
        val reports = KeyboardReportEncoder.digitsWithEnter("123456")

        assertEquals(listOf("1", "2", "3", "4", "5", "6", "Enter"), reports.map { it.label })
        assertEquals(listOf(0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x28), reports.map { it.keyDown[2].toInt() })
        reports.forEach { report ->
            assertEquals(8, report.keyDown.size)
            assertContentEquals(ByteArray(8), report.keyUp)
        }
    }

    @Test
    fun zeroUsesKeyboardUsage27() {
        assertEquals(0x27, KeyboardReportEncoder.digitsWithEnter("0").first().keyDown[2].toInt())
    }

    @Test
    fun rejectsEmptyAndNonDigitInput() {
        assertFailsWith<IllegalArgumentException> { KeyboardReportEncoder.digitsWithEnter("") }
        assertFailsWith<IllegalArgumentException> { KeyboardReportEncoder.digitsWithEnter("12A") }
    }

    @Test
    fun encodesMacKeyboardIdentificationKeys() {
        assertEquals(0x1D, KeyboardReportEncoder.singleUsage("Z", 0x1D).keyDown[2].toInt())
        assertEquals(0x38, KeyboardReportEncoder.singleUsage("/", 0x38).keyDown[2].toInt())
    }
}
