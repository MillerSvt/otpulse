package com.otpulse.persistence

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okio.FileSystem
import okio.Path.Companion.toPath
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreSecretStore(context: Context) : SecretStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun put(reference: String, secret: ByteArray): StorageResult<Unit> {
        return try {
            if (preferences.contains(reference)) return StorageResult.Failure(StorageError.DUPLICATE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
            val ciphertext = cipher.doFinal(secret)
            val payload = ByteArray(1 + cipher.iv.size + ciphertext.size)
            payload[0] = cipher.iv.size.toByte()
            cipher.iv.copyInto(payload, 1)
            ciphertext.copyInto(payload, 1 + cipher.iv.size)
            if (preferences.edit().putString(reference, Base64.encodeToString(payload, Base64.NO_WRAP)).commit()) {
                StorageResult.Success(Unit)
            } else {
                StorageResult.Failure(StorageError.IO)
            }
        } catch (_: Throwable) {
            StorageResult.Failure(StorageError.SECURE_STORAGE_UNAVAILABLE)
        }
    }

    override fun get(reference: String): StorageResult<ByteArray> {
        return try {
            val encoded = preferences.getString(reference, null) ?: return StorageResult.Failure(StorageError.NOT_FOUND)
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.isEmpty()) return StorageResult.Failure(StorageError.CORRUPT_DATA)
            val ivSize = payload[0].toInt() and 0xff
            if (ivSize !in 12..16 || payload.size <= 1 + ivSize) return StorageResult.Failure(StorageError.CORRUPT_DATA)
            val iv = payload.copyOfRange(1, 1 + ivSize)
            val ciphertext = payload.copyOfRange(1 + ivSize, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, iv))
            StorageResult.Success(cipher.doFinal(ciphertext))
        } catch (_: Throwable) {
            StorageResult.Failure(StorageError.SECURE_STORAGE_UNAVAILABLE)
        }
    }

    override fun contains(reference: String): StorageResult<Boolean> = try {
        StorageResult.Success(preferences.contains(reference))
    } catch (_: Throwable) {
        StorageResult.Failure(StorageError.SECURE_STORAGE_UNAVAILABLE)
    }

    override fun delete(reference: String): StorageResult<Unit> = try {
        if (preferences.edit().remove(reference).commit()) StorageResult.Success(Unit)
        else StorageResult.Failure(StorageError.IO)
    } catch (_: Throwable) {
        StorageResult.Failure(StorageError.SECURE_STORAGE_UNAVAILABLE)
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "otpulse_secure_secrets"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "otpulse.totp.secret.aes.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

object AndroidPersistence {
    fun repository(context: Context): AccountRepository {
        val appContext = context.applicationContext
        val metadata = FileAccountMetadataStore(
            FileSystem.SYSTEM,
            (appContext.filesDir.absolutePath + "/persistence/accounts.db").toPath(),
        )
        return AccountRepository(metadata, AndroidKeystoreSecretStore(appContext))
    }
}
