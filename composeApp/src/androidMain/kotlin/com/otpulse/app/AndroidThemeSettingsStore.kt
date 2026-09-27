package com.otpulse.app

import android.content.Context

class AndroidThemeSettingsStore(context: Context) : ThemeSettingsStore {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun selectedTheme(): AppTheme =
        AppTheme.fromStorageValue(preferences.getString(KEY_THEME, null))

    override fun setSelectedTheme(theme: AppTheme) {
        preferences.edit().putString(KEY_THEME, theme.storageValue).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "otpulse_appearance"
        const val KEY_THEME = "selected_theme"
    }
}
