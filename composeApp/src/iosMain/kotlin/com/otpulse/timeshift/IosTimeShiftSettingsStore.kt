package com.otpulse.timeshift

import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

class IosTimeShiftSettingsStore : TimeShiftSettingsStore {
    private val preferences = NSUserDefaults.standardUserDefaults

    override fun expectedManualInputMilliseconds(): Long {
        val stored = (preferences.objectForKey(EXPECTED_MANUAL_INPUT_KEY) as? NSNumber)?.longLongValue
        return normalizeGlobalManualInputMilliseconds(stored ?: DEFAULT_MANUAL_INPUT_MILLISECONDS)
    }

    override fun setExpectedManualInputMilliseconds(value: Long): TimeShiftSettingResult {
        if (value !in 0L..MAX_MANUAL_INPUT_MILLISECONDS || value % 1_000L != 0L) {
            return TimeShiftSettingResult.Failure("Выберите целое значение от 0 до 5 секунд")
        }
        preferences.setInteger(value, forKey = EXPECTED_MANUAL_INPUT_KEY)
        return TimeShiftSettingResult.Success
    }

    private companion object {
        const val EXPECTED_MANUAL_INPUT_KEY = "otpulse.expected-manual-input-ms"
    }
}
