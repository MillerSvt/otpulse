package com.otpulse.core.time

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.gettimeofday
import platform.posix.timeval

actual object PlatformClock : Clock {
    @OptIn(ExperimentalForeignApi::class)
    override fun nowEpochMilliseconds(): Long = memScoped {
        val time = alloc<timeval>()
        gettimeofday(time.ptr, null)
        time.tv_sec * 1_000L + time.tv_usec / 1_000L
    }
}
