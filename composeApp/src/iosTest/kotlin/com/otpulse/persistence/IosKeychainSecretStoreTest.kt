package com.otpulse.persistence

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IosKeychainSecretStoreTest {
    // Kotlin/Native's CLI-style simulator test executable has no application
    // Keychain entitlement. The same round-trip runs in KeychainTestHost.app.
    @Ignore
    @Test
    fun secretSurvivesStoreRecreationAndDeleteRemovesIt() {
        val reference = "ios-test-${Random.nextLong()}"
        val expected = "keychain-test-secret".encodeToByteArray()
        val first = IosKeychainSecretStore()

        assertIs<StorageResult.Success<Unit>>(first.put(reference, expected), "Keychain OSStatus=${first.lastStatus}")
        val restored = assertIs<StorageResult.Success<ByteArray>>(IosKeychainSecretStore().get(reference)).value
        assertContentEquals(expected, restored)
        restored.fill(0)

        assertIs<StorageResult.Success<Unit>>(first.delete(reference))
        assertEquals(false, assertIs<StorageResult.Success<Boolean>>(first.contains(reference)).value)
    }
}
