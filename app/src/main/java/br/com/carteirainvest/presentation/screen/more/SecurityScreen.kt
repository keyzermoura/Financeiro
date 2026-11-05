package br.com.carteirainvest.presentation.screen.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.carteirainvest.core.security.BiometricAuth
import br.com.carteirainvest.core.security.findFragmentActivity
import br.com.carteirainvest.presentation.components.AppCard
import br.com.carteirainvest.presentation.components.CarteiraTopBar
import br.com.carteirainvest.presentation.components.InfoBanner
import br.com.carteirainvest.presentation.components.LabeledValueRow
import br.com.carteirainvest.presentation.components.ObserveMessages

@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    onLock: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val availability = remember(context) { BiometricAuth.availability(context) }
    var promptError by remember { mutableStateOf<String?>(null) }
    ObserveMessages(viewModel.messages)

    val session = state.session
    val biometricEnabled = session?.biometricEnabled == true

    fun requestEnable() {
        promptError = null
        if (!availability.available) {
            promptError = availability.message
            return
        }
        val host = activity
        if (host == null) {
            promptError = "Não foi possível abrir a autenticação biométrica."
            return
        }
        BiometricAuth.authenticate(
            activity = host,
            title = "Ativar bloqueio biométrico",
            subtitle = "Confirme sua identidade para proteger o acesso ao Carteira Invest.",
            negativeText = "Cancelar",
            onSuccess = { viewModel.setBiometricEnabled(true) },
            onError = { promptError = it },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CarteiraTopBar(
            title = "Segurança",
            subtitle = "Bloqueio e proteção local",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Bloqueio biométrico",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (biometricEnabled) {
                                    "Ao abrir o app, será preciso confirmar sua biometria."
                                } else {
                                    "Proteja o acesso à sua sessão neste aparelho."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = biometricEnabled,
                            enabled = !state.isUpdating &&
                                session != null &&
                                (biometricEnabled || availability.available),
                            onCheckedChange = { enabled ->
                                if (enabled) requestEnable() else viewModel.setBiometricEnabled(false)
                            },
                        )
                    }
                    if (!availability.available && !biometricEnabled) {
                        Spacer(Modifier.height(10.dp))
                        InfoBanner(text = availability.message)
                    }
                    if (promptError != null) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                promptError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }

            item {
                AppCard {
                    Text("Sessão atual", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    LabeledValueRow("Nome", session?.name.orEmpty().ifBlank { "—" })
                    LabeledValueRow("E-mail", session?.email.orEmpty().ifBlank { "—" })
                    LabeledValueRow(
                        "Bloqueio",
                        if (biometricEnabled) "Biometria ativa" else "Somente senha",
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onLock,
                        enabled = biometricEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Bloquear agora")
                    }
                    if (!biometricEnabled) {
                        Text(
                            "Ative o bloqueio biométrico para poder bloquear a sessão sem sair da conta.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            item {
                AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Como seus dados são protegidos",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Bullet("A senha é guardada como hash PBKDF2 com salt aleatório; a senha em texto nunca é salva.")
                    Bullet("A sessão e a carteira selecionada ficam em EncryptedSharedPreferences.")
                    Bullet("Carteiras, lançamentos e cotações ficam no banco local do aplicativo.")
                    Bullet("O bloqueio biométrico é opcional e usa o BiometricPrompt do Android.")
                    Bullet("Nenhum dado é enviado para servidores além das consultas públicas de cotação.")
                }
            }

            item {
                InfoBanner(
                    icon = Icons.Rounded.Lock,
                    text = "Se a biometria falhar ou não estiver disponível, saia da conta e entre novamente " +
                        "com sua senha. Seus dados continuam salvos.",
                )
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
