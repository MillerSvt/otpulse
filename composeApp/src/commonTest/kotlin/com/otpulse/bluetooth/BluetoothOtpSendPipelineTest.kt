package com.otpulse.bluetooth

import com.otpulse.core.time.FakeClock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BluetoothOtpSendPipelineTest {
    @Test
    fun capturesCurrentImmediatelyOutsideCriticalWindow() = runBlocking {
        val clock = FakeClock(20_000L)
        var waited = -1L
        val pipeline = BluetoothOtpSendPipeline(clock, BluetoothSendWaiter { waited = it })

        val result = pipeline.prepare("account-1", 30_000L, 2_000L) { now ->
            BluetoothOtpSnapshot("account-1", "123456", now / 30_000L, now)
        }

        val ready = assertIs<BluetoothOtpPreparation.Ready>(result)
        assertEquals("123456", ready.snapshot.code)
        assertEquals(0L, ready.waitedMilliseconds)
        assertEquals(-1L, waited)
    }

    @Test
    fun waitsThenCapturesFreshCurrentInsideCriticalWindow() = runBlocking {
        val clock = FakeClock(29_000L)
        val capturedTimes = mutableListOf<Long>()
        var announcedWait = 0L
        val pipeline = BluetoothOtpSendPipeline(clock, BluetoothSendWaiter { milliseconds ->
            assertEquals(1_000L, milliseconds)
            clock.epochMilliseconds += milliseconds
        })

        val result = pipeline.prepare("account-1", 30_000L, 500L, { announcedWait = it }) { now ->
            capturedTimes += now
            BluetoothOtpSnapshot("account-1", if (now < 30_000L) "111111" else "222222", now / 30_000L, now)
        }

        val ready = assertIs<BluetoothOtpPreparation.Ready>(result)
        assertEquals(listOf(30_000L), capturedTimes)
        assertEquals("222222", ready.snapshot.code)
        assertEquals(1L, ready.snapshot.counter)
        assertEquals(1_000L, ready.waitedMilliseconds)
        assertEquals(1_000L, announcedWait)
    }

    @Test
    fun rereadsWallClockAfterWaitInsteadOfAssumingOneCounterAdvance() = runBlocking {
        val clock = FakeClock(29_900L)
        val pipeline = BluetoothOtpSendPipeline(clock, BluetoothSendWaiter {
            clock.epochMilliseconds = 90_000L
        })

        val ready = assertIs<BluetoothOtpPreparation.Ready>(
            pipeline.prepare("account-1", 30_000L, 100L) { now ->
                BluetoothOtpSnapshot("account-1", "333333", now / 30_000L, now)
            }
        )

        assertEquals(3L, ready.snapshot.counter)
        assertEquals(90_000L, ready.snapshot.capturedAtEpochMilliseconds)
    }

    @Test
    fun rejectsUnavailableInvalidAndStaleSnapshots() = runBlocking {
        val clock = FakeClock(10_000L)
        val pipeline = BluetoothOtpSendPipeline(clock)

        assertEquals(
            BluetoothOtpPreparationError.SNAPSHOT_UNAVAILABLE,
            assertIs<BluetoothOtpPreparation.Failure>(
                pipeline.prepare("account-1", 30_000L, 0L) { null }
            ).reason,
        )
        assertEquals(
            BluetoothOtpPreparationError.INVALID_CODE,
            assertIs<BluetoothOtpPreparation.Failure>(
                pipeline.prepare("account-1", 30_000L, 0L) { now ->
                    BluetoothOtpSnapshot("account-1", "12A456", 0L, now)
                }
            ).reason,
        )
        assertEquals(
            BluetoothOtpPreparationError.STALE_SNAPSHOT,
            assertIs<BluetoothOtpPreparation.Failure>(
                pipeline.prepare("account-1", 30_000L, 0L) { now ->
                    BluetoothOtpSnapshot("account-1", "123456", 1L, now)
                }
            ).reason,
        )
    }

    @Test
    fun timingStatisticsUseBoundedRollingAverage() {
        val first = BluetoothTimingStatistics().record(200L)
        assertEquals(BluetoothTimingStatistics(200L, 1), first)
        assertEquals(BluetoothTimingStatistics(300L, 2), first.record(400L))

        var statistics = BluetoothTimingStatistics(100L, MAX_TIMING_SAMPLES)
        repeat(5) { statistics = statistics.record(200L) }
        assertEquals(MAX_TIMING_SAMPLES, statistics.sampleCount)
        assertEquals(5_000L, BluetoothTimingStatistics().record(50_000L).averageMilliseconds)
    }
}
