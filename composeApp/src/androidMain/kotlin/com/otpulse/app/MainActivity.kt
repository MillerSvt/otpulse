package com.otpulse.app

import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import com.otpulse.backup.AndroidBackupFileGateway
import com.otpulse.bluetooth.AndroidBluetoothHidTestController
import com.otpulse.core.lifecycle.AppLifecycleController
import com.otpulse.persistence.AndroidPersistence
import com.otpulse.qr.AndroidQrCodeScanner
import com.otpulse.timeshift.AndroidTimeShiftSettingsStore
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var hidTestController: AndroidBluetoothHidTestController
    private lateinit var backupFileGateway: AndroidBackupFileGateway
    private val lifecycleController = AppLifecycleController()
    private val timeShiftSettingsStore by lazy { AndroidTimeShiftSettingsStore(this) }
    private val languageSettingsStore by lazy { AndroidLanguageSettingsStore(this) }
    private val themeSettingsStore by lazy { AndroidThemeSettingsStore(this) }
    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_CHANGED) lifecycleController.onSystemTimeChanged()
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hidTestController.initialize()
    }

    private val bluetoothEnableLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hidTestController.initialize()
    }

    private val discoverableLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hidTestController.refresh()
    }

    override fun attachBaseContext(newBase: Context) {
        val storedLanguage = AndroidLanguageSettingsStore(newBase).selectedLanguageTag()
        val supportedLanguage = AppLanguage.fromTag(storedLanguage)?.tag
        super.attachBaseContext(newBase.withAppLanguage(supportedLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_OTPulse)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val storedLanguage = languageSettingsStore.selectedLanguageTag()
        val supportedLanguage = AppLanguage.fromTag(storedLanguage)?.tag
        if (storedLanguage != supportedLanguage) languageSettingsStore.setSelectedLanguageTag(null)
        backupFileGateway = AndroidBackupFileGateway(this)
        val timeChangeFilter = IntentFilter(Intent.ACTION_TIME_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timeChangeReceiver, timeChangeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(timeChangeReceiver, timeChangeFilter)
        }
        hidTestController = AndroidBluetoothHidTestController.getInstance(this)
        setContent {
            OTPulseApp(
                accountRepository = AndroidPersistence.repository(this@MainActivity),
                hidTestController = hidTestController,
                qrCodeScanner = AndroidQrCodeScanner(this@MainActivity),
                lifecycleController = lifecycleController,
                timeShiftSettingsStore = timeShiftSettingsStore,
                languageSettingsStore = languageSettingsStore,
                themeSettingsStore = themeSettingsStore,
                backupFileGateway = backupFileGateway,
                onRequestBluetoothPermissions = {
                    permissionLauncher.launch(AndroidBluetoothHidTestController.REQUIRED_PERMISSIONS)
                },
                onRequestEnableBluetooth = {
                    bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                },
                onRequestDiscoverable = {
                    discoverableLauncher.launch(
                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(
                            BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                            AndroidBluetoothHidTestController.DISCOVERABLE_SECONDS,
                        )
                    )
                },
                onApplyLanguage = { recreate() },
                onApplyTheme = { theme, darkTheme ->
                    persistApplicationNightMode(theme)
                    window.statusBarColor = android.graphics.Color.parseColor(
                        if (darkTheme) "#09111F" else "#F7F8FC"
                    )
                    window.navigationBarColor = android.graphics.Color.parseColor(
                        if (darkTheme) "#09111F" else "#F7F8FC"
                    )
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                },
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            AndroidBluetoothHidTestController.REQUIRED_PERMISSIONS.any {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
        ) {
            permissionLauncher.launch(AndroidBluetoothHidTestController.REQUIRED_PERMISSIONS)
        }
    }

    private fun persistApplicationNightMode(theme: AppTheme) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val uiModeManager = getSystemService(UiModeManager::class.java)
        val mode = when (theme) {
            AppTheme.LIGHT -> UiModeManager.MODE_NIGHT_NO
            AppTheme.DARK -> UiModeManager.MODE_NIGHT_YES
            AppTheme.SYSTEM -> uiModeManager.nightMode
        }
        uiModeManager.setApplicationNightMode(mode)
    }

    private fun Context.withAppLanguage(tag: String?): Context {
        val locale = localeFor(tag)
        Locale.setDefault(locale)
        if (tag == null) return this
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return createConfigurationContext(configuration)
    }

    private fun restoreSelectedLocale() {
        val storedLanguage = languageSettingsStore.selectedLanguageTag()
        val supportedLanguage = AppLanguage.fromTag(storedLanguage)?.tag
        Locale.setDefault(localeFor(supportedLanguage))
    }

    private fun localeFor(tag: String?): Locale = if (tag == null) {
        Resources.getSystem().configuration.locales[0]
    } else {
        Locale.forLanguageTag(tag)
    }

    override fun onStart() {
        super.onStart()
        lifecycleController.onForeground()
    }

    override fun onResume() {
        // Android restores the process default locale from the system configuration while
        // returning from the Google Code Scanner. Compose Resources reads Locale.getDefault(),
        // so restore the explicit app locale before the resumed lifecycle reaches Compose.
        restoreSelectedLocale()
        super.onResume()
        lifecycleController.onForeground()
        if (::hidTestController.isInitialized) hidTestController.initialize()
    }

    override fun onPause() {
        lifecycleController.onBackground()
        super.onPause()
    }

    override fun onStop() {
        lifecycleController.onBackground()
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(timeChangeReceiver)
        super.onDestroy()
    }
}
