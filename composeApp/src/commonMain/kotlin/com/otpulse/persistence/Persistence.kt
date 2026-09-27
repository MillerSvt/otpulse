package com.otpulse.persistence

import com.otpulse.core.model.TotpAccount
import com.otpulse.totp.TotpAlgorithm
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.buffer
import kotlin.random.Random

enum class StorageError {
    IO,
    SECURE_STORAGE_UNAVAILABLE,
    NOT_FOUND,
    CORRUPT_DATA,
    DUPLICATE,
    INVALID_INPUT,
    ROLLBACK_FAILED,
}

sealed interface StorageResult<out T> {
    data class Success<T>(val value: T) : StorageResult<T>
    data class Failure(val error: StorageError) : StorageResult<Nothing>
}

interface SecretStore {
    fun put(reference: String, secret: ByteArray): StorageResult<Unit>
    fun get(reference: String): StorageResult<ByteArray>
    fun contains(reference: String): StorageResult<Boolean>
    fun delete(reference: String): StorageResult<Unit>
}

enum class RecordState { PENDING_CREATE, ACTIVE, PENDING_DELETE }

data class AccountRecord(
    val account: TotpAccount,
    val state: RecordState,
)

interface AccountMetadataStore {
    fun read(): StorageResult<List<AccountRecord>>
    fun replace(records: List<AccountRecord>): StorageResult<Unit>
}

/**
 * Small transactional metadata database. The complete snapshot is written to a
 * sibling file and atomically moved into place. It never contains TOTP secrets.
 */
class FileAccountMetadataStore(
    private val fileSystem: FileSystem,
    private val databasePath: Path,
) : AccountMetadataStore {
    override fun read(): StorageResult<List<AccountRecord>> {
        return try {
            if (!fileSystem.exists(databasePath)) return StorageResult.Success(emptyList())
            fileSystem.read(databasePath) {
                if (readInt() != MAGIC || readInt() != FORMAT_VERSION) {
                    return StorageResult.Failure(StorageError.CORRUPT_DATA)
                }
                val count = readInt()
                if (count !in 0..MAX_RECORDS) return StorageResult.Failure(StorageError.CORRUPT_DATA)
                val records = buildList(count) {
                    repeat(count) {
                        val id = readSizedUtf8()
                        val issuer = readSizedUtf8()
                        val accountName = readSizedUtf8()
                        val secretReference = readSizedUtf8()
                        val algorithm = TotpAlgorithm.entries.getOrNull(readInt())
                            ?: return StorageResult.Failure(StorageError.CORRUPT_DATA)
                        val digits = readInt()
                        val period = readInt()
                        val sortOrder = readInt()
                        val shift = readLong().takeUnless { it == NO_TIME_SHIFT }
                        val state = RecordState.entries.getOrNull(readInt())
                            ?: return StorageResult.Failure(StorageError.CORRUPT_DATA)
                        if (id.isBlank() || secretReference.isBlank() || digits !in setOf(6, 8) || period <= 0 || sortOrder < 0 || (shift != null && shift < 0L)) {
                            return StorageResult.Failure(StorageError.CORRUPT_DATA)
                        }
                        add(
                            AccountRecord(
                                TotpAccount(id, issuer, accountName, secretReference, algorithm, digits, period, sortOrder, shift),
                                state,
                            )
                        )
                    }
                }
                if (records.distinctBy { it.account.id }.size != records.size ||
                    records.distinctBy { it.account.secretReference }.size != records.size
                ) {
                    return StorageResult.Failure(StorageError.CORRUPT_DATA)
                }
                if (!exhausted()) return StorageResult.Failure(StorageError.CORRUPT_DATA)
                StorageResult.Success(records)
            }
        } catch (_: Throwable) {
            StorageResult.Failure(StorageError.IO)
        }
    }

    override fun replace(records: List<AccountRecord>): StorageResult<Unit> = try {
        val parent = databasePath.parent ?: return StorageResult.Failure(StorageError.IO)
        fileSystem.createDirectories(parent)
        val temporary = parent / "${databasePath.name}.next"
        val buffer = Buffer().apply {
            writeInt(MAGIC)
            writeInt(FORMAT_VERSION)
            writeInt(records.size)
            records.forEach { record ->
                val account = record.account
                writeSizedUtf8(account.id)
                writeSizedUtf8(account.issuer)
                writeSizedUtf8(account.accountName)
                writeSizedUtf8(account.secretReference)
                writeInt(account.algorithm.ordinal)
                writeInt(account.digits)
                writeInt(account.periodSeconds)
                writeInt(account.sortOrder)
                writeLong(account.optionalTimeShiftOverrideMilliseconds ?: NO_TIME_SHIFT)
                writeInt(record.state.ordinal)
            }
        }
        fileSystem.write(temporary, mustCreate = false) {
            writeAll(buffer)
            flush()
        }
        fileSystem.atomicMove(temporary, databasePath)
        StorageResult.Success(Unit)
    } catch (_: Throwable) {
        StorageResult.Failure(StorageError.IO)
    }

    private fun okio.BufferedSource.readSizedUtf8(): String {
        val size = readInt()
        if (size !in 0..MAX_STRING_BYTES) error("Invalid metadata string length")
        return readUtf8(size.toLong())
    }

    private fun Buffer.writeSizedUtf8(value: String) {
        val encoded = value.encodeToByteArray()
        require(encoded.size <= MAX_STRING_BYTES)
        writeInt(encoded.size)
        write(encoded)
    }

    private companion object {
        const val MAGIC = 0x4F545044 // OTPD
        const val FORMAT_VERSION = 1
        const val MAX_RECORDS = 10_000
        const val MAX_STRING_BYTES = 64 * 1024
        const val NO_TIME_SHIFT = Long.MIN_VALUE
    }
}

class InMemoryAccountMetadataStore : AccountMetadataStore {
    private var records = emptyList<AccountRecord>()

    override fun read(): StorageResult<List<AccountRecord>> = StorageResult.Success(records.map { it.copy() })

    override fun replace(records: List<AccountRecord>): StorageResult<Unit> {
        this.records = records.map { it.copy() }
        return StorageResult.Success(Unit)
    }
}

class InMemorySecretStore : SecretStore {
    private val secrets = mutableMapOf<String, ByteArray>()

    override fun put(reference: String, secret: ByteArray): StorageResult<Unit> {
        if (reference in secrets) return StorageResult.Failure(StorageError.DUPLICATE)
        secrets[reference] = secret.copyOf()
        return StorageResult.Success(Unit)
    }

    override fun get(reference: String): StorageResult<ByteArray> = secrets[reference]?.let {
        StorageResult.Success(it.copyOf())
    } ?: StorageResult.Failure(StorageError.NOT_FOUND)

    override fun contains(reference: String): StorageResult<Boolean> = StorageResult.Success(reference in secrets)

    override fun delete(reference: String): StorageResult<Unit> {
        secrets.remove(reference)?.fill(0)
        return StorageResult.Success(Unit)
    }
}

class AccountRepository(
    private val metadataStore: AccountMetadataStore,
    private val secretStore: SecretStore,
    private val idSource: () -> String = { randomIdentifier() },
) {
    private var recovered = false

    fun accounts(): StorageResult<List<TotpAccount>> {
        val records = when (val result = readyRecords()) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        val active = records.filter { it.state == RecordState.ACTIVE }
        for (record in active) {
            when (val present = secretStore.contains(record.account.secretReference)) {
                is StorageResult.Success -> if (!present.value) return StorageResult.Failure(StorageError.CORRUPT_DATA)
                is StorageResult.Failure -> return present
            }
        }
        return StorageResult.Success(active.map { it.account }.sortedBy { it.sortOrder })
    }

    fun secret(accountId: String): StorageResult<ByteArray> {
        val account = when (val result = findActive(accountId)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        return secretStore.get(account.secretReference)
    }

    fun create(
        issuer: String,
        accountName: String,
        secret: ByteArray,
        algorithm: TotpAlgorithm,
        digits: Int,
        periodSeconds: Int,
        sortOrder: Int,
    ): StorageResult<TotpAccount> {
        if ((issuer.isBlank() && accountName.isBlank()) || secret.isEmpty() || digits !in setOf(6, 8) || periodSeconds <= 0 || sortOrder < 0) {
            return StorageResult.Failure(StorageError.INVALID_INPUT)
        }
        val records = when (val result = readyRecords()) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        for (record in records.filter { it.state == RecordState.ACTIVE }) {
            val existingSecret = when (val result = secretStore.get(record.account.secretReference)) {
                is StorageResult.Success -> result.value
                is StorageResult.Failure -> return result
            }
            val duplicate = try {
                existingSecret.contentEquals(secret)
            } finally {
                existingSecret.fill(0)
            }
            if (duplicate) return StorageResult.Failure(StorageError.DUPLICATE)
        }
        val id = "account-${idSource()}"
        val reference = "secret-${idSource()}"
        if (records.any { it.account.id == id || it.account.secretReference == reference }) {
            return StorageResult.Failure(StorageError.DUPLICATE)
        }
        val account = TotpAccount(id, issuer, accountName, reference, algorithm, digits, periodSeconds, sortOrder)
        val pending = records + AccountRecord(account, RecordState.PENDING_CREATE)
        val prepared = metadataStore.replace(pending)
        if (prepared is StorageResult.Failure) return prepared
        val secretWrite = secretStore.put(reference, secret)
        if (secretWrite is StorageResult.Failure) {
            val rollback = metadataStore.replace(records)
            return if (rollback is StorageResult.Failure) StorageResult.Failure(StorageError.ROLLBACK_FAILED) else secretWrite
        }
        val activated = metadataStore.replace(pending.map { if (it.account.id == id) it.copy(state = RecordState.ACTIVE) else it })
        if (activated is StorageResult.Failure) {
            val secretRollback = secretStore.delete(reference)
            val metadataRollback = metadataStore.replace(records)
            return if (secretRollback is StorageResult.Failure || metadataRollback is StorageResult.Failure) {
                StorageResult.Failure(StorageError.ROLLBACK_FAILED)
            } else activated
        }
        return StorageResult.Success(account)
    }

    fun update(account: TotpAccount): StorageResult<TotpAccount> {
        if ((account.issuer.isBlank() && account.accountName.isBlank()) ||
            account.digits !in setOf(6, 8) || account.periodSeconds <= 0 || account.sortOrder < 0 ||
            (account.optionalTimeShiftOverrideMilliseconds != null && account.optionalTimeShiftOverrideMilliseconds < 0L)
        ) return StorageResult.Failure(StorageError.INVALID_INPUT)
        val records = when (val result = readyRecords()) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        val existing = records.firstOrNull { it.account.id == account.id && it.state == RecordState.ACTIVE }
            ?: return StorageResult.Failure(StorageError.NOT_FOUND)
        if (account.secretReference != existing.account.secretReference) {
            return StorageResult.Failure(StorageError.CORRUPT_DATA)
        }
        return when (val result = metadataStore.replace(records.map { if (it.account.id == account.id) it.copy(account = account) else it })) {
            is StorageResult.Success -> StorageResult.Success(account)
            is StorageResult.Failure -> result
        }
    }

    fun delete(accountId: String): StorageResult<Unit> {
        val records = when (val result = readyRecords()) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        val target = records.firstOrNull { it.account.id == accountId && it.state == RecordState.ACTIVE }
            ?: return StorageResult.Failure(StorageError.NOT_FOUND)
        val pending = records.map { if (it.account.id == accountId) it.copy(state = RecordState.PENDING_DELETE) else it }
        val marked = metadataStore.replace(pending)
        if (marked is StorageResult.Failure) return marked
        val deleted = secretStore.delete(target.account.secretReference)
        if (deleted is StorageResult.Failure) {
            val rollback = metadataStore.replace(records)
            return if (rollback is StorageResult.Failure) StorageResult.Failure(StorageError.ROLLBACK_FAILED) else deleted
        }
        val finalized = metadataStore.replace(records.filterNot { it.account.id == accountId })
        return if (finalized is StorageResult.Failure) StorageResult.Failure(StorageError.ROLLBACK_FAILED) else StorageResult.Success(Unit)
    }

    private fun findActive(accountId: String): StorageResult<TotpAccount> = when (val result = accounts()) {
        is StorageResult.Success -> result.value.firstOrNull { it.id == accountId }?.let { StorageResult.Success(it) }
            ?: StorageResult.Failure(StorageError.NOT_FOUND)
        is StorageResult.Failure -> result
    }

    private fun readyRecords(): StorageResult<List<AccountRecord>> {
        val initial = metadataStore.read()
        if (initial is StorageResult.Failure) return initial
        var records = (initial as StorageResult.Success).value
        if (!recovered) {
            val pending = records.filter { it.state != RecordState.ACTIVE }
            for (record in pending) {
                val cleanup = secretStore.delete(record.account.secretReference)
                if (cleanup is StorageResult.Failure) return cleanup
            }
            if (pending.isNotEmpty()) {
                records = records.filter { it.state == RecordState.ACTIVE }
                val saved = metadataStore.replace(records)
                if (saved is StorageResult.Failure) return saved
            }
            recovered = true
        }
        return StorageResult.Success(records)
    }

    private companion object {
        fun randomIdentifier(): String = Random.nextBytes(16).joinToString("") { byte ->
            byte.toUByte().toString(16).padStart(2, '0')
        }
    }
}
