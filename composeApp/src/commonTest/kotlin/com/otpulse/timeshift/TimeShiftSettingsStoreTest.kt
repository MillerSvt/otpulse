package com.otpulse.timeshift

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TimeShiftSettingsStoreTest {
    @Test
    fun inMemorySettingPersistsValidManualInputTime() {
        val settings = InMemoryTimeShiftSettingsStore()

        assertIs<TimeShiftSettingResult.Success>(settings.setExpectedManualInputMilliseconds(4_000L))

        assertEquals(4_000L, settings.expectedManualInputMilliseconds())
    }

    @Test
    fun invalidSettingDoesNotReplacePreviousValue() {
        val settings = InMemoryTimeShiftSettingsStore(4_000L)

        assertIs<TimeShiftSettingResult.Failure>(settings.setExpectedManualInputMilliseconds(-1L))
        assertIs<TimeShiftSettingResult.Failure>(settings.setExpectedManualInputMilliseconds(1_500L))
        assertIs<TimeShiftSettingResult.Failure>(
            settings.setExpectedManualInputMilliseconds(MAX_MANUAL_INPUT_MILLISECONDS + 1L)
        )

        assertEquals(4_000L, settings.expectedManualInputMilliseconds())
    }

    @Test
    fun legacyGlobalValueIsRoundedToNearestSliderSecond() {
        assertEquals(5_000L, InMemoryTimeShiftSettingsStore(4_500L).expectedManualInputMilliseconds())
        assertEquals(4_000L, InMemoryTimeShiftSettingsStore(4_499L).expectedManualInputMilliseconds())
    }
}
