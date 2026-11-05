package br.com.carteirainvest.core.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuth {

    const val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK

    data class Availability(val available: Boolean, val message: String)

    fun availability(context: Context): Availability {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(ALLOWED_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                Availability(true, "Biometria disponível")
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                Availability(false, "Este aparelho não possui leitor biométrico.")
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                Availability(false, "O leitor biométrico está indisponível no momento.")
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                Availability(false, "Cadastre uma digital ou reconhecimento facial nas configurações do aparelho.")
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                Availability(false, "Atualize a segurança do aparelho para usar biometria.")
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
                Availability(false, "Não foi possível verificar a biometria do aparelho.")
            else -> Availability(false, "Biometria indisponível neste aparelho.")
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeText: String = "Usar senha",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {},
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        onCancel()
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometria não reconhecida. Tente novamente.")
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .setNegativeButtonText(negativeText)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(promptInfo)
    }

    val supportsModernBiometrics: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
}

fun Activity.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    else -> null
}

fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
