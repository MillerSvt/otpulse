package com.otpulse.timeshift

enum class ManualCodePosition { CURRENT, NEXT }

data class ManualCodeSelection(
    val position: ManualCodePosition,
    val code: String,
)

object CodeSelectionPolicy {
    fun selectForManualInput(
        nowEpochMilliseconds: Long,
        periodMilliseconds: Long,
        expectedManualInputMilliseconds: Long,
        currentCode: String,
        nextCode: String,
    ): ManualCodeSelection {
        require(nowEpochMilliseconds >= 0L)
        require(periodMilliseconds > 0L)
        val leadTime = expectedManualInputMilliseconds.coerceAtLeast(0L)
        val effectiveTime = saturatingAdd(nowEpochMilliseconds, leadTime)
        val position = if (effectiveTime / periodMilliseconds == nowEpochMilliseconds / periodMilliseconds) {
            ManualCodePosition.CURRENT
        } else {
            ManualCodePosition.NEXT
        }
        return ManualCodeSelection(position, if (position == ManualCodePosition.CURRENT) currentCode else nextCode)
    }
}

enum class BluetoothSendReadiness { SEND_CURRENT_NOW, WAIT_FOR_FRESH_CURRENT }

data class BluetoothReadinessDecision(
    val readiness: BluetoothSendReadiness,
    val waitMilliseconds: Long,
    val criticalWindowMilliseconds: Long,
)

object BluetoothSendReadinessPolicy {
    const val CRITICAL_MULTIPLIER = 4L

    fun evaluate(
        nowEpochMilliseconds: Long,
        periodMilliseconds: Long,
        expectedBluetoothInputMilliseconds: Long,
    ): BluetoothReadinessDecision {
        require(nowEpochMilliseconds >= 0L)
        require(periodMilliseconds > 0L)
        val expected = expectedBluetoothInputMilliseconds.coerceAtLeast(0L)
        val multiplied = if (expected > Long.MAX_VALUE / CRITICAL_MULTIPLIER) {
            Long.MAX_VALUE
        } else {
            expected * CRITICAL_MULTIPLIER
        }
        val criticalWindow = multiplied.coerceAtMost(periodMilliseconds)
        val remaining = periodMilliseconds - nowEpochMilliseconds % periodMilliseconds
        val readiness = if (remaining < criticalWindow) {
            BluetoothSendReadiness.WAIT_FOR_FRESH_CURRENT
        } else {
            BluetoothSendReadiness.SEND_CURRENT_NOW
        }
        return BluetoothReadinessDecision(
            readiness = readiness,
            waitMilliseconds = if (readiness == BluetoothSendReadiness.WAIT_FOR_FRESH_CURRENT) remaining else 0L,
            criticalWindowMilliseconds = criticalWindow,
        )
    }
}

private fun saturatingAdd(left: Long, right: Long): Long =
    if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right
