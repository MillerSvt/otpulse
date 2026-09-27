package com.otpulse.presentation

/**
 * Allows the decorative countdown reset only for the next consecutive TOTP period.
 * Recomposition, clock jumps and lifecycle refreshes must simply establish a new baseline.
 */
class CountdownArcTransitionTracker(initialCounter: Long) {
    private var counter = initialCounter

    fun observe(newCounter: Long): Boolean {
        val shouldAnimate = newCounter == counter + 1L
        counter = newCounter
        return shouldAnimate
    }
}
