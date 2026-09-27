package com.otpulse.timeshift

const val DEFAULT_MANUAL_INPUT_MILLISECONDS = 5_000L
const val MAX_MANUAL_INPUT_MILLISECONDS = 5_000L

sealed interface TimeShiftSettingResult {
    data object Success : TimeShiftSettingResult
    data class Failure(val message: String) : TimeShiftSettingResult
}

interface TimeShiftSettingsStore {
    fun expectedManualInputMilliseconds(): Long
    fun setExpectedManualInputMilliseconds(value: Long): TimeShiftSettingResult
}

class InMemoryTimeShiftSettingsStore(
    initialValue: Long = DEFAULT_MANUAL_INPUT_MILLISECONDS,
) : TimeShiftSettingsStore {
    private var value = normalizeGlobalManualInputMilliseconds(initialValue)

    override fun expectedManualInputMilliseconds(): Long = value

    override fun setExpectedManualInputMilliseconds(value: Long): TimeShiftSettingResult {
        if (value !in 0L..MAX_MANUAL_INPUT_MILLISECONDS || value % 1_000L != 0L) {
            return TimeShiftSettingResult.Failure("Выберите целое значение от 0 до 5 секунд")
        }
        this.value = value
        return TimeShiftSettingResult.Success
    }
}

fun normalizeGlobalManualInputMilliseconds(value: Long): Long {
    val clamped = value.coerceIn(0L, MAX_MANUAL_INPUT_MILLISECONDS)
    return ((clamped + 500L) / 1_000L) * 1_000L
}
