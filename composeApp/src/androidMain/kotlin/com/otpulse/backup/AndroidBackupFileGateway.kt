package com.otpulse.backup

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class AndroidBackupFileGateway(private val activity: ComponentActivity) : BackupFileGateway {
    private var pendingExportText: String? = null
    private var exportCallback: ((BackupFileWriteResult) -> Unit)? = null
    private var importCallback: ((BackupFileReadResult) -> Unit)? = null

    private val createDocument = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val callback = exportCallback ?: return@registerForActivityResult
        exportCallback = null
        val text = pendingExportText
        pendingExportText = null
        if (uri == null || text == null) {
            callback(BackupFileWriteResult.Cancelled)
            return@registerForActivityResult
        }
        val result = try {
            activity.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
                ?: error("No output stream")
            BackupFileWriteResult.Success
        } catch (_: Throwable) {
            BackupFileWriteResult.Failure("Не удалось записать backup")
        }
        callback(result)
    }

    private val openDocument = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = importCallback ?: return@registerForActivityResult
        importCallback = null
        if (uri == null) {
            callback(BackupFileReadResult.Cancelled)
            return@registerForActivityResult
        }
        Log.d(LOG_TAG, "Import selected, mime=${activity.contentResolver.getType(uri) ?: "unknown"}")
        val result = try {
            val text = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val content = reader.readText()
                val size = content.encodeToByteArray().size
                Log.d(LOG_TAG, "Import read complete, bytes=$size")
                require(size <= MAX_BACKUP_BYTES) { "size=$size bytes, limit=$MAX_BACKUP_BYTES bytes" }
                content
            } ?: error("No input stream")
            BackupFileReadResult.Success(text)
        } catch (error: Throwable) {
            Log.e(LOG_TAG, "Import read failed: ${error::class.simpleName}: ${error.message ?: "no details"}")
            BackupFileReadResult.Failure(
                "Не удалось прочитать backup (${error::class.simpleName}: ${error.message ?: "no details"})"
            )
        }
        callback(result)
    }

    override fun createBackup(text: String, onResult: (BackupFileWriteResult) -> Unit) {
        if (exportCallback != null || importCallback != null) return
        pendingExportText = text
        exportCallback = onResult
        createDocument.launch("otpulse-backup.txt")
    }

    override fun openBackup(onResult: (BackupFileReadResult) -> Unit) {
        if (exportCallback != null || importCallback != null) return
        importCallback = onResult
        openDocument.launch(arrayOf("text/plain", "text/*", "application/json", "application/octet-stream"))
    }

    private companion object {
        const val LOG_TAG = "OTPulseBackup"
        const val MAX_BACKUP_BYTES = 2 * 1024 * 1024
    }
}
