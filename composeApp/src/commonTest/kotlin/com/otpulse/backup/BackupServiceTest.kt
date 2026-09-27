package com.otpulse.backup

import com.otpulse.persistence.AccountRepository
import com.otpulse.persistence.InMemoryAccountMetadataStore
import com.otpulse.persistence.InMemorySecretStore
import com.otpulse.persistence.StorageResult
import com.otpulse.presentation.AuthenticatorStore
import com.otpulse.totp.Base32
import com.otpulse.totp.Base32Result
import com.otpulse.totp.TotpAlgorithm
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupServiceTest {
    @Test
    fun base32EncodingRoundTripsArbitraryBytes() {
        listOf(
            byteArrayOf(0),
            byteArrayOf(0xff.toByte(), 0x10, 0x00, 0x7f),
            "Hello!\u0000secure".encodeToByteArray(),
        ).forEach { original ->
            val decoded = assertIs<Base32Result.Success>(Base32.decode(Base32.encode(original))).bytes
            assertContentEquals(original, decoded)
        }
    }

    @Test
    fun exportWipeImportProducesSameCodes() {
        val repository = repository()
        createFixtures(repository)
        val before = AuthenticatorStore(repository).codeViews(FIXED_TIME).map { it.identity() }

        val exported = assertIs<BackupExportResult.Success>(BackupService(repository).exportText())
        assertEquals(2, exported.accountCount)
        assertTrue(exported.text.lineSequence().filter { it.isNotBlank() }.all { it.startsWith("otpauth://totp/") })

        val existing = assertIs<StorageResult.Success<List<com.otpulse.core.model.TotpAccount>>>(repository.accounts()).value
        existing.forEach { assertIs<StorageResult.Success<Unit>>(repository.delete(it.id)) }
        assertTrue(assertIs<StorageResult.Success<List<com.otpulse.core.model.TotpAccount>>>(repository.accounts()).value.isEmpty())

        val preview = assertIs<BackupPreviewResult.Success>(BackupService(repository).preview(exported.text)).preview
        assertEquals(2, preview.importCount)
        assertEquals(0, preview.duplicateCount)
        val restored = assertIs<BackupRestoreResult.Success>(BackupService(repository).restore(preview))
        assertEquals(2, restored.importedCount)
        assertEquals(0, restored.skippedDuplicateCount)
        assertEquals(before, AuthenticatorStore(repository).codeViews(FIXED_TIME).map { it.identity() })
    }

    @Test
    fun previewMarksDuplicatesAndRestoreSkipsThem() {
        val repository = repository()
        createFixtures(repository)
        val service = BackupService(repository)
        val exported = assertIs<BackupExportResult.Success>(service.exportText()).text
        val firstUri = exported.lineSequence().first()

        val preview = assertIs<BackupPreviewResult.Success>(service.preview(exported + firstUri + "\n")).preview

        assertEquals(0, preview.newCount)
        assertEquals(3, preview.duplicateCount)
        val result = assertIs<BackupRestoreResult.Success>(service.restore(preview))
        assertEquals(0, result.importedCount)
        assertEquals(3, result.skippedDuplicateCount)
        assertEquals(2, assertIs<StorageResult.Success<List<com.otpulse.core.model.TotpAccount>>>(repository.accounts()).value.size)
    }

    @Test
    fun previewRejectsMalformedInputBeforeWritingAnything() {
        val repository = repository()
        val result = BackupService(repository).preview(
            "otpauth://totp/Valid?secret=JBSWY3DPEHPK3PXP\nnot-an-otpauth-uri\n"
        )

        assertIs<BackupPreviewResult.Failure>(result)
        assertTrue(assertIs<StorageResult.Success<List<com.otpulse.core.model.TotpAccount>>>(repository.accounts()).value.isEmpty())
    }

    @Test
    fun importsJsonArrayAndIgnoresUnusedAndUnknownFields() {
        val repository = repository()
        val json = """
            [
              {
                "id": "external-1",
                "accountName": "alice@example.com",
                "issuer": "Example",
                "secret": "JBSW Y3DP-EHPK3PXP",
                "algorithm": "sha-256",
                "digits": 8,
                "period": 60,
                "image": null,
                "unused": {"nested": [true, false, 12.5]}
              },
              {
                "id": "external-2",
                "accountName": "Алиса \"телефон\"",
                "issuer": "Банк",
                "secret": "MFRGGZDFMZTWQ2LK",
                "algorithm": "SHA1",
                "digits": 6,
                "period": 30,
                "image": "ignored.png"
              }
            ]
        """.trimIndent()

        val preview = assertIs<BackupPreviewResult.Success>(BackupService(repository).preview(json)).preview
        assertEquals(2, preview.importCount)
        assertEquals("alice@example.com", preview.entries[0].account.accountName)
        assertEquals(TotpAlgorithm.SHA256, preview.entries[0].account.algorithm)
        assertEquals("Алиса \"телефон\"", preview.entries[1].account.accountName)

        val restored = assertIs<BackupRestoreResult.Success>(BackupService(repository).restore(preview))
        assertEquals(2, restored.importedCount)
        val accounts = assertIs<StorageResult.Success<List<com.otpulse.core.model.TotpAccount>>>(repository.accounts()).value
        assertEquals(listOf("Example", "Банк"), accounts.map { it.issuer })
        assertEquals(listOf(60, 30), accounts.map { it.periodSeconds })
    }

    @Test
    fun jsonDuplicatesAreReportedAndSkipped() {
        val repository = repository()
        val item = """{"id":"1","accountName":"alice","issuer":"Example","secret":"JBSWY3DPEHPK3PXP","algorithm":"SHA1","digits":6,"period":30,"image":null}"""
        val preview = assertIs<BackupPreviewResult.Success>(BackupService(repository).preview("[$item,$item,$item]")).preview

        assertEquals(3, preview.importCount)
        assertEquals(2, preview.duplicateCount)
        val restored = assertIs<BackupRestoreResult.Success>(BackupService(repository).restore(preview))
        assertEquals(1, restored.importedCount)
        assertEquals(2, restored.skippedDuplicateCount)
        assertEquals(1, assertIs<StorageResult.Success<List<com.otpulse.core.model.TotpAccount>>>(repository.accounts()).value.size)
    }

    @Test
    fun jsonImportAllowsMissingOrNullIssuerWhenAccountNameIsPresent() {
        val repository = repository()
        val json = """
            [
              {"accountName":"missing issuer","secret":"MFRGGZDFMZTWQ2LK","algorithm":"SHA1","digits":6,"period":30,"image":null},
              {"accountName":"null issuer","issuer":null,"secret":"KRSXG5DSNFXGOIDB","algorithm":"SHA1","digits":6,"period":30,"image":null}
            ]
        """.trimIndent()

        val preview = assertIs<BackupPreviewResult.Success>(BackupService(repository).preview(json)).preview
        assertEquals(2, preview.newCount)
        assertEquals(listOf("", ""), preview.entries.map { it.account.issuer })
        assertEquals(listOf("missing issuer", "null issuer"), preview.entries.map { it.account.accountName })

        val restored = assertIs<BackupRestoreResult.Success>(BackupService(repository).restore(preview))
        assertEquals(2, restored.importedCount)
        val accounts = assertIs<StorageResult.Success<List<com.otpulse.core.model.TotpAccount>>>(repository.accounts()).value
        assertEquals(listOf("", ""), accounts.map { it.issuer })
        assertEquals(listOf("missing issuer", "null issuer"), accounts.map { it.accountName })
    }

    @Test
    fun malformedJsonFailsBeforeWriting() {
        val repository = repository()
        val invalid = """[{"accountName":"alice","issuer":"Example","secret":"bad!","algorithm":"SHA1","digits":6,"period":30}]"""

        assertIs<BackupPreviewResult.Failure>(BackupService(repository).preview(invalid))
        assertTrue(assertIs<StorageResult.Success<List<com.otpulse.core.model.TotpAccount>>>(repository.accounts()).value.isEmpty())
    }

    @Test
    fun exportCollapsesLegacyAccountsWithTheSameSecret() {
        val metadata = InMemoryAccountMetadataStore()
        val secrets = InMemorySecretStore()
        val secret = "legacy-duplicate".encodeToByteArray()
        assertIs<StorageResult.Success<Unit>>(secrets.put("secret-a", secret))
        assertIs<StorageResult.Success<Unit>>(secrets.put("secret-b", secret))
        assertIs<StorageResult.Success<Unit>>(
            metadata.replace(
                listOf(
                    com.otpulse.persistence.AccountRecord(
                        com.otpulse.core.model.TotpAccount("a", "First", "alice", "secret-a", sortOrder = 0),
                        com.otpulse.persistence.RecordState.ACTIVE,
                    ),
                    com.otpulse.persistence.AccountRecord(
                        com.otpulse.core.model.TotpAccount("b", "Second", "bob", "secret-b", sortOrder = 1),
                        com.otpulse.persistence.RecordState.ACTIVE,
                    ),
                )
            )
        )

        val exported = assertIs<BackupExportResult.Success>(BackupService(AccountRepository(metadata, secrets)).exportText())

        assertEquals(1, exported.accountCount)
        assertEquals(1, exported.text.lineSequence().count { it.isNotBlank() })
        secret.fill(0)
    }

    private fun repository() = AccountRepository(
        InMemoryAccountMetadataStore(),
        InMemorySecretStore(),
    ) { "backup-${nextId++}" }

    private fun createFixtures(repository: AccountRepository) {
        createDuplicateFixture(repository, sortOrder = 0)
        assertIs<StorageResult.Success<com.otpulse.core.model.TotpAccount>>(
            repository.create("Банк", "телефон", byteArrayOf(1, 2, 3, 4, 5, 6, 7), TotpAlgorithm.SHA512, 8, 60, 1)
        )
    }

    private fun createDuplicateFixture(repository: AccountRepository, sortOrder: Int) {
        assertIs<StorageResult.Success<com.otpulse.core.model.TotpAccount>>(
            repository.create(
                "Example",
                "alice@example.com",
                "Hello!".encodeToByteArray(),
                TotpAlgorithm.SHA1,
                6,
                30,
                sortOrder,
            )
        )
    }

    private fun com.otpulse.presentation.AccountCodeView.identity() = listOf(
        issuer,
        accountName,
        currentCode,
        nextCode,
        periodMilliseconds.toString(),
    )

    private companion object {
        var nextId = 0
        const val FIXED_TIME = 1_700_000_000_000L
    }
}
