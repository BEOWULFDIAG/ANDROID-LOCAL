package com.mjolnir.terminal

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEYSTORE_ALIAS = "mjolnir_api_key"
private const val PREFS_NAME = "mjolnir_secure"
private const val KEY_ENCRYPTED = "api_key_enc"
private const val KEY_IV = "api_key_iv"
private const val AES_MODE = "AES/GCM/NoPadding"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"

class ApiKeyStore(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(apiKey: String) {
        val cipher = Cipher.getInstance(AES_MODE).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_ENCRYPTED, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun load(): String? {
        val enc = prefs.getString(KEY_ENCRYPTED, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            val cipher = Cipher.getInstance(AES_MODE).apply { init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec) }
            cipher.doFinal(Base64.decode(enc, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        } catch (e: Exception) { null }
    }

    fun clear() = prefs.edit().remove(KEY_ENCRYPTED).remove(KEY_IV).apply()

    fun exists(): Boolean = prefs.contains(KEY_ENCRYPTED)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }
}
