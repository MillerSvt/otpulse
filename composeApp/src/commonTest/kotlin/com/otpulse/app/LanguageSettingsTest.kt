package com.otpulse.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageSettingsTest {
    @Test
    fun `language tag is stored and system choice clears it`() {
        val store = InMemoryLanguageSettingsStore()

        assertNull(store.selectedLanguageTag())
        store.setSelectedLanguageTag(AppLanguage.RUSSIAN.tag)
        assertEquals("ru", store.selectedLanguageTag())
        store.setSelectedLanguageTag(null)
        assertNull(store.selectedLanguageTag())
    }

    @Test
    fun `all supported language tags resolve`() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, AppLanguage.fromTag(language.tag))
        }
    }
}
