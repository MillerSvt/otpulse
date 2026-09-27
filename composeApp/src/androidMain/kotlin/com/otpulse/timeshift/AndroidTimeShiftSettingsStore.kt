package com.otpulse.timeshift

import android.content.Context

class AndroidTimeShiftSettingsStore(context: Context) : TimeShiftSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun expectedManualInputMilliseconds(): Long =
        normalizeGlobalManualInputMilliseconds(
            preferences.getLong(EXPECTED_MANUAL_INPUT_KEY, DEFAULT_MANUAL_INPUT_MILLISECONDS)
        )

    override fun setExpectedManualInputMilliseconds(value: Long): TimeShiftSettingResult {
        if (value !in 0L..MAX_MANUAL_INPUT_MILLISECONDS || value % 1_000L != 0L) {
            return TimeShiftSettingResult.Failure("Выберите целое значение от 0 до 5 секунд")
        }
        return if (preferences.edit().putLong(EXPECTED_MANUAL_INPUT_KEY, value).commit()) {
            TimeShiftSettingResult.Success
        } else {
            TimeShiftSettingResult.Failure("Не удалось сохранить настройку TimeShift")
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "otpulse-timeshift"
        const val EXPECTED_MANUAL_INPUT_KEY = "expected-manual-input-ms"
    }
}
