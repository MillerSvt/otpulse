package com.otpulse.qr

import android.app.Activity
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class AndroidQrCodeScanner(activity: Activity) : QrCodeScanner {
    private val scanner = GmsBarcodeScanning.getClient(
        activity,
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .allowManualInput()
            .build(),
    )

    private var active = false

    override fun scan(onResult: (QrScanResult) -> Unit) {
        if (active) return
        active = true
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                active = false
                val value = barcode.rawValue
                onResult(
                    if (value.isNullOrBlank()) QrScanResult.Failure("QR-код не содержит данных")
                    else QrScanResult.Success(value)
                )
            }
            .addOnCanceledListener {
                active = false
                onResult(QrScanResult.Cancelled)
            }
            .addOnFailureListener {
                active = false
                onResult(QrScanResult.Failure("Не удалось запустить сканер QR-кодов"))
            }
    }
}
