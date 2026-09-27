package com.otpulse.app

import androidx.compose.ui.window.ComposeUIViewController
import com.otpulse.core.lifecycle.AppLifecycleController
import com.otpulse.persistence.IosPersistence
import com.otpulse.qr.IosQrCodeScanner
import com.otpulse.timeshift.IosTimeShiftSettingsStore

fun MainViewController(lifecycleController: AppLifecycleController) = run {
    val accountRepository = IosPersistence.repository()
    val qrCodeScanner = IosQrCodeScanner()
    val timeShiftSettingsStore = IosTimeShiftSettingsStore()
    val themeSettingsStore = IosThemeSettingsStore()
    ComposeUIViewController {
        OTPulseApp(
            accountRepository = accountRepository,
            qrCodeScanner = qrCodeScanner,
            lifecycleController = lifecycleController,
            timeShiftSettingsStore = timeShiftSettingsStore,
            themeSettingsStore = themeSettingsStore,
        )
    }
}
