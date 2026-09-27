package com.otpulse.presentation

import com.otpulse.core.lifecycle.AppLifecycleController
import com.otpulse.core.time.FakeClock
import com.otpulse.persistence.AccountRepository
import com.otpulse.persistence.InMemoryAccountMetadataStore
import com.otpulse.persistence.InMemorySecretStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeListPresenterTest {
    @Test
    fun foregroundRefreshIsFreshBeforeAnyPeriodicTick() {
        val fixture = fixture(12 * 60 * 60 * 1_000L + 5_000L)
        fixture.presenter.refresh()

        listOf(20_000L, 30_000L, 30 * 60_000L, 4 * 60 * 60_000L).forEach { backgroundDuration ->
            fixture.clock.epochMilliseconds = 12 * 60 * 60 * 1_000L + 5_000L + backgroundDuration

            val resumed = fixture.presenter.onForeground().single()
            val expected = fixture.store.codeViews(fixture.clock.epochMilliseconds).single()

            assertEquals(expected.currentCode, resumed.currentCode, "background=$backgroundDuration")
            assertEquals(expected.nextCode, resumed.nextCode, "background=$backgroundDuration")
            assertEquals(expected.counter, resumed.counter, "background=$backgroundDuration")
            assertEquals(expected.remainingMilliseconds, resumed.remainingMilliseconds, "background=$backgroundDuration")
        }
    }

    @Test
    fun systemClockJumpsForwardAndBackwardRefreshImmediately() {
        val fixture = fixture(12 * 60 * 60 * 1_000L + 5_000L)
        fixture.presenter.refresh()

        listOf(
            12 * 60 * 60 * 1_000L + 37 * 60_000L + 14_000L,
            8 * 60 * 60 * 1_000L + 2_000L,
        ).forEach { changedTime ->
            fixture.clock.epochMilliseconds = changedTime

            val refreshed = fixture.presenter.onSystemTimeChanged().single()
            val expected = fixture.store.codeViews(changedTime).single()

            assertEquals(expected.currentCode, refreshed.currentCode)
            assertEquals(expected.nextCode, refreshed.nextCode)
            assertEquals(expected.counter, refreshed.counter)
            assertEquals(expected.remainingMilliseconds, refreshed.remainingMilliseconds)
        }
    }

    @Test
    fun passiveCheckMutatesSnapshotOnlyAfterCounterChange() {
        val fixture = fixture(5_000L)
        fixture.presenter.refresh()

        fixture.clock.epochMilliseconds = 29_999L
        assertNull(fixture.presenter.refreshIfCounterChanged())

        fixture.clock.epochMilliseconds = 30_000L
        val refreshed = assertNotNull(fixture.presenter.refreshIfCounterChanged())
        assertEquals(1L, refreshed.single().counter)
    }

    @Test
    fun lifecycleControllerRetainsRefreshEventsUntilUiObservesThem() {
        val lifecycle = AppLifecycleController()

        lifecycle.onForeground()
        assertTrue(lifecycle.state.value.isForeground)
        assertEquals(1L, lifecycle.state.value.refreshRevision)

        lifecycle.onForeground()
        assertEquals(1L, lifecycle.state.value.refreshRevision)

        lifecycle.onBackground()
        assertEquals(false, lifecycle.state.value.isForeground)

        lifecycle.onSystemTimeChanged()
        assertEquals(2L, lifecycle.state.value.refreshRevision)
    }

    @Test
    fun manualRecommendationChangesAtLeadTimeWithoutRegeneratingCounter() {
        val fixture = fixture(24_000L)
        val initial = fixture.presenter.refresh().single()
        assertEquals(com.otpulse.timeshift.ManualCodePosition.CURRENT, initial.manualRecommendation)
        assertEquals(1_000L, fixture.presenter.millisecondsUntilNextCheck())

        fixture.clock.epochMilliseconds = 25_000L
        val updated = assertNotNull(fixture.presenter.refreshIfPresentationChanged()).single()

        assertEquals(initial.counter, updated.counter)
        assertEquals(initial.currentCode, updated.currentCode)
        assertEquals(com.otpulse.timeshift.ManualCodePosition.NEXT, updated.manualRecommendation)
    }

    private fun fixture(initialTime: Long): Fixture {
        val repository = AccountRepository(InMemoryAccountMetadataStore(), InMemorySecretStore())
        val store = AuthenticatorStore(repository)
        val result = store.addManual("Lifecycle", "test@example.com", "JBSWY3DPEHPK3PXP", "SHA1", "6", "30")
        check(result is AddAccountResult.Success)
        val clock = FakeClock(initialTime)
        return Fixture(clock, store, CodeListPresenter(store, clock))
    }

    private data class Fixture(
        val clock: FakeClock,
        val store: AuthenticatorStore,
        val presenter: CodeListPresenter,
    )
}
