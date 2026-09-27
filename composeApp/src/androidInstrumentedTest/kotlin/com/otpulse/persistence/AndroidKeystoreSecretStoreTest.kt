package com.otpulse.persistence

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otpulse.core.model.TotpAccount
import com.otpulse.totp.TotpAlgorithm
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSecretStoreTest {
    @Test
    fun secretSurvivesStoreRecreationAndDeleteRemovesIt() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reference = "instrumented-${System.nanoTime()}"
        val expected = "keystore-test-secret".encodeToByteArray()
        val first = AndroidKeystoreSecretStore(context)

        assertTrue(first.put(reference, expected) is StorageResult.Success)
        val restoredResult = AndroidKeystoreSecretStore(context).get(reference)
        assertTrue(restoredResult is StorageResult.Success)
        val restored = (restoredResult as StorageResult.Success).value
        assertArrayEquals(expected, restored)
        restored.fill(0)

        assertTrue(first.delete(reference) is StorageResult.Success)
        val contains = first.contains(reference)
        assertTrue(contains is StorageResult.Success)
        assertFalse((contains as StorageResult.Success).value)
    }

    @Test
    fun repositorySurvivesRecreationWithRealMetadataAndKeystore() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = (context.filesDir.absolutePath + "/phase3-instrumented-${System.nanoTime()}").toPath()
        val metadataPath = directory / "accounts.db"
        val secretStore = AndroidKeystoreSecretStore(context)
        var generated = 0
        try {
            val first = AccountRepository(FileAccountMetadataStore(FileSystem.SYSTEM, metadataPath), secretStore) {
                "instrumented-${System.nanoTime()}-${++generated}"
            }
            val createdResult = first.create(
                issuer = "Instrumented",
                accountName = "restart",
                secret = "repository-secret".encodeToByteArray(),
                algorithm = TotpAlgorithm.SHA256,
                digits = 8,
                periodSeconds = 60,
                sortOrder = 0,
            )
            assertTrue(createdResult is StorageResult.Success)
            val created = (createdResult as StorageResult.Success<TotpAccount>).value

            val restarted = AccountRepository(FileAccountMetadataStore(FileSystem.SYSTEM, metadataPath), secretStore)
            val accounts = restarted.accounts()
            assertTrue(accounts is StorageResult.Success)
            assertTrue((accounts as StorageResult.Success).value.single() == created)
            val secret = restarted.secret(created.id)
            assertTrue(secret is StorageResult.Success)
            val restoredSecret = (secret as StorageResult.Success).value
            assertArrayEquals("repository-secret".encodeToByteArray(), restoredSecret)
            restoredSecret.fill(0)

            assertTrue(restarted.delete(created.id) is StorageResult.Success)
            assertFalse((secretStore.contains(created.secretReference) as StorageResult.Success).value)
        } finally {
            if (FileSystem.SYSTEM.exists(directory)) FileSystem.SYSTEM.deleteRecursively(directory)
        }
    }
}
