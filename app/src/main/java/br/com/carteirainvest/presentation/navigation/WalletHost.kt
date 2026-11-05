package br.com.carteirainvest.presentation.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.SnackbarHostState
import br.com.carteirainvest.domain.model.Wallet

@Immutable
data class WalletHostState(
    val userName: String,
    val userEmail: String,
    val wallets: List<Wallet>,
    val selectedWallet: Wallet?,
    val isOnline: Boolean,
    val onSelectWallet: (String) -> Unit,
    val onCreateWallet: (String, String) -> Unit,
    val onUpdateWallet: (Wallet, String, String) -> Unit,
    val onDeleteWallet: (String) -> Unit,
)

val LocalWalletHost = staticCompositionLocalOf<WalletHostState?> { null }
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }
