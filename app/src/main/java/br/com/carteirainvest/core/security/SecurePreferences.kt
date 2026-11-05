package br.com.carteirainvest.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of encrypted local preferences. If the Android Keystore entry is
 * invalidated (for example after a restore), the encrypted file is discarded and
 * recreated instead of silently falling back to plaintext.
 */
@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var prefs: SharedPreferences = open()

    fun getString(key: String): String? = guarded { prefs.getString(key, null) }

    fun putString(key: String, value: String?) {
        guarded {
            prefs.edit().apply {
                if (value == null) remove(key) else putString(key, value)
            }.apply()
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        guarded { prefs.getBoolean(key, defaultValue) } ?: defaultValue

    fun putBoolean(key: String, value: Boolean) {
        guarded { prefs.edit().putBoolean(key, value).apply() }
    }

    fun remove(key: String) {
        guarded { prefs.edit().remove(key).apply() }
    }

    fun clear() {
        guarded { prefs.edit().clear().apply() }
    }

    private fun open(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun <T> guarded(block: () -> T): T? {
        return try {
            block()
        } catch (_: Exception) {
            recover()
            null
        }
    }

    @Synchronized
    private fun recover() {
        runCatching { context.deleteSharedPreferences(FILE_NAME) }
        prefs = open()
    }

    companion object {
        private const val FILE_NAME = "carteira_invest_secure_prefs"
    }
}
