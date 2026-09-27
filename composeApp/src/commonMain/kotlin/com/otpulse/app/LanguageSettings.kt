package com.otpulse.app

enum class AppLanguage(val tag: String, val region: String, val rtl: Boolean = false) {
    ENGLISH("en", "US"),
    CHINESE_SIMPLIFIED("zh", "CN"),
    HINDI("hi", "IN"),
    SPANISH("es", "ES"),
    FRENCH("fr", "FR"),
    ARABIC("ar", "SA", rtl = true),
    BENGALI("bn", "BD"),
    PORTUGUESE("pt", "BR"),
    INDONESIAN("id", "ID"),
    URDU("ur", "PK", rtl = true),
    RUSSIAN("ru", "RU"),
    UKRAINIAN("uk", "UA"),
    KAZAKH("kk", "KZ");

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }
    }
}

interface LanguageSettingsStore {
    fun selectedLanguageTag(): String?
    fun setSelectedLanguageTag(tag: String?)
}

class InMemoryLanguageSettingsStore : LanguageSettingsStore {
    private var tag: String? = null
    override fun selectedLanguageTag(): String? = tag
    override fun setSelectedLanguageTag(tag: String?) {
        this.tag = tag
    }
}
