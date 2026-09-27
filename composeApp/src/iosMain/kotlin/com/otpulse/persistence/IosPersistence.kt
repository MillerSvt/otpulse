package com.otpulse.persistence

import cnames.structs.__CFData
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.get
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSUUID
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecDuplicateItem
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class)
class IosKeychainSecretStore : SecretStore {
    internal var lastStatus: Int? = null
        private set

    override fun put(reference: String, secret: ByteArray): StorageResult<Unit> {
        return withBaseQuery(reference) { query ->
            val data = secret.usePinned { pinned ->
                CFDataCreate(null, if (secret.isEmpty()) null else pinned.addressOf(0).reinterpret(), secret.size.toLong())
            } ?: return@withBaseQuery StorageResult.Failure(StorageError.SECURE_STORAGE_UNAVAILABLE)
            try {
                CFDictionarySetValue(query, kSecValueData, data)
                CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                val status = SecItemAdd(query, null).also { lastStatus = it }
                when (status) {
                    errSecSuccess -> StorageResult.Success(Unit)
                    errSecDuplicateItem -> StorageResult.Failure(StorageError.DUPLICATE)
                    else -> StorageResult.Failure(StorageError.SECURE_STORAGE_UNAVAILABLE)
                }
            } finally {
                CFRelease(data)
            }
        }
    }

    override fun get(reference: String): StorageResult<ByteArray> = withBaseQuery(reference) { query ->
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<COpaquePointerVar>()
            val status = SecItemCopyMatching(query, result.ptr).also { lastStatus = it }
            when (status) {
                errSecItemNotFound -> StorageResult.Failure(StorageError.NOT_FOUND)
                errSecSuccess -> {
                    val raw = result.value ?: return@memScoped StorageResult.Failure(StorageError.CORRUPT_DATA)
                    try {
                        val data = raw.reinterpret<__CFData>()
                        val size = CFDataGetLength(data).toInt()
                        val bytes = CFDataGetBytePtr(data)
                        if (size < 0 || (size > 0 && bytes == null)) StorageResult.Failure(StorageError.CORRUPT_DATA)
                        else StorageResult.Success(ByteArray(size) { index -> bytes!![index].toByte() })
                    } finally {
                        CFRelease(raw)
                    }
                }
                else -> StorageResult.Failure(StorageError.SECURE_STORAGE_UNAVAILABLE)
            }
        }
    }

    override fun contains(reference: String): StorageResult<Boolean> = withBaseQuery(reference) { query ->
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val status = SecItemCopyMatching(query, null).also { lastStatus = it }
        when (status) {
            errSecSuccess -> StorageResult.Success(true)
            errSecItemNotFound -> StorageResult.Success(false)
            else -> StorageResult.Failure(StorageError.SECURE_STORAGE_UNAVAILABLE)
        }
    }

    override fun delete(reference: String): StorageResult<Unit> = withBaseQuery(reference) { query ->
        val status = SecItemDelete(query).also { lastStatus = it }
        when (status) {
            errSecSuccess, errSecItemNotFound -> StorageResult.Success(Unit)
            else -> StorageResult.Failure(StorageError.SECURE_STORAGE_UNAVAILABLE)
        }
    }

    private inline fun <T> withBaseQuery(reference: String, block: (platform.CoreFoundation.CFMutableDictionaryRef?) -> T): T {
        val query = CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        val service = CFStringCreateWithCString(null, SERVICE, kCFStringEncodingUTF8)
        val account = CFStringCreateWithCString(null, reference, kCFStringEncodingUTF8)
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, service)
            CFDictionarySetValue(query, kSecAttrAccount, account)
            return block(query)
        } finally {
            if (account != null) CFRelease(account)
            if (service != null) CFRelease(service)
            if (query != null) CFRelease(query)
        }
    }

    private companion object {
        const val SERVICE = "com.otpulse.app.totp-secrets"
    }
}

object IosPersistence {
    fun repository(): AccountRepository {
        val metadata = FileAccountMetadataStore(
            FileSystem.SYSTEM,
            (NSHomeDirectory() + "/Library/Application Support/OTPulse/accounts.db").toPath(),
        )
        return AccountRepository(metadata, IosKeychainSecretStore())
    }
}

/** Repeatable app-host diagnostic because command-line iOS test executables have no Keychain. */
fun runIosKeychainSelfTest(): Int {
    val reference = "app-host-${NSUUID().UUIDString}"
    val expected = "otpulse-keychain-self-test".encodeToByteArray()
    val store = IosKeychainSecretStore()
    return try {
        if (store.put(reference, expected) !is StorageResult.Success) return store.lastStatus ?: -1
        val reader = IosKeychainSecretStore()
        val restored = (reader.get(reference) as? StorageResult.Success)?.value ?: return reader.lastStatus ?: -2
        val matches = restored.contentEquals(expected)
        restored.fill(0)
        val removed = store.delete(reference) is StorageResult.Success
        val absent = (store.contains(reference) as? StorageResult.Success)?.value == false
        when {
            !matches -> -3
            !removed -> store.lastStatus ?: -4
            !absent -> store.lastStatus ?: -5
            else -> 0
        }
    } finally {
        store.delete(reference)
        expected.fill(0)
    }
}
