package com.otpulse.app

enum class AppTheme(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorageValue(value: String?): AppTheme =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

interface ThemeSettingsStore {
    fun selectedTheme(): AppTheme
    fun setSelectedTheme(theme: AppTheme)
}

class InMemoryThemeSettingsStore(
    initialTheme: AppTheme = AppTheme.SYSTEM,
) : ThemeSettingsStore {
    private var theme = initialTheme

    override fun selectedTheme(): AppTheme = theme

    override fun setSelectedTheme(theme: AppTheme) {
        this.theme = theme
    }
}
