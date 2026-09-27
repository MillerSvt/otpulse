package com.otpulse.qr

sealed interface QrScanResult {
    data class Success(val value: String) : QrScanResult
    data object ManualRequested : QrScanResult
    data object Cancelled : QrScanResult
    data class Failure(val message: String) : QrScanResult
}

fun interface QrCodeScanner {
    fun scan(onResult: (QrScanResult) -> Unit)
}

object UnsupportedQrCodeScanner : QrCodeScanner {
    override fun scan(onResult: (QrScanResult) -> Unit) {
        onResult(QrScanResult.Failure("Сканирование QR-кодов недоступно на этой платформе"))
    }
}
