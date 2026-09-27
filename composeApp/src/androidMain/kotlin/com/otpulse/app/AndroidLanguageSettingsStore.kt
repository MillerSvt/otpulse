package com.otpulse.app

import android.content.Context

class AndroidLanguageSettingsStore(context: Context) : LanguageSettingsStore {
    private val preferences = context.getSharedPreferences("otpulse_language", Context.MODE_PRIVATE)

    override fun selectedLanguageTag(): String? = preferences.getString(KEY_LANGUAGE, null)

    override fun setSelectedLanguageTag(tag: String?) {
        preferences.edit().apply {
            if (tag == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, tag)
        }.apply()
    }

    private companion object {
        const val KEY_LANGUAGE = "selected_language"
    }
}
