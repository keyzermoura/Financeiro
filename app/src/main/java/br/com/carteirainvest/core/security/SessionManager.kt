package br.com.carteirainvest.core.security

import br.com.carteirainvest.domain.model.User
import br.com.carteirainvest.domain.model.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the authenticated session only inside [SecurePreferences]. */
@Singleton
class SessionManager @Inject constructor(
    private val prefs: SecurePreferences,
) {
    private val _session = MutableStateFlow(readSession())
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    fun start(user: User) {
        prefs.putString(KEY_USER_ID, user.id)
        prefs.putString(KEY_NAME, user.name)
        prefs.putString(KEY_EMAIL, user.email)
        prefs.putBoolean(KEY_BIOMETRIC, false)
        _session.value = UserSession(
            userId = user.id,
            name = user.name,
            email = user.email,
            biometricEnabled = false,
        )
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_BIOMETRIC, enabled)
        _session.value = _session.value?.copy(biometricEnabled = enabled)
    }

    fun clear() {
        prefs.remove(KEY_USER_ID)
        prefs.remove(KEY_NAME)
        prefs.remove(KEY_EMAIL)
        prefs.remove(KEY_BIOMETRIC)
        _session.value = null
    }

    private fun readSession(): UserSession? {
        val userId = prefs.getString(KEY_USER_ID) ?: return null
        val name = prefs.getString(KEY_NAME).orEmpty()
        val email = prefs.getString(KEY_EMAIL).orEmpty()
        return UserSession(
            userId = userId,
            name = name,
            email = email,
            biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false),
        )
    }

    private companion object {
        const val KEY_USER_ID = "session_user_id"
        const val KEY_NAME = "session_user_name"
        const val KEY_EMAIL = "session_user_email"
        const val KEY_BIOMETRIC = "session_biometric_enabled"
    }
}

/** Remembers the last selected wallet between sessions on this device. */
@Singleton
class WalletSelectionManager @Inject constructor(
    private val prefs: SecurePreferences,
) {
    private val _selectedWalletId = MutableStateFlow(prefs.getString(KEY_SELECTED_WALLET))
    val selectedWalletId: StateFlow<String?> = _selectedWalletId.asStateFlow()

    fun select(walletId: String?) {
        if (walletId == null) prefs.remove(KEY_SELECTED_WALLET)
        else prefs.putString(KEY_SELECTED_WALLET, walletId)
        _selectedWalletId.value = walletId
    }

    fun clear() {
        select(null)
    }

    private companion object {
        const val KEY_SELECTED_WALLET = "selected_wallet_id"
    }
}
