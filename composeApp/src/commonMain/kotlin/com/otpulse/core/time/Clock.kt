package com.otpulse.core.time

fun interface Clock {
    /** Unix wall-clock time. This, rather than a ticker, is the TOTP source of truth. */
    fun nowEpochMilliseconds(): Long
}

class FakeClock(var epochMilliseconds: Long) : Clock {
    override fun nowEpochMilliseconds(): Long = epochMilliseconds
}
