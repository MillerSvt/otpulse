package com.otpulse.bluetooth

data class KeyboardReport(
    val label: String,
    val keyDown: ByteArray,
    val keyUp: ByteArray = ByteArray(KEYBOARD_REPORT_SIZE),
)

object KeyboardReportEncoder {
    const val REPORT_ID = 1

    fun digitsWithEnter(digits: String): List<KeyboardReport> {
        require(digits.isNotEmpty()) { "At least one digit is required" }
        return buildList {
            digits.forEach { digit ->
                require(digit in '0'..'9') { "Only decimal digits are supported" }
                add(report(digit.toString(), digitUsage(digit)))
            }
            add(report("Enter", ENTER_USAGE))
        }
    }

    fun singleUsage(label: String, usage: Int): KeyboardReport {
        require(usage in 0x04..0x65) { "Usage must be a standard keyboard key" }
        return report(label, usage)
    }

    private fun report(label: String, usage: Int): KeyboardReport = KeyboardReport(
        label = label,
        keyDown = ByteArray(KEYBOARD_REPORT_SIZE).also { it[2] = usage.toByte() },
    )

    private fun digitUsage(digit: Char): Int = when (digit) {
        '0' -> 0x27
        else -> 0x1E + (digit - '1')
    }

    private const val ENTER_USAGE = 0x28
}

private const val KEYBOARD_REPORT_SIZE = 8
