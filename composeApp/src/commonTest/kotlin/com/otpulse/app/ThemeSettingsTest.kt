package com.otpulse.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeSettingsTest {
    @Test
    fun `system theme is the default`() {
        assertEquals(AppTheme.SYSTEM, InMemoryThemeSettingsStore().selectedTheme())
        assertEquals(AppTheme.SYSTEM, AppTheme.fromStorageValue(null))
        assertEquals(AppTheme.SYSTEM, AppTheme.fromStorageValue("unknown"))
    }

    @Test
    fun `explicit theme is stored`() {
        val store = InMemoryThemeSettingsStore()

        store.setSelectedTheme(AppTheme.LIGHT)
        assertEquals(AppTheme.LIGHT, store.selectedTheme())
        store.setSelectedTheme(AppTheme.DARK)
        assertEquals(AppTheme.DARK, store.selectedTheme())
    }
}
