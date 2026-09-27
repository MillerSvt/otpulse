package com.otpulse.backup

import com.otpulse.otpauth.OtpAuth
import com.otpulse.otpauth.OtpAuthAccount
import com.otpulse.otpauth.OtpAuthResult
import com.otpulse.persistence.AccountRepository
import com.otpulse.persistence.StorageResult
import com.otpulse.totp.Base32
import com.otpulse.totp.Base32Result
import com.otpulse.totp.TotpAlgorithm

enum class BackupEntryStatus { NEW, DUPLICATE_EXISTING, DUPLICATE_IN_FILE }

data class BackupPreviewEntry(
    val account: OtpAuthAccount,
    val status: BackupEntryStatus,
)

data class BackupPreview(val entries: List<BackupPreviewEntry>) {
    val newCount: Int get() = entries.count { it.status == BackupEntryStatus.NEW }
    val duplicateCount: Int get() = entries.size - newCount
    val importCount: Int get() = entries.size
}

sealed interface BackupExportResult {
    data class Success(val text: String, val accountCount: Int) : BackupExportResult
    data class Failure(val message: String) : BackupExportResult
}

sealed interface BackupPreviewResult {
    data class Success(val preview: BackupPreview) : BackupPreviewResult
    data class Failure(val message: String) : BackupPreviewResult
}

sealed interface BackupRestoreResult {
    data class Success(val importedCount: Int, val skippedDuplicateCount: Int) : BackupRestoreResult
    data class Failure(val message: String, val importedBeforeFailure: Int) : BackupRestoreResult
}

class BackupService(private val repository: AccountRepository) {
    fun exportText(): BackupExportResult {
        val accounts = when (val result = repository.accounts()) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return BackupExportResult.Failure("Не удалось прочитать аккаунты")
        }
        val uris = mutableListOf<String>()
        val seenSecrets = mutableSetOf<String>()
        for (account in accounts) {
            val secret = when (val result = repository.secret(account.id)) {
                is StorageResult.Success -> result.value
                is StorageResult.Failure -> return BackupExportResult.Failure("Не удалось прочитать защищённый секрет")
            }
            try {
                val encodedSecret = Base32.encode(secret)
                if (!seenSecrets.add(encodedSecret)) continue
                uris += OtpAuth.serialize(
                    OtpAuthAccount(
                        issuer = account.issuer,
                        accountName = account.accountName,
                        secret = encodedSecret,
                        algorithm = account.algorithm,
                        digits = account.digits,
                        periodSeconds = account.periodSeconds,
                    )
                )
            } finally {
                secret.fill(0)
            }
        }
        val text = if (uris.isEmpty()) "" else uris.joinToString(separator = "\n", postfix = "\n")
        return BackupExportResult.Success(text, uris.size)
    }

    fun preview(text: String): BackupPreviewResult {
        val content = text.removePrefix("\uFEFF").trimStart()
        backupLog("Preview started: chars=${text.length}, format=${if (content.startsWith('[')) "json" else "otpauth"}")
        val imported = if (content.startsWith('[')) {
            when (val parsed = parseJsonAccounts(content)) {
                is ParsedAccounts.Success -> parsed.accounts
                is ParsedAccounts.Failure -> {
                    backupLog("JSON parse rejected: ${parsed.message}")
                    return BackupPreviewResult.Failure(parsed.message)
                }
            }
        } else {
            when (val parsed = parseOtpAuthLines(text)) {
                is ParsedAccounts.Success -> parsed.accounts
                is ParsedAccounts.Failure -> {
                    backupLog("otpauth parse rejected: ${parsed.message}")
                    return BackupPreviewResult.Failure(parsed.message)
                }
            }
        }
        if (imported.isEmpty()) {
            backupLog("Preview rejected: no accounts")
            return BackupPreviewResult.Failure("Backup не содержит аккаунтов")
        }
        val existing = when (val result = existingAccounts()) {
            is ExistingAccounts.Success -> result.accounts
            is ExistingAccounts.Failure -> {
                backupLog("Preview failed while reading existing accounts: ${result.message}")
                return BackupPreviewResult.Failure(result.message)
            }
        }
        val seenSecrets = mutableSetOf<String>()
        val preview = BackupPreview(
            imported.map { account ->
                val normalizedSecret = Base32.normalize(account.secret)
                val status = when {
                    normalizedSecret in existing -> BackupEntryStatus.DUPLICATE_EXISTING
                    !seenSecrets.add(normalizedSecret) -> BackupEntryStatus.DUPLICATE_IN_FILE
                    else -> BackupEntryStatus.NEW
                }
                BackupPreviewEntry(account, status)
            }
        )
        backupLog("Preview ready: entries=${preview.importCount}, new=${preview.newCount}, duplicates=${preview.duplicateCount}")
        return BackupPreviewResult.Success(
            preview
        )
    }

    private fun parseOtpAuthLines(text: String): ParsedAccounts {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith('#') }.toList()
        if (lines.isEmpty()) return ParsedAccounts.Failure("Файл не содержит ссылок otpauth://")
        val imported = mutableListOf<OtpAuthAccount>()
        lines.forEachIndexed { index, line ->
            when (val parsed = OtpAuth.parse(line)) {
                is OtpAuthResult.Success -> imported += parsed.account
                is OtpAuthResult.Failure -> return ParsedAccounts.Failure("Строка ${index + 1} содержит некорректную ссылку otpauth://")
            }
        }
        return ParsedAccounts.Success(imported)
    }

    private fun parseJsonAccounts(text: String): ParsedAccounts {
        val objects = JsonBackupParser(text).parseArray()
            ?: return ParsedAccounts.Failure("JSON backup должен быть массивом объектов")
        val accounts = mutableListOf<OtpAuthAccount>()
        objects.forEachIndexed { index, objectValue ->
            val fields = objectValue.fields
            fun string(name: String): String? = (fields[name] as? JsonValue.StringValue)?.value
            fun integer(name: String): Int? = (fields[name] as? JsonValue.NumberValue)?.value?.toIntOrNull()
            val accountName = string("accountName")
                ?: return ParsedAccounts.Failure("JSON запись ${index + 1}: отсутствует accountName")
            val issuer = string("issuer").orEmpty()
            val secret = string("secret")
                ?: return ParsedAccounts.Failure("JSON запись ${index + 1}: отсутствует secret")
            val algorithmName = string("algorithm")?.uppercase()?.replace("-", "")
                ?: return ParsedAccounts.Failure("JSON запись ${index + 1}: отсутствует algorithm")
            val algorithm = when (algorithmName) {
                "SHA1" -> TotpAlgorithm.SHA1
                "SHA256" -> TotpAlgorithm.SHA256
                "SHA512" -> TotpAlgorithm.SHA512
                else -> return ParsedAccounts.Failure("JSON запись ${index + 1}: алгоритм не поддерживается")
            }
            val digits = integer("digits")
                ?: return ParsedAccounts.Failure("JSON запись ${index + 1}: отсутствует или некорректен digits")
            if (digits !in setOf(6, 8)) return ParsedAccounts.Failure("JSON запись ${index + 1}: digits должен быть 6 или 8")
            val period = integer("period")
                ?: return ParsedAccounts.Failure("JSON запись ${index + 1}: отсутствует или некорректен period")
            if (period <= 0) return ParsedAccounts.Failure("JSON запись ${index + 1}: period должен быть больше нуля")
            if (issuer.isBlank() && accountName.isBlank()) {
                return ParsedAccounts.Failure("JSON запись ${index + 1}: issuer и accountName пусты")
            }
            val normalizedSecret = Base32.normalize(secret)
            val decoded = Base32.decode(normalizedSecret)
            if (decoded !is Base32Result.Success) {
                return ParsedAccounts.Failure("JSON запись ${index + 1}: secret не является корректным Base32")
            }
            decoded.bytes.fill(0)
            accounts += OtpAuthAccount(issuer, accountName, normalizedSecret, algorithm, digits, period)
        }
        return ParsedAccounts.Success(accounts)
    }

    fun restore(preview: BackupPreview): BackupRestoreResult {
        var imported = 0
        val createdAccountIds = mutableListOf<String>()
        for (entry in preview.entries) {
            if (entry.status != BackupEntryStatus.NEW) continue
            val account = entry.account
            val decoded = com.otpulse.totp.Base32.decode(account.secret)
            val secret = (decoded as? com.otpulse.totp.Base32Result.Success)?.bytes
                ?: return BackupRestoreResult.Failure("Секрет в backup повреждён", imported)
            val sortOrder = when (val result = repository.accounts()) {
                is StorageResult.Success -> result.value.size
                is StorageResult.Failure -> {
                    secret.fill(0)
                    return BackupRestoreResult.Failure("Не удалось подготовить импорт", imported)
                }
            }
            val created = repository.create(
                issuer = account.issuer,
                accountName = account.accountName,
                secret = secret,
                algorithm = account.algorithm,
                digits = account.digits,
                periodSeconds = account.periodSeconds,
                sortOrder = sortOrder,
            )
            secret.fill(0)
            if (created is StorageResult.Failure) {
                var rolledBack = true
                createdAccountIds.asReversed().forEach { if (repository.delete(it) is StorageResult.Failure) rolledBack = false }
                return BackupRestoreResult.Failure(
                    if (rolledBack) "Импорт отменён: не удалось сохранить один из аккаунтов" else "Импорт прерван, и откат не удалось завершить полностью",
                    if (rolledBack) 0 else imported,
                )
            }
            createdAccountIds += (created as StorageResult.Success).value.id
            imported++
        }
        return BackupRestoreResult.Success(imported, preview.duplicateCount)
    }

    private fun existingAccounts(): ExistingAccounts {
        val accounts = when (val result = repository.accounts()) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return ExistingAccounts.Failure("Не удалось прочитать существующие аккаунты")
        }
        val result = mutableSetOf<String>()
        for (account in accounts) {
            val secret = when (val stored = repository.secret(account.id)) {
                is StorageResult.Success -> stored.value
                is StorageResult.Failure -> return ExistingAccounts.Failure("Не удалось проверить существующие аккаунты")
            }
            try {
                result += Base32.encode(secret)
            } finally {
                secret.fill(0)
            }
        }
        return ExistingAccounts.Success(result)
    }

    private sealed interface ExistingAccounts {
        data class Success(val accounts: Set<String>) : ExistingAccounts
        data class Failure(val message: String) : ExistingAccounts
    }

    private sealed interface ParsedAccounts {
        data class Success(val accounts: List<OtpAuthAccount>) : ParsedAccounts
        data class Failure(val message: String) : ParsedAccounts
    }

    private fun backupLog(message: String) {
        println("OTPulseBackup: $message")
    }
}
