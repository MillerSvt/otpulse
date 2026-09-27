package com.otpulse.bluetooth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BluetoothUxPhaseTest {
    private val host = HidHost("host-1", "MacBook", connected = false)

    @Test
    fun mapsCompleteBluetoothUxFlowWithoutImpossibleShortcuts() {
        var state = HidTestState()
        assertIs<BluetoothUxPhase.Unsupported>(state.toBluetoothUxPhase())

        state = state.copy(supported = true)
        assertIs<BluetoothUxPhase.PermissionRequired>(state.toBluetoothUxPhase())

        state = state.copy(permissionGranted = true)
        assertIs<BluetoothUxPhase.BluetoothDisabled>(state.toBluetoothUxPhase())

        state = state.copy(bluetoothEnabled = true)
        assertIs<BluetoothUxPhase.Preparing>(state.toBluetoothUxPhase())

        state = state.copy(profileReady = true, registered = true)
        assertIs<BluetoothUxPhase.NoPairedHosts>(state.toBluetoothUxPhase())

        state = state.copy(hosts = listOf(host))
        assertIs<BluetoothUxPhase.ChooseHost>(state.toBluetoothUxPhase())

        state = state.copy(selectedAddress = host.address, connecting = true, reconnectAttempt = 1)
        val connecting = assertIs<BluetoothUxPhase.Connecting>(state.toBluetoothUxPhase())
        assertEquals(1, connecting.attempt)

        state = state.copy(connecting = false)
        assertIs<BluetoothUxPhase.Disconnected>(state.toBluetoothUxPhase())

        state = state.copy(hosts = listOf(host.copy(connected = true)))
        assertTrue(state.toBluetoothUxPhase().canSendOverHid)

        state = state.copy(sending = true, waitingForFreshCode = true)
        assertIs<BluetoothUxPhase.WaitingForFreshCurrent>(state.toBluetoothUxPhase())

        state = state.copy(waitingForFreshCode = false, sentKeys = 3, sendKeysTotal = 7)
        val sending = assertIs<BluetoothUxPhase.Sending>(state.toBluetoothUxPhase())
        assertEquals(3, sending.completedKeys)
        assertEquals(7, sending.totalKeys)

        state = state.copy(sending = false, lastSendOutcome = HidSendOutcome.SUCCESS)
        assertIs<BluetoothUxPhase.Connected>(state.toBluetoothUxPhase())

        state = state.copy(
            hosts = listOf(host),
            reconnectAttempt = 3,
            recoveryError = "Не удалось восстановить соединение",
            lastSendOutcome = HidSendOutcome.FAILURE,
        )
        assertIs<BluetoothUxPhase.RecoveryFailed>(state.toBluetoothUxPhase())
        assertFalse(state.toBluetoothUxPhase().canSendOverHid)
    }

    @Test
    fun clampsTransportProgressForPresentation() {
        val state = HidTestState(
            supported = true,
            permissionGranted = true,
            bluetoothEnabled = true,
            profileReady = true,
            registered = true,
            hosts = listOf(host.copy(connected = true)),
            selectedAddress = host.address,
            sending = true,
            sentKeys = 20,
            sendKeysTotal = 7,
        )

        val sending = assertIs<BluetoothUxPhase.Sending>(state.toBluetoothUxPhase())
        assertEquals(7, sending.completedKeys)
        assertEquals(7, sending.totalKeys)
    }
}
