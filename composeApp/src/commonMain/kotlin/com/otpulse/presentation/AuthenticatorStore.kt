package com.otpulse.presentation

import com.otpulse.otpauth.OtpAuth
import com.otpulse.otpauth.OtpAuthAccount
import com.otpulse.otpauth.OtpAuthError
import com.otpulse.otpauth.OtpAuthResult
import com.otpulse.persistence.AccountRepository
import com.otpulse.persistence.StorageError
import com.otpulse.persistence.StorageResult
import com.otpulse.totp.Base32
import com.otpulse.totp.Base32Result
import com.otpulse.totp.TotpAlgorithm
import com.otpulse.totp.TotpEngine
import com.otpulse.timeshift.CodeSelectionPolicy
import com.otpulse.timeshift.DEFAULT_MANUAL_INPUT_MILLISECONDS
import com.otpulse.timeshift.MAX_MANUAL_INPUT_MILLISECONDS
import com.otpulse.timeshift.ManualCodePosition
import com.otpulse.bluetooth.BluetoothOtpSnapshot

data class AccountCodeView(
    val id: String,
    val issuer: String,
    val accountName: String,
    val currentCode: String,
    val nextCode: String,
    val counter: Long,
    val periodMilliseconds: Long,
    val remainingMilliseconds: Long,
    val manualRecommendation: ManualCodePosition,
    val recommendedManualCode: String,
    val expectedManualInputMilliseconds: Long,
    val usesManualInputOverride: Boolean,
)

data class EditableAccount(
    val id: String,
    val issuer: String,
    val accountName: String,
    val algorithm: String,
    val digits: String,
    val period: String,
    val manualInputOverrideMilliseconds: String,
)

sealed interface AddAccountResult {
    data object Success : AddAccountResult
    data class Failure(val message: String) : AddAccountResult
}

sealed interface AccountInputResult {
    data object Imported : AccountInputResult
    data class NeedsDetails(val normalizedSecret: String) : AccountInputResult
    data class Failure(val message: String) : AccountInputResult
}

sealed interface AccountMutationResult {
    data object Success : AccountMutationResult
    data class Failure(val message: String) : AccountMutationResult
}

class AuthenticatorStore(private val repository: AccountRepository) {
    var lastStorageError: StorageError? = null
        private set

    fun codeViews(
        epochMilliseconds: Long,
        expectedManualInputMilliseconds: Long = DEFAULT_MANUAL_INPUT_MILLISECONDS,
    ): List<AccountCodeView> {
        val accounts = when (val result = repository.accounts()) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> {
                lastStorageError = result.error
                return emptyList()
            }
        }
        return accounts.mapNotNull { account ->
        val secret = when (val result = repository.secret(account.id)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> {
                lastStorageError = result.error
                return@mapNotNull null
            }
        }
        try {
        val pair = TotpEngine.pair(
            secret = secret,
            timestampMilliseconds = epochMilliseconds,
            algorithm = account.algorithm,
            digits = account.digits,
            periodSeconds = account.periodSeconds,
        )
        val manualInputTime = account.optionalTimeShiftOverrideMilliseconds ?: expectedManualInputMilliseconds
        val manualSelection = CodeSelectionPolicy.selectForManualInput(
            nowEpochMilliseconds = epochMilliseconds,
            periodMilliseconds = account.periodSeconds * 1_000L,
            expectedManualInputMilliseconds = manualInputTime,
            currentCode = pair.current,
            nextCode = pair.next,
        )
        AccountCodeView(
            id = account.id,
            issuer = account.issuer,
            accountName = account.accountName,
            currentCode = pair.current,
            nextCode = pair.next,
            counter = pair.counter,
            periodMilliseconds = account.periodSeconds * 1_000L,
            remainingMilliseconds = pair.remainingMilliseconds,
            manualRecommendation = manualSelection.position,
            recommendedManualCode = manualSelection.code,
            expectedManualInputMilliseconds = manualInputTime,
            usesManualInputOverride = account.optionalTimeShiftOverrideMilliseconds != null,
        )
        } finally {
            secret.fill(0)
        }
        }.also { lastStorageError = null }
    }

    fun bluetoothSnapshot(accountId: String, epochMilliseconds: Long): BluetoothOtpSnapshot? {
        if (epochMilliseconds < 0L) return null
        val account = when (val result = repository.accounts()) {
            is StorageResult.Success -> result.value.firstOrNull { it.id == accountId }
            is StorageResult.Failure -> {
                lastStorageError = result.error
                return null
            }
        } ?: return null
        val secret = when (val result = repository.secret(accountId)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> {
                lastStorageError = result.error
                return null
            }
        }
        return try {
            val pair = TotpEngine.pair(
                secret = secret,
                timestampMilliseconds = epochMilliseconds,
                algorithm = account.algorithm,
                digits = account.digits,
                periodSeconds = account.periodSeconds,
            )
            lastStorageError = null
            BluetoothOtpSnapshot(
                accountId = accountId,
                code = pair.current,
                counter = pair.counter,
                capturedAtEpochMilliseconds = epochMilliseconds,
            )
        } finally {
            secret.fill(0)
        }
    }

    fun addManual(
        issuer: String,
        accountName: String,
        secret: String,
        algorithm: String,
        digits: String,
        period: String,
    ): AddAccountResult {
        val normalizedSecret = Base32.normalize(secret)
        val decoded = Base32.decode(normalizedSecret)
        if (decoded !is Base32Result.Success) return AddAccountResult.Failure("Проверьте секретный ключ Base32")
        val parsedAlgorithm = when (algorithm.trim().uppercase()) {
            "SHA1", "SHA-1" -> TotpAlgorithm.SHA1
            "SHA256", "SHA-256" -> TotpAlgorithm.SHA256
            "SHA512", "SHA-512" -> TotpAlgorithm.SHA512
            else -> return AddAccountResult.Failure("Поддерживаются SHA1, SHA256 и SHA512")
        }
        val parsedDigits = digits.toIntOrNull()
        if (parsedDigits != 6 && parsedDigits != 8) return AddAccountResult.Failure("Код должен содержать 6 или 8 цифр")
        val parsedPeriod = period.toIntOrNull()
        if (parsedPeriod == null || parsedPeriod <= 0) return AddAccountResult.Failure("Период должен быть больше нуля")
        if (issuer.isBlank() && accountName.isBlank()) return AddAccountResult.Failure("Укажите имя или аккаунт")

        val secretBytes = decoded.bytes.copyOf()
        val sortOrder = when (val existing = repository.accounts()) {
            is StorageResult.Success -> existing.value.size
            is StorageResult.Failure -> {
                secretBytes.fill(0)
                lastStorageError = existing.error
                return AddAccountResult.Failure(existing.error.userMessage())
            }
        }
        val created = repository.create(
            issuer = issuer.trim().ifEmpty { "Без названия" },
            accountName = accountName.trim(),
            secret = secretBytes,
            algorithm = parsedAlgorithm,
            digits = parsedDigits,
            periodSeconds = parsedPeriod,
            sortOrder = sortOrder,
        )
        secretBytes.fill(0)
        return when (created) {
            is StorageResult.Success -> {
                lastStorageError = null
                AddAccountResult.Success
            }
            is StorageResult.Failure -> {
                lastStorageError = created.error
                AddAccountResult.Failure(created.error.userMessage())
            }
        }
    }

    fun importUri(uri: String): AddAccountResult = when (val result = OtpAuth.parse(uri.trim())) {
        is OtpAuthResult.Success -> add(result.account)
        is OtpAuthResult.Failure -> AddAccountResult.Failure(result.error.userMessage())
    }

    fun consumeAccountInput(input: String): AccountInputResult {
        val trimmed = input.trim()
        if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            return when (val imported = importUri(trimmed)) {
                AddAccountResult.Success -> AccountInputResult.Imported
                is AddAccountResult.Failure -> AccountInputResult.Failure(imported.message)
            }
        }

        val normalizedSecret = Base32.normalize(trimmed)
        return when (val decoded = Base32.decode(normalizedSecret)) {
            is Base32Result.Success -> {
                val isNotEmpty = decoded.bytes.isNotEmpty()
                decoded.bytes.fill(0)
                if (isNotEmpty) AccountInputResult.NeedsDetails(normalizedSecret)
                else AccountInputResult.Failure("Введите ссылку otpauth:// или секрет Base32")
            }
            is Base32Result.Failure -> AccountInputResult.Failure("Введите ссылку otpauth:// или секрет Base32")
        }
    }

    fun editableAccount(accountId: String): StorageResult<EditableAccount> {
        val accounts = repository.accounts()
        if (accounts is StorageResult.Failure) {
            lastStorageError = accounts.error
            return accounts
        }
        val account = (accounts as StorageResult.Success).value.firstOrNull { it.id == accountId }
            ?: return StorageResult.Failure(StorageError.NOT_FOUND)
        lastStorageError = null
        return StorageResult.Success(
            EditableAccount(
                id = account.id,
                issuer = account.issuer,
                accountName = account.accountName,
                algorithm = account.algorithm.name,
                digits = account.digits.toString(),
                period = account.periodSeconds.toString(),
                manualInputOverrideMilliseconds = account.optionalTimeShiftOverrideMilliseconds?.toString().orEmpty(),
            )
        )
    }

    fun updateAccount(
        accountId: String,
        issuer: String,
        accountName: String,
        algorithm: String,
        digits: String,
        period: String,
        manualInputOverrideMilliseconds: String = "",
    ): AccountMutationResult {
        val parsed = validateMetadata(issuer, accountName, algorithm, digits, period)
        if (parsed is MetadataValidation.Failure) return AccountMutationResult.Failure(parsed.message)
        val values = parsed as MetadataValidation.Success
        val normalizedManualInputOverride = manualInputOverrideMilliseconds.trim()
        val parsedManualInputOverride = when {
            normalizedManualInputOverride.isEmpty() -> null
            normalizedManualInputOverride.toLongOrNull() == null -> {
                return AccountMutationResult.Failure("TimeShift должен быть целым числом миллисекунд")
            }
            normalizedManualInputOverride.toLong() !in 0L..MAX_MANUAL_INPUT_MILLISECONDS -> {
                return AccountMutationResult.Failure("TimeShift должен быть от 0 до 5000 мс")
            }
            else -> normalizedManualInputOverride.toLong()
        }
        val accounts = repository.accounts()
        if (accounts is StorageResult.Failure) return storageFailure(accounts.error)
        val existing = (accounts as StorageResult.Success).value.firstOrNull { it.id == accountId }
            ?: return AccountMutationResult.Failure("Аккаунт не найден")
        return when (
            val updated = repository.update(
                existing.copy(
                    issuer = issuer.trim().ifEmpty { "Без названия" },
                    accountName = accountName.trim(),
                    algorithm = values.algorithm,
                    digits = values.digits,
                    periodSeconds = values.period,
                    optionalTimeShiftOverrideMilliseconds = parsedManualInputOverride,
                )
            )
        ) {
            is StorageResult.Success -> {
                lastStorageError = null
                AccountMutationResult.Success
            }
            is StorageResult.Failure -> storageFailure(updated.error)
        }
    }

    fun deleteAccount(accountId: String): AccountMutationResult = when (val deleted = repository.delete(accountId)) {
        is StorageResult.Success -> {
            lastStorageError = null
            AccountMutationResult.Success
        }
        is StorageResult.Failure -> storageFailure(deleted.error)
    }

    private fun add(account: OtpAuthAccount): AddAccountResult = addManual(
        issuer = account.issuer,
        accountName = account.accountName,
        secret = account.secret,
        algorithm = account.algorithm.name,
        digits = account.digits.toString(),
        period = account.periodSeconds.toString(),
    )

    private sealed interface MetadataValidation {
        data class Success(val algorithm: TotpAlgorithm, val digits: Int, val period: Int) : MetadataValidation
        data class Failure(val message: String) : MetadataValidation
    }

    private fun validateMetadata(
        issuer: String,
        accountName: String,
        algorithm: String,
        digits: String,
        period: String,
    ): MetadataValidation {
        if (issuer.isBlank() && accountName.isBlank()) return MetadataValidation.Failure("Укажите имя или аккаунт")
        val parsedAlgorithm = when (algorithm.trim().uppercase()) {
            "SHA1", "SHA-1" -> TotpAlgorithm.SHA1
            "SHA256", "SHA-256" -> TotpAlgorithm.SHA256
            "SHA512", "SHA-512" -> TotpAlgorithm.SHA512
            else -> return MetadataValidation.Failure("Поддерживаются SHA1, SHA256 и SHA512")
        }
        val parsedDigits = digits.toIntOrNull()
        if (parsedDigits != 6 && parsedDigits != 8) return MetadataValidation.Failure("Код должен содержать 6 или 8 цифр")
        val parsedPeriod = period.toIntOrNull()
        if (parsedPeriod == null || parsedPeriod <= 0) return MetadataValidation.Failure("Период должен быть больше нуля")
        return MetadataValidation.Success(parsedAlgorithm, parsedDigits, parsedPeriod)
    }

    private fun storageFailure(error: StorageError): AccountMutationResult.Failure {
        lastStorageError = error
        return AccountMutationResult.Failure(error.userMessage())
    }

    private fun OtpAuthError.userMessage(): String = when (this) {
        OtpAuthError.HOTP_NOT_SUPPORTED -> "HOTP пока не поддерживается"
        OtpAuthError.MISSING_SECRET -> "В ссылке отсутствует секретный ключ"
        OtpAuthError.INVALID_SECRET -> "Секретный ключ в ссылке повреждён"
        OtpAuthError.UNSUPPORTED_ALGORITHM -> "Алгоритм из ссылки не поддерживается"
        OtpAuthError.INVALID_DIGITS -> "Поддерживаются коды из 6 или 8 цифр"
        OtpAuthError.INVALID_PERIOD -> "Период в ссылке некорректен"
        else -> "Не удалось распознать ссылку otpauth"
    }

    private fun StorageError.userMessage(): String = when (this) {
        StorageError.SECURE_STORAGE_UNAVAILABLE -> "Защищённое хранилище недоступно"
        StorageError.CORRUPT_DATA -> "Локальные данные повреждены"
        StorageError.DUPLICATE -> "Такой аккаунт уже существует"
        StorageError.INVALID_INPUT -> "Параметры аккаунта некорректны"
        StorageError.NOT_FOUND -> "Аккаунт не найден"
        StorageError.ROLLBACK_FAILED -> "Не удалось безопасно завершить операцию хранения"
        StorageError.IO -> "Не удалось сохранить данные"
    }
}
