package com.otpulse.presentation

import com.otpulse.core.time.Clock
import com.otpulse.timeshift.DEFAULT_MANUAL_INPUT_MILLISECONDS
import com.otpulse.timeshift.CodeSelectionPolicy
import com.otpulse.timeshift.ManualCodePosition

class CodeListPresenter(
    private val store: AuthenticatorStore,
    private val clock: Clock,
    private val expectedManualInputMilliseconds: Long = DEFAULT_MANUAL_INPUT_MILLISECONDS,
) {
    private var current: List<AccountCodeView> = emptyList()

    fun refresh(): List<AccountCodeView> =
        store.codeViews(clock.nowEpochMilliseconds(), expectedManualInputMilliseconds).also { current = it }

    fun onForeground(): List<AccountCodeView> = refresh()

    fun onSystemTimeChanged(): List<AccountCodeView> = refresh()

    fun refreshIfPresentationChanged(): List<AccountCodeView>? {
        val now = clock.nowEpochMilliseconds()
        if (current.isEmpty() || current.any { now / it.periodMilliseconds != it.counter }) return refresh()
        var changed = false
        val updated = current.map { account ->
            val selection = CodeSelectionPolicy.selectForManualInput(
                nowEpochMilliseconds = now,
                periodMilliseconds = account.periodMilliseconds,
                expectedManualInputMilliseconds = account.expectedManualInputMilliseconds,
                currentCode = account.currentCode,
                nextCode = account.nextCode,
            )
            if (selection.position == account.manualRecommendation) account else {
                changed = true
                account.copy(
                    manualRecommendation = selection.position,
                    recommendedManualCode = selection.code,
                )
            }
        }
        if (!changed) return null
        current = updated
        return updated
    }

    fun refreshIfCounterChanged(): List<AccountCodeView>? {
        val now = clock.nowEpochMilliseconds()
        return if (current.isEmpty() || current.any { now / it.periodMilliseconds != it.counter }) refresh() else null
    }

    fun millisecondsUntilNextCheck(maximumDelay: Long = 1_000L): Long {
        val now = clock.nowEpochMilliseconds()
        val nextBoundary = current.minOfOrNull { account ->
            val remaining = account.periodMilliseconds - now % account.periodMilliseconds
            val recommendationChange = if (account.manualRecommendation == ManualCodePosition.CURRENT) {
                (remaining - account.expectedManualInputMilliseconds).coerceAtLeast(0L)
            } else {
                remaining
            }
            minOf(remaining, recommendationChange)
        } ?: maximumDelay
        return nextBoundary.coerceIn(16L, maximumDelay)
    }
}
