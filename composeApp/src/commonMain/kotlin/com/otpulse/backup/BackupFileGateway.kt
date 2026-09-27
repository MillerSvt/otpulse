package com.otpulse.backup

sealed interface BackupFileWriteResult {
    data object Success : BackupFileWriteResult
    data object Cancelled : BackupFileWriteResult
    data class Failure(val message: String) : BackupFileWriteResult
}

sealed interface BackupFileReadResult {
    data class Success(val text: String) : BackupFileReadResult
    data object Cancelled : BackupFileReadResult
    data class Failure(val message: String) : BackupFileReadResult
}

interface BackupFileGateway {
    fun createBackup(text: String, onResult: (BackupFileWriteResult) -> Unit)
    fun openBackup(onResult: (BackupFileReadResult) -> Unit)
}

object UnsupportedBackupFileGateway : BackupFileGateway {
    override fun createBackup(text: String, onResult: (BackupFileWriteResult) -> Unit) {
        onResult(BackupFileWriteResult.Failure("Сохранение файлов недоступно на этой платформе"))
    }

    override fun openBackup(onResult: (BackupFileReadResult) -> Unit) {
        onResult(BackupFileReadResult.Failure("Выбор файлов недоступен на этой платформе"))
    }
}
