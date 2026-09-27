package com.otpulse.bluetooth

import com.otpulse.core.time.Clock
import com.otpulse.timeshift.BluetoothSendReadiness
import com.otpulse.timeshift.BluetoothSendReadinessPolicy
import kotlinx.coroutines.delay

data class BluetoothOtpSnapshot(
    val accountId: String,
    val code: String,
    val counter: Long,
    val capturedAtEpochMilliseconds: Long,
)

fun interface BluetoothOtpSnapshotProvider {
    fun currentAt(epochMilliseconds: Long): BluetoothOtpSnapshot?
}

fun interface BluetoothSendWaiter {
    suspend fun wait(milliseconds: Long)
}

sealed interface BluetoothOtpPreparation {
    data class Ready(
        val snapshot: BluetoothOtpSnapshot,
        val waitedMilliseconds: Long,
    ) : BluetoothOtpPreparation

    data class Failure(val reason: BluetoothOtpPreparationError) : BluetoothOtpPreparation
}

enum class BluetoothOtpPreparationError {
    SNAPSHOT_UNAVAILABLE,
    INVALID_CODE,
    STALE_SNAPSHOT,
}

/**
 * Establishes the atomic boundary of a production Bluetooth send operation.
 * No OTP is captured before a critical-window wait. The returned snapshot is
 * the only code the HID layer may encode and transmit for this operation.
 */
class BluetoothOtpSendPipeline(
    private val clock: Clock,
    private val waiter: BluetoothSendWaiter = BluetoothSendWaiter { delay(it) },
) {
    suspend fun prepare(
        accountId: String,
        periodMilliseconds: Long,
        expectedBluetoothInputMilliseconds: Long,
        onWaitingForFreshCurrent: (Long) -> Unit = {},
        snapshotProvider: BluetoothOtpSnapshotProvider,
    ): BluetoothOtpPreparation {
        require(accountId.isNotBlank())
        require(periodMilliseconds > 0L)

        val requestedAt = clock.nowEpochMilliseconds()
        val readiness = BluetoothSendReadinessPolicy.evaluate(
            nowEpochMilliseconds = requestedAt,
            periodMilliseconds = periodMilliseconds,
            expectedBluetoothInputMilliseconds = expectedBluetoothInputMilliseconds,
        )
        if (readiness.readiness == BluetoothSendReadiness.WAIT_FOR_FRESH_CURRENT) {
            onWaitingForFreshCurrent(readiness.waitMilliseconds)
            waiter.wait(readiness.waitMilliseconds)
        }

        val capturedAt = clock.nowEpochMilliseconds()
        val provided = snapshotProvider.currentAt(capturedAt)
            ?: return BluetoothOtpPreparation.Failure(BluetoothOtpPreparationError.SNAPSHOT_UNAVAILABLE)
        if (provided.accountId != accountId || provided.code.isEmpty() || provided.code.any { it !in '0'..'9' }) {
            return BluetoothOtpPreparation.Failure(BluetoothOtpPreparationError.INVALID_CODE)
        }
        val actualCounter = capturedAt / periodMilliseconds
        if (provided.counter != actualCounter || provided.capturedAtEpochMilliseconds != capturedAt) {
            return BluetoothOtpPreparation.Failure(BluetoothOtpPreparationError.STALE_SNAPSHOT)
        }

        return BluetoothOtpPreparation.Ready(
            snapshot = provided.copy(code = provided.code.toCharArray().concatToString()),
            waitedMilliseconds = readiness.waitMilliseconds,
        )
    }
}

data class BluetoothTimingStatistics(
    val averageMilliseconds: Long = DEFAULT_EXPECTED_BLUETOOTH_INPUT_MILLISECONDS,
    val sampleCount: Int = 0,
) {
    fun record(durationMilliseconds: Long): BluetoothTimingStatistics {
        require(durationMilliseconds >= 0L)
        val nextCount = (sampleCount + 1).coerceAtMost(MAX_TIMING_SAMPLES)
        val retainedCount = nextCount - 1
        val boundedDuration = durationMilliseconds.coerceAtMost(MAX_RECORDED_BLUETOOTH_INPUT_MILLISECONDS)
        val nextAverage = if (sampleCount == 0) {
            boundedDuration
        } else {
            ((averageMilliseconds * retainedCount) + boundedDuration) / nextCount
        }
        return BluetoothTimingStatistics(nextAverage, nextCount)
    }
}

const val DEFAULT_EXPECTED_BLUETOOTH_INPUT_MILLISECONDS = 250L
const val MAX_RECORDED_BLUETOOTH_INPUT_MILLISECONDS = 5_000L
const val MAX_TIMING_SAMPLES = 20
