package br.com.carteirainvest.presentation.screen.more

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.carteirainvest.BuildConfig
import br.com.carteirainvest.domain.model.ReportFile
import br.com.carteirainvest.presentation.components.AppCard
import br.com.carteirainvest.presentation.components.CarteiraTopBar
import br.com.carteirainvest.presentation.components.DeleteConfirmationDialog
import br.com.carteirainvest.presentation.components.InfoBanner
import br.com.carteirainvest.presentation.components.ObserveMessages
import br.com.carteirainvest.presentation.components.SectionHeader
import br.com.carteirainvest.presentation.navigation.LocalSnackbarHostState
import java.io.File

@Composable
fun MoreScreen(
    onOpenWallets: () -> Unit,\n    onOpenCash: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenExchange: () -> Unit,
    onOpenSimulation: () -> Unit,
    onOpenSecurity: () -> Unit,
    onLock: () -> Unit,
    onLogout: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    ObserveMessages(viewModel.messages)

    var confirmLogout by remember { mutableStateOf(false) }
    val session = state.session

    LaunchedEffect(Unit) {
        viewModel.reports.collect { report ->
            if (!shareReport(context, report)) {
                snackbarHostState.showSnackbar("O arquivo do relatório não foi encontrado.")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CarteiraTopBar(
            title = "Mais",
            subtitle = "Sua conta e preferências",
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ProfileCard(
                    name = session?.name.orEmpty(),
                    email = session?.email.orEmpty(),
                )
            }

            item {
                SectionHeader(title = "Gestão")
            }

            item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    MenuRow(
                        icon = Icons.Rounded.AccountBalanceWallet,
                        title = "Carteiras",
                        subtitle = "Criar, editar e escolher a carteira ativa",
                        onClick = onOpenWallets,
                    )
                    MenuDivider()
                    MenuRow(
                        icon = Icons.Rounded.Notifications,
                        title = "Alertas",
                        subtitle = "Variações de preço e vencimentos",
                        onClick = onOpenAlerts,
                    )
                    MenuDivider()
                    MenuRow(
                        icon = Icons.Rounded.CurrencyExchange,
                        title = "Câmbio",
                        subtitle = "Dólar e euro com conversor",
                        onClick = onOpenExchange,
                    )
                    MenuDivider()
                    MenuRow(
                        icon = Icons.Rounded.Calculate,
                        title = "Simulador",
                        subtitle = "Projete juros compostos e aportes",
                        onClick = onOpenSimulation,
                    )
                }
            }

            item {
                SectionHeader(title = "Relatório")
            }

            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Relatório em PDF", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Resumo patrimonial, alocação, posições e proventos da carteira ativa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = viewModel::exportReport,
                        enabled = !state.isExporting && session != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Gerando relatório…")
                        } else {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Gerar e compartilhar")
                        }
                    }
                    Text(
                        "O arquivo é gerado localmente e compartilhado pelo seletor do Android. " +
                            "Nenhum dado é enviado para servidores.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                SectionHeader(title = "Segurança")
            }

            item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    MenuRow(
                        icon = Icons.Rounded.Security,
                        title = "Segurança e biometria",
                        subtitle = "Bloqueio local e proteção da sessão",
                        onClick = onOpenSecurity,
                    )
                    MenuDivider()
                    MenuRow(
                        icon = Icons.Rounded.Lock,
                        title = "Bloquear agora",
                        subtitle = if (session?.biometricEnabled == true) {
                            "Exigir biometria para voltar ao app"
                        } else {
                            "Ative a biometria para usar o bloqueio"
                        },
                        enabled = session?.biometricEnabled == true,
                        onClick = onLock,
                    )
                    MenuDivider()
                    MenuRow(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        title = "Sair da conta",
                        subtitle = "Encerrar a sessão neste aparelho",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { confirmLogout = true },
                    )
                }
            }

            item {
                InfoBanner(
                    icon = Icons.Rounded.Shield,
                    text = "Sua conta é local: a senha é armazenada com hash PBKDF2 e a sessão " +
                        "fica em preferências criptografadas. Faça backup dos seus dados com o relatório em PDF.",
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Carteira Invest ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Cotações: Brapi, CoinGecko e AwesomeAPI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }

    if (confirmLogout) {
        DeleteConfirmationDialog(
            title = "Sair da conta?",
            message = "Sua sessão será encerrada neste aparelho. As carteiras e os lançamentos continuam salvos.",
            confirmText = "Sair",
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                onLogout()
            },
        )
    }
}

@Composable
private fun ProfileCard(name: String, email: String) {
    val initials = remember(name) {
        name.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "CI" }
    }
    AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name.ifBlank { "Investidor" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) tint else tint.copy(alpha = 0.4f),
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) tint else tint.copy(alpha = 0.45f),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.55f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (enabled) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

private fun shareReport(context: Context, report: ReportFile): Boolean {
    val file = File(report.absolutePath)
    if (!file.exists()) return false
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Relatório da carteira ${report.walletName}")
        putExtra(Intent.EXTRA_TEXT, "Relatório gerado pelo Carteira Invest.")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Compartilhar relatório")
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }.onFailure { return false }
    return true
}
