package com.otpulse.app

import platform.Foundation.NSUserDefaults

class IosThemeSettingsStore : ThemeSettingsStore {
    private val preferences = NSUserDefaults.standardUserDefaults

    override fun selectedTheme(): AppTheme =
        AppTheme.fromStorageValue(preferences.stringForKey(KEY_THEME))

    override fun setSelectedTheme(theme: AppTheme) {
        preferences.setObject(theme.storageValue, forKey = KEY_THEME)
    }

    private companion object {
        const val KEY_THEME = "otpulse.selected-theme"
    }
}
