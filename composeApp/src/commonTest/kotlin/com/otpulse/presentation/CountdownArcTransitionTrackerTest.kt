package com.otpulse.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CountdownArcTransitionTrackerTest {
    @Test
    fun animatesOnlyTheNextConsecutivePeriod() {
        val tracker = CountdownArcTransitionTracker(initialCounter = 10L)

        assertFalse(tracker.observe(10L))
        assertTrue(tracker.observe(11L))
        assertFalse(tracker.observe(11L))
        assertTrue(tracker.observe(12L))
    }

    @Test
    fun clockJumpsOnlyReplaceTheBaseline() {
        val tracker = CountdownArcTransitionTracker(initialCounter = 10L)

        assertFalse(tracker.observe(15L))
        assertTrue(tracker.observe(16L))
        assertFalse(tracker.observe(8L))
        assertTrue(tracker.observe(9L))
    }
}
