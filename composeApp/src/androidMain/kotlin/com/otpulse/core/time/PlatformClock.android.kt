package com.otpulse.core.time

actual object PlatformClock : Clock {
    override fun nowEpochMilliseconds(): Long = System.currentTimeMillis()
}
