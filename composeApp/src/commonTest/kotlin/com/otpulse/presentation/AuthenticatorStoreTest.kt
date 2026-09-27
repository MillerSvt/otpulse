package com.otpulse.presentation

import com.otpulse.persistence.AccountRepository
import com.otpulse.persistence.InMemoryAccountMetadataStore
import com.otpulse.persistence.InMemorySecretStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.otpulse.timeshift.ManualCodePosition

class AuthenticatorStoreTest {
    @Test
    fun manualAccountBecomesUsableWithoutExposingSecret() {
        val store = testStore()
        val before = store.codeViews(59_000).size

        assertIs<AddAccountResult.Success>(
            store.addManual(
                issuer = "Example",
                accountName = "alice@example.com",
                secret = "JBSW Y3DP-EHPK3PXP",
                algorithm = "SHA256",
                digits = "8",
                period = "60",
            )
        )

        val added = store.codeViews(59_000).last()
        assertEquals(before + 1, store.codeViews(59_000).size)
        assertEquals("Example", added.issuer)
        assertEquals(8, added.currentCode.length)
        assertTrue(added.remainingMilliseconds in 1..60_000)
    }

    @Test
    fun invalidManualAccountDoesNotMutateStore() {
        val store = testStore()
        val before = store.codeViews(0).size
        assertIs<AddAccountResult.Failure>(
            store.addManual("Example", "alice", "not-base32!", "SHA1", "6", "30")
        )
        assertEquals(before, store.codeViews(0).size)
    }

    @Test
    fun importsOtpAuthUri() {
        val store = testStore()
        assertIs<AddAccountResult.Success>(
            store.importUri("otpauth://totp/Example:bob?secret=JBSWY3DPEHPK3PXP&issuer=Example")
        )
        assertEquals("bob", store.codeViews(0).last().accountName)
    }

    @Test
    fun classifiesAndImportsOtpAuthInput() {
        val store = testStore()

        assertIs<AccountInputResult.Imported>(
            store.consumeAccountInput("otpauth://totp/Example:bob?secret=JBSWY3DPEHPK3PXP&issuer=Example")
        )

        assertEquals("bob", store.codeViews(0).single().accountName)
    }

    @Test
    fun qrAndManualFlowsRejectAnExistingSecret() {
        val store = testStore()
        assertIs<AccountInputResult.Imported>(
            store.consumeAccountInput("otpauth://totp/Example:alice?secret=JBSWY3DPEHPK3PXP&issuer=Example")
        )

        val qrDuplicate = assertIs<AccountInputResult.Failure>(
            store.consumeAccountInput("otpauth://totp/Other:bob?secret=JBSWY3DPEHPK3PXP&issuer=Other")
        )
        val manualDuplicate = assertIs<AddAccountResult.Failure>(
            store.addManual("Manual", "carol", "JBSWY3DPEHPK3PXP", "SHA512", "8", "60")
        )

        assertTrue(qrDuplicate.message.contains("существует"))
        assertTrue(manualDuplicate.message.contains("существует"))
        assertEquals(1, store.codeViews(0).size)
    }

    @Test
    fun classifiesRawBase32SecretWithoutPersistingIt() {
        val store = testStore()

        val result = assertIs<AccountInputResult.NeedsDetails>(
            store.consumeAccountInput("VHXONY27PLEFUQJVATTSQLEDZ67Y7U7K")
        )

        assertEquals("VHXONY27PLEFUQJVATTSQLEDZ67Y7U7K", result.normalizedSecret)
        assertTrue(store.codeViews(0).isEmpty())
    }

    @Test
    fun normalizesFormattedBase32InputBeforeRequestingDetails() {
        val store = testStore()

        val result = assertIs<AccountInputResult.NeedsDetails>(
            store.consumeAccountInput("vhxo ny27-plef uqjv atts qled z67y 7u7k")
        )

        assertEquals("VHXONY27PLEFUQJVATTSQLEDZ67Y7U7K", result.normalizedSecret)
    }

    @Test
    fun rejectsUnknownAccountInputWithoutMutation() {
        val store = testStore()

        assertIs<AccountInputResult.Failure>(store.consumeAccountInput("not a secret!"))

        assertTrue(store.codeViews(0).isEmpty())
    }

    @Test
    fun editsAccountMetadataWithoutReplacingSecret() {
        val store = testStore()
        assertIs<AddAccountResult.Success>(
            store.addManual("Example", "alice", "JBSWY3DPEHPK3PXP", "SHA1", "6", "30")
        )
        val accountId = store.codeViews(0).single().id

        assertIs<AccountMutationResult.Success>(
            store.updateAccount(accountId, "Renamed", "bob", "SHA256", "8", "60")
        )

        val editable = assertIs<com.otpulse.persistence.StorageResult.Success<EditableAccount>>(
            store.editableAccount(accountId)
        ).value
        assertEquals("Renamed", editable.issuer)
        assertEquals("bob", editable.accountName)
        assertEquals("SHA256", editable.algorithm)
        assertEquals("8", editable.digits)
        assertEquals("60", editable.period)
        assertEquals(8, store.codeViews(0).single().currentCode.length)
    }

    @Test
    fun invalidEditLeavesAccountUnchanged() {
        val store = testStore()
        assertIs<AddAccountResult.Success>(
            store.addManual("Example", "alice", "JBSWY3DPEHPK3PXP", "SHA1", "6", "30")
        )
        val before = store.codeViews(0).single()

        assertIs<AccountMutationResult.Failure>(
            store.updateAccount(before.id, "", "", "SHA1", "6", "30")
        )

        assertEquals(before.issuer, store.codeViews(0).single().issuer)
        assertEquals(before.accountName, store.codeViews(0).single().accountName)
    }

    @Test
    fun deleteRemovesAccountFromAuthenticator() {
        val store = testStore()
        assertIs<AddAccountResult.Success>(
            store.addManual("Example", "alice", "JBSWY3DPEHPK3PXP", "SHA1", "6", "30")
        )
        val accountId = store.codeViews(0).single().id

        assertIs<AccountMutationResult.Success>(store.deleteAccount(accountId))

        assertTrue(store.codeViews(0).isEmpty())
        assertIs<AccountMutationResult.Failure>(store.deleteAccount(accountId))
    }

    @Test
    fun manualRecommendationUsesGlobalSettingAndPerAccountOverride() {
        val store = testStore()
        assertIs<AddAccountResult.Success>(
            store.addManual("Example", "alice", "JBSWY3DPEHPK3PXP", "SHA1", "6", "30")
        )
        val account = store.codeViews(26_000L, expectedManualInputMilliseconds = 5_000L).single()
        assertEquals(ManualCodePosition.NEXT, account.manualRecommendation)
        assertEquals(account.nextCode, account.recommendedManualCode)

        assertIs<AccountMutationResult.Success>(
            store.updateAccount(account.id, "Example", "alice", "SHA1", "6", "30", "1000")
        )

        val overridden = store.codeViews(26_000L, expectedManualInputMilliseconds = 5_000L).single()
        assertEquals(ManualCodePosition.CURRENT, overridden.manualRecommendation)
        assertEquals(1_000L, overridden.expectedManualInputMilliseconds)
        assertTrue(overridden.usesManualInputOverride)
        assertEquals("1000", assertIs<com.otpulse.persistence.StorageResult.Success<EditableAccount>>(
            store.editableAccount(account.id)
        ).value.manualInputOverrideMilliseconds)
    }

    @Test
    fun bluetoothSnapshotAlwaysUsesCurrentAtRequestedWallClockTime() {
        val store = testStore()
        assertIs<AddAccountResult.Success>(
            store.addManual("Example", "alice", "JBSWY3DPEHPK3PXP", "SHA1", "6", "30")
        )
        val account = store.codeViews(29_999L).single()

        val beforeBoundary = requireNotNull(store.bluetoothSnapshot(account.id, 29_999L))
        val atBoundary = requireNotNull(store.bluetoothSnapshot(account.id, 30_000L))

        assertEquals(account.currentCode, beforeBoundary.code)
        assertEquals(account.counter, beforeBoundary.counter)
        assertEquals(account.nextCode, atBoundary.code)
        assertEquals(account.counter + 1L, atBoundary.counter)
        assertEquals(30_000L, atBoundary.capturedAtEpochMilliseconds)
    }

    @Test
    fun invalidManualInputOverrideDoesNotMutateAccount() {
        val store = testStore()
        assertIs<AddAccountResult.Success>(
            store.addManual("Example", "alice", "JBSWY3DPEHPK3PXP", "SHA1", "6", "30")
        )
        val account = store.codeViews(0L).single()

        assertIs<AccountMutationResult.Failure>(
            store.updateAccount(account.id, "Example", "alice", "SHA1", "6", "30", "-1")
        )
        assertIs<AccountMutationResult.Failure>(
            store.updateAccount(account.id, "Example", "alice", "SHA1", "6", "30", "5001")
        )

        val editable = assertIs<com.otpulse.persistence.StorageResult.Success<EditableAccount>>(
            store.editableAccount(account.id)
        ).value
        assertEquals("", editable.manualInputOverrideMilliseconds)
    }

    private fun testStore() = AuthenticatorStore(
        AccountRepository(InMemoryAccountMetadataStore(), InMemorySecretStore())
    )
}
