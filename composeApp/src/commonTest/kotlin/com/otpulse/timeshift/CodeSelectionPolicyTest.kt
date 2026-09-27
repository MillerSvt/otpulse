package com.otpulse.timeshift

import kotlin.test.Test
import kotlin.test.assertEquals

class CodeSelectionPolicyTest {
    @Test
    fun manualInputUsesCurrentWhenRemainingExceedsLeadTime() {
        assertManual(now = 10_000L, lead = 19_999L, expected = ManualCodePosition.CURRENT)
    }

    @Test
    fun manualInputUsesNextWhenRemainingEqualsLeadTime() {
        assertManual(now = 10_000L, lead = 20_000L, expected = ManualCodePosition.NEXT)
    }

    @Test
    fun manualInputUsesNextWhenRemainingIsBelowLeadTime() {
        assertManual(now = 29_000L, lead = 1_001L, expected = ManualCodePosition.NEXT)
    }

    @Test
    fun zeroManualLeadTimeUsesCurrent() {
        assertManual(now = 29_999L, lead = 0L, expected = ManualCodePosition.CURRENT)
    }

    @Test
    fun manualLeadLongerThanPeriodReturnsNextWithoutOverflow() {
        assertManual(now = 29_999L, lead = Long.MAX_VALUE, expected = ManualCodePosition.NEXT)
    }

    @Test
    fun manualPolicySupportsDifferentPeriods() {
        val selected = CodeSelectionPolicy.selectForManualInput(55_000L, 60_000L, 5_000L, "111111", "222222")
        assertEquals(ManualCodePosition.NEXT, selected.position)
        assertEquals("222222", selected.code)
    }

    @Test
    fun bluetoothSendsCurrentAboveCriticalWindow() {
        assertBluetooth(now = 20_000L, expectedInput = 2_000L, expected = BluetoothSendReadiness.SEND_CURRENT_NOW)
    }

    @Test
    fun bluetoothWithZeroExpectedDurationAlwaysSendsCurrent() {
        assertBluetooth(now = 29_999L, expectedInput = 0L, expected = BluetoothSendReadiness.SEND_CURRENT_NOW)
    }

    @Test
    fun bluetoothSendsCurrentAtCriticalWindowEquality() {
        assertBluetooth(now = 22_000L, expectedInput = 2_000L, expected = BluetoothSendReadiness.SEND_CURRENT_NOW)
    }

    @Test
    fun bluetoothWaitsBelowCriticalWindow() {
        val decision = assertBluetooth(
            now = 22_001L,
            expectedInput = 2_000L,
            expected = BluetoothSendReadiness.WAIT_FOR_FRESH_CURRENT,
        )
        assertEquals(7_999L, decision.waitMilliseconds)
    }

    @Test
    fun bluetoothCriticalWindowIsCappedAtOnePeriod() {
        val atBoundary = assertBluetooth(
            now = 30_000L,
            expectedInput = Long.MAX_VALUE,
            expected = BluetoothSendReadiness.SEND_CURRENT_NOW,
        )
        assertEquals(30_000L, atBoundary.criticalWindowMilliseconds)

        assertBluetooth(
            now = 30_001L,
            expectedInput = Long.MAX_VALUE,
            expected = BluetoothSendReadiness.WAIT_FOR_FRESH_CURRENT,
        )
    }

    private fun assertManual(now: Long, lead: Long, expected: ManualCodePosition) {
        val selection = CodeSelectionPolicy.selectForManualInput(now, 30_000L, lead, "111111", "222222")
        assertEquals(expected, selection.position)
        assertEquals(if (expected == ManualCodePosition.CURRENT) "111111" else "222222", selection.code)
    }

    private fun assertBluetooth(
        now: Long,
        expectedInput: Long,
        expected: BluetoothSendReadiness,
    ): BluetoothReadinessDecision {
        val decision = BluetoothSendReadinessPolicy.evaluate(now, 30_000L, expectedInput)
        assertEquals(expected, decision.readiness)
        return decision
    }
}
