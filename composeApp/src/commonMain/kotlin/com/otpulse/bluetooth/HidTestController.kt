package com.otpulse.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class HidHost(
    val address: String,
    val name: String,
    val connected: Boolean,
)

enum class HidSendOutcome {
    NONE,
    SUCCESS,
    FAILURE,
}

data class HidTestState(
    val supported: Boolean = false,
    val permissionGranted: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val profileReady: Boolean = false,
    val registered: Boolean = false,
    val hosts: List<HidHost> = emptyList(),
    val selectedAddress: String? = null,
    val connecting: Boolean = false,
    val sending: Boolean = false,
    val waitingForFreshCode: Boolean = false,
    val sendingAccountId: String? = null,
    val sentKeys: Int = 0,
    val sendKeysTotal: Int = 0,
    val successfulSendCount: Int = 0,
    val failedSendCount: Int = 0,
    val lastSendOutcome: HidSendOutcome = HidSendOutcome.NONE,
    val averageSendDurationMilliseconds: Long = DEFAULT_EXPECTED_BLUETOOTH_INPUT_MILLISECONDS,
    val reconnectAttempt: Int = 0,
    val recoveryError: String? = null,
    val message: String = "Bluetooth HID доступен только в Android-сборке",
)

enum class MacSetupKey(val label: String, val usage: Int) {
    RIGHT_OF_LEFT_SHIFT("Z", 0x1D),
    LEFT_OF_RIGHT_SHIFT("/", 0x38),
}

interface HidTestController {
    val state: StateFlow<HidTestState>
    fun initialize()
    fun refresh()
    fun connect(address: String)
    fun disconnect()
    fun sendTestSequence()
    fun sendOtp(accountId: String, periodMilliseconds: Long, snapshotProvider: BluetoothOtpSnapshotProvider)
    fun sendMacSetupKey(key: MacSetupKey)
    fun retryConnection()
    fun selectDifferentHost()
    fun dismissRecovery()
    fun close()
}

object UnsupportedHidTestController : HidTestController {
    override val state: StateFlow<HidTestState> = MutableStateFlow(HidTestState())
    override fun initialize() = Unit
    override fun refresh() = Unit
    override fun connect(address: String) = Unit
    override fun disconnect() = Unit
    override fun sendTestSequence() = Unit
    override fun sendOtp(accountId: String, periodMilliseconds: Long, snapshotProvider: BluetoothOtpSnapshotProvider) = Unit
    override fun sendMacSetupKey(key: MacSetupKey) = Unit
    override fun retryConnection() = Unit
    override fun selectDifferentHost() = Unit
    override fun dismissRecovery() = Unit
    override fun close() = Unit
}
