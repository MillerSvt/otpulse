package com.otpulse.presentation

/**
 * Distinguishes a normal TOTP boundary from recomposition, foreground refreshes and clock jumps.
 * The OTP state always updates immediately; this class only decides whether decoration may animate.
 */
data class CodeTransitionPlan(
    val outgoingCurrentCode: String,
    val promotedNextCode: String,
    val incomingNextCode: String,
)

class CodeTransitionTracker(
    initialCounter: Long,
    initialCurrentCode: String,
    initialNextCode: String,
) {
    private var counter = initialCounter
    private var currentCode = initialCurrentCode
    private var nextCode = initialNextCode

    fun observe(newCounter: Long, newCurrentCode: String, newNextCode: String): CodeTransitionPlan? {
        val plan = if (newCounter == counter + 1L && newCurrentCode == nextCode) {
            CodeTransitionPlan(currentCode, nextCode, newNextCode)
        } else {
            null
        }
        counter = newCounter
        currentCode = newCurrentCode
        nextCode = newNextCode
        return plan
    }
}
