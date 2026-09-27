package com.otpulse.bluetooth

sealed interface BluetoothUxPhase {
    data object Unsupported : BluetoothUxPhase
    data object PermissionRequired : BluetoothUxPhase
    data object BluetoothDisabled : BluetoothUxPhase
    data object Preparing : BluetoothUxPhase
    data object NoPairedHosts : BluetoothUxPhase
    data object ChooseHost : BluetoothUxPhase
    data class Disconnected(val host: HidHost) : BluetoothUxPhase
    data class Connecting(val host: HidHost?, val attempt: Int) : BluetoothUxPhase
    data class Connected(val host: HidHost) : BluetoothUxPhase
    data class WaitingForFreshCurrent(val host: HidHost) : BluetoothUxPhase
    data class Sending(val host: HidHost, val completedKeys: Int, val totalKeys: Int) : BluetoothUxPhase
    data class RecoveryFailed(val message: String, val host: HidHost?) : BluetoothUxPhase
}

fun HidTestState.toBluetoothUxPhase(): BluetoothUxPhase {
    val selectedHost = hosts.firstOrNull { it.address == selectedAddress }
    return when {
        !supported -> BluetoothUxPhase.Unsupported
        !permissionGranted -> BluetoothUxPhase.PermissionRequired
        !bluetoothEnabled -> BluetoothUxPhase.BluetoothDisabled
        recoveryError != null -> BluetoothUxPhase.RecoveryFailed(recoveryError, selectedHost)
        !profileReady || !registered -> BluetoothUxPhase.Preparing
        sending && waitingForFreshCode && selectedHost != null ->
            BluetoothUxPhase.WaitingForFreshCurrent(selectedHost)
        sending && selectedHost != null -> BluetoothUxPhase.Sending(
            host = selectedHost,
            completedKeys = sentKeys.coerceIn(0, sendKeysTotal.coerceAtLeast(0)),
            totalKeys = sendKeysTotal.coerceAtLeast(0),
        )
        connecting -> BluetoothUxPhase.Connecting(selectedHost, reconnectAttempt.coerceAtLeast(0))
        selectedHost?.connected == true -> BluetoothUxPhase.Connected(selectedHost)
        hosts.isEmpty() -> BluetoothUxPhase.NoPairedHosts
        selectedHost != null -> BluetoothUxPhase.Disconnected(selectedHost)
        else -> BluetoothUxPhase.ChooseHost
    }
}

val BluetoothUxPhase.canSendOverHid: Boolean
    get() = this is BluetoothUxPhase.Connected
