package com.otpulse.persistence

import com.otpulse.core.model.TotpAccount
import com.otpulse.totp.TotpAlgorithm
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AccountRepositoryTest {
    @Test
    fun restartPersistenceKeepsMetadataAndSecretSeparated() {
        val directory = "/tmp/otpulse-persistence-${Random.nextLong().toString().replace('-', '0')}".toPath()
        val path = directory / "accounts.db"
        val secrets = InMemorySecretStore()
        try {
            val first = AccountRepository(FileAccountMetadataStore(FileSystem.SYSTEM, path), secrets) { "first-${ids++}" }
            val created = assertIs<StorageResult.Success<TotpAccount>>(first.createAccount()).value
            val configured = assertIs<StorageResult.Success<TotpAccount>>(
                first.update(created.copy(optionalTimeShiftOverrideMilliseconds = 4_500L))
            ).value

            val restarted = AccountRepository(FileAccountMetadataStore(FileSystem.SYSTEM, path), secrets) { "second-${ids++}" }
            val restored = assertIs<StorageResult.Success<List<TotpAccount>>>(restarted.accounts()).value.single()
            assertEquals(configured, restored)
            val restoredSecret = assertIs<StorageResult.Success<ByteArray>>(restarted.secret(restored.id)).value
            assertContentEquals(TEST_SECRET, restoredSecret)

            val rawMetadata = FileSystem.SYSTEM.read(path) { readByteArray() }.decodeToString(throwOnInvalidSequence = false)
            assertTrue("JBSWY3DPEHPK3PXP" !in rawMetadata)
            assertTrue(TEST_SECRET.decodeToString() !in rawMetadata)
        } finally {
            if (FileSystem.SYSTEM.exists(directory)) FileSystem.SYSTEM.deleteRecursively(directory)
        }
    }

    @Test
    fun deleteRemovesMetadataAndSecureSecret() {
        val metadata = InMemoryAccountMetadataStore()
        val secrets = InMemorySecretStore()
        val repository = AccountRepository(metadata, secrets) { "delete-${ids++}" }
        val account = assertIs<StorageResult.Success<TotpAccount>>(repository.createAccount()).value

        assertIs<StorageResult.Success<Unit>>(repository.delete(account.id))

        assertTrue(assertIs<StorageResult.Success<List<TotpAccount>>>(repository.accounts()).value.isEmpty())
        assertEquals(false, assertIs<StorageResult.Success<Boolean>>(secrets.contains(account.secretReference)).value)
    }

    @Test
    fun failedSecretWriteRollsBackPendingMetadata() {
        val metadata = InMemoryAccountMetadataStore()
        val repository = AccountRepository(metadata, FailingPutSecretStore()) { "failure-${ids++}" }

        assertEquals(StorageError.SECURE_STORAGE_UNAVAILABLE, assertIs<StorageResult.Failure>(repository.createAccount()).error)
        assertTrue(assertIs<StorageResult.Success<List<AccountRecord>>>(metadata.read()).value.isEmpty())
    }

    @Test
    fun failedActivationRollsBackSecretAndMetadata() {
        val backing = InMemoryAccountMetadataStore()
        val metadata = FailOnReplaceMetadataStore(backing, failAtCall = 2)
        val secrets = InMemorySecretStore()
        var generated = 0
        val repository = AccountRepository(metadata, secrets) { "activation-${++generated}" }

        assertEquals(StorageError.IO, assertIs<StorageResult.Failure>(repository.createAccount()).error)
        assertTrue(assertIs<StorageResult.Success<List<AccountRecord>>>(backing.read()).value.isEmpty())
        assertEquals(false, assertIs<StorageResult.Success<Boolean>>(secrets.contains("secret-activation-2")).value)
    }

    @Test
    fun restartRecoversInterruptedCreateWithoutPublishingAccount() {
        val metadata = InMemoryAccountMetadataStore()
        val secrets = InMemorySecretStore()
        val account = TotpAccount("pending", "Issuer", "alice", "pending-secret")
        assertIs<StorageResult.Success<Unit>>(metadata.replace(listOf(AccountRecord(account, RecordState.PENDING_CREATE))))
        assertIs<StorageResult.Success<Unit>>(secrets.put(account.secretReference, TEST_SECRET))

        val restarted = AccountRepository(metadata, secrets)

        assertTrue(assertIs<StorageResult.Success<List<TotpAccount>>>(restarted.accounts()).value.isEmpty())
        assertEquals(false, assertIs<StorageResult.Success<Boolean>>(secrets.contains(account.secretReference)).value)
    }

    @Test
    fun failedSecretDeleteRestoresActiveMetadata() {
        val metadata = InMemoryAccountMetadataStore()
        val backingSecrets = InMemorySecretStore()
        val initial = AccountRepository(metadata, backingSecrets) { "delete-rollback-${ids++}" }
        val account = assertIs<StorageResult.Success<TotpAccount>>(initial.createAccount()).value
        val repository = AccountRepository(metadata, FailingDeleteSecretStore(backingSecrets))

        assertEquals(StorageError.SECURE_STORAGE_UNAVAILABLE, assertIs<StorageResult.Failure>(repository.delete(account.id)).error)
        assertEquals(account, assertIs<StorageResult.Success<List<TotpAccount>>>(repository.accounts()).value.single())
        assertEquals(true, assertIs<StorageResult.Success<Boolean>>(backingSecrets.contains(account.secretReference)).value)
    }

    @Test
    fun restartFinishesDeleteWhenFinalMetadataWriteFailed() {
        val backingMetadata = InMemoryAccountMetadataStore()
        val secrets = InMemorySecretStore()
        val initial = AccountRepository(backingMetadata, secrets) { "delete-finalize-${ids++}" }
        val account = assertIs<StorageResult.Success<TotpAccount>>(initial.createAccount()).value
        val failingMetadata = FailOnReplaceMetadataStore(backingMetadata, failAtCall = 2)
        val deleting = AccountRepository(failingMetadata, secrets)

        assertEquals(StorageError.ROLLBACK_FAILED, assertIs<StorageResult.Failure>(deleting.delete(account.id)).error)
        val restarted = AccountRepository(backingMetadata, secrets)
        assertTrue(assertIs<StorageResult.Success<List<TotpAccount>>>(restarted.accounts()).value.isEmpty())
        assertTrue(assertIs<StorageResult.Success<List<AccountRecord>>>(backingMetadata.read()).value.isEmpty())
    }

    @Test
    fun updateChangesMetadataButCannotSwapSecretReference() {
        val repository = AccountRepository(InMemoryAccountMetadataStore(), InMemorySecretStore()) { "update-${ids++}" }
        val original = assertIs<StorageResult.Success<TotpAccount>>(repository.createAccount()).value
        val edited = original.copy(issuer = "Renamed", periodSeconds = 60)

        assertEquals(edited, assertIs<StorageResult.Success<TotpAccount>>(repository.update(edited)).value)
        assertEquals(StorageError.CORRUPT_DATA, assertIs<StorageResult.Failure>(repository.update(edited.copy(secretReference = "other"))).error)
        assertEquals("Renamed", assertIs<StorageResult.Success<List<TotpAccount>>>(repository.accounts()).value.single().issuer)
    }

    @Test
    fun createRejectsDuplicateSecretRegardlessOfDisplayMetadata() {
        val repository = AccountRepository(InMemoryAccountMetadataStore(), InMemorySecretStore()) { "duplicate-${ids++}" }
        assertIs<StorageResult.Success<TotpAccount>>(repository.createAccount())

        val duplicate = repository.create(
            issuer = "Other issuer",
            accountName = "different@example.com",
            secret = TEST_SECRET.copyOf(),
            algorithm = TotpAlgorithm.SHA1,
            digits = 6,
            periodSeconds = 30,
            sortOrder = 1,
        )

        assertEquals(StorageError.DUPLICATE, assertIs<StorageResult.Failure>(duplicate).error)
        assertEquals(1, assertIs<StorageResult.Success<List<TotpAccount>>>(repository.accounts()).value.size)
    }

    private fun AccountRepository.createAccount() = create(
        issuer = "Example",
        accountName = "alice@example.com",
        secret = TEST_SECRET,
        algorithm = TotpAlgorithm.SHA256,
        digits = 8,
        periodSeconds = 60,
        sortOrder = 0,
    )

    private class FailingPutSecretStore : SecretStore {
        override fun put(reference: String, secret: ByteArray) = StorageResult.Failure(StorageError.SECURE_STORAGE_UNAVAILABLE)
        override fun get(reference: String) = StorageResult.Failure(StorageError.NOT_FOUND)
        override fun contains(reference: String) = StorageResult.Success(false)
        override fun delete(reference: String) = StorageResult.Success(Unit)
    }

    private class FailingDeleteSecretStore(private val delegate: SecretStore) : SecretStore by delegate {
        override fun delete(reference: String) = StorageResult.Failure(StorageError.SECURE_STORAGE_UNAVAILABLE)
    }

    private class FailOnReplaceMetadataStore(
        private val delegate: AccountMetadataStore,
        private val failAtCall: Int,
    ) : AccountMetadataStore {
        private var calls = 0
        override fun read() = delegate.read()
        override fun replace(records: List<AccountRecord>): StorageResult<Unit> {
            calls++
            return if (calls == failAtCall) StorageResult.Failure(StorageError.IO) else delegate.replace(records)
        }
    }

    private companion object {
        var ids = 0
        val TEST_SECRET = "Hello!\u0000secure".encodeToByteArray()
    }
}
