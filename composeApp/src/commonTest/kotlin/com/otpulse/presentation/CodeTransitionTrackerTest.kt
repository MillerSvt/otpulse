package com.otpulse.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodeTransitionTrackerTest {
    @Test
    fun `normal boundary promotes next code once`() {
        val tracker = CodeTransitionTracker(10L, "111111", "222222")

        assertNull(tracker.observe(10L, "111111", "222222"))
        assertEquals(
            CodeTransitionPlan("111111", "222222", "333333"),
            tracker.observe(11L, "222222", "333333"),
        )
        assertNull(tracker.observe(11L, "222222", "333333"))
    }

    @Test
    fun `clock jumps and mismatched codes do not animate`() {
        val tracker = CodeTransitionTracker(10L, "111111", "222222")

        assertNull(tracker.observe(15L, "999999", "000000"))
        assertNull(tracker.observe(14L, "888888", "999999"))
        assertNull(tracker.observe(15L, "777777", "888888"))
    }
}
