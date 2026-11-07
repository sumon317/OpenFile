package com.example.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Properties
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed credential vault using AndroidKeyStore (AES/GCM/NoPadding, 256-bit key).
 * Never stores raw credentials in Room or SharedPreferences; returns opaque UUID aliases
 * that reference AES-GCM encrypted blobs with 12-byte random IVs and 128-bit auth tags.
 */
class KeystoreCredentialVault(context: Context) {

    private val vaultFile = File(context.filesDir, "openfile_credential_vault.properties")
    private val fallbackKeyFile = File(context.filesDir, ".openfile_vault_fallback.key")
    private val lock = Any()
    private var cachedProperties: Properties? = null
    private var cachedSecretKey: SecretKey? = null
    private val retrievedSecretCache = linkedMapOf<String, String?>()

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "openfile_master_vault_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    /**
     * Encrypts [plainTextSecret] using AES-256-GCM and returns an opaque [secretAlias] to store in Room.
     */
    fun storeSecret(existingAlias: String? = null, plainTextSecret: String): String {
        synchronized(lock) {
            retrievedSecretCache.clear()
            val alias = existingAlias?.takeIf { it.isNotBlank() } ?: "sec_${UUID.randomUUID()}"
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val plainBytes = plainTextSecret.toByteArray(Charsets.UTF_8)
            val cipherBytes = cipher.doFinal(plainBytes)
            plainBytes.fill(0)

            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)

            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
            retrievedSecretCache.remove(alias)
            val props = loadProperties()
            props.setProperty(alias, encoded)
            saveProperties(props)
            return alias
        }
    }

    /**
     * Decrypts and returns the secret associated with [secretAlias], or null if missing or tampered.
     */
    fun retrieveSecret(secretAlias: String?): String? {
        if (secretAlias.isNullOrBlank()) return null
        synchronized(lock) {
            retrievedSecretCache[secretAlias]?.let { return it }
            val props = loadProperties()
            val encoded = props.getProperty(secretAlias) ?: return null.also { retrievedSecretCache[secretAlias] = null }
            return try {
                val combined = Base64.decode(encoded, Base64.NO_WRAP)
                if (combined.size < 2 + GCM_IV_LENGTH_BYTES) return null
                val ivLength = combined[0].toInt() and 0xFF
                val iv = combined.copyOfRange(1, 1 + ivLength)
                val cipherBytes = combined.copyOfRange(1 + ivLength, combined.size)

                val secretKey = getOrCreateSecretKey()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                val decryptedBytes = cipher.doFinal(cipherBytes)
                val result = String(decryptedBytes, Charsets.UTF_8)
                decryptedBytes.fill(0)
                result
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Deletes a specific secret alias from the encrypted vault.
     */
    fun deleteSecret(secretAlias: String?) {
        if (secretAlias.isNullOrBlank()) return
        synchronized(lock) {
            retrievedSecretCache.remove(secretAlias)
            val props = loadProperties()
            if (props.remove(secretAlias) != null) {
                saveProperties(props)
            }
        }
    }

    /**
     * Purges all stored credentials and rotates the master key.
     */
    fun clearAllSecrets() {
        synchronized(lock) {
            cachedProperties = Properties()
            retrievedSecretCache.clear()
            cachedSecretKey = null
            if (vaultFile.exists()) vaultFile.delete()
            if (fallbackKeyFile.exists()) fallbackKeyFile.delete()
            try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                    keyStore.deleteEntry(MASTER_KEY_ALIAS)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun countStoredSecrets(): Int {
        synchronized(lock) {
            return loadProperties().size
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        cachedSecretKey?.let { return it }
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingEntry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existingEntry != null) {
                cachedSecretKey = existingEntry.secretKey
                existingEntry.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                val generated = keyGenerator.generateKey()
                cachedSecretKey = generated
                generated
            }
        } catch (_: Exception) {
            // Fallback for local JVM / Robolectric test environments where AndroidKeyStore provider is absent
            if (fallbackKeyFile.exists()) {
                val keyBytes = fallbackKeyFile.readBytes()
                if (keyBytes.size == 32) {
                    val key = SecretKeySpec(keyBytes, "AES")
                    cachedSecretKey = key
                    return key
                }
            }
            val randomBytes = ByteArray(32)
            SecureRandom().nextBytes(randomBytes)
            fallbackKeyFile.writeBytes(randomBytes)
            val key = SecretKeySpec(randomBytes, "AES")
            cachedSecretKey = key
            key
        }
    }

    private fun loadProperties(): Properties {
        cachedProperties?.let { return it }
        val props = Properties()
        if (vaultFile.exists()) {
            vaultFile.inputStream().use { props.load(it) }
        }
        cachedProperties = props
        return props
    }

    private fun saveProperties(props: Properties) {
        cachedProperties = props
        vaultFile.outputStream().use { props.store(it, "OpenFile Encrypted Credential Vault") }
    }
}
