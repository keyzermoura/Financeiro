package br.com.carteirainvest.presentation.screen.more

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.domain.model.Wallet
import br.com.carteirainvest.presentation.components.AppCard
import br.com.carteirainvest.presentation.components.CarteiraTopBar
import br.com.carteirainvest.presentation.components.DeleteConfirmationDialog
import br.com.carteirainvest.presentation.components.EmptyState
import br.com.carteirainvest.presentation.components.InfoBanner
import br.com.carteirainvest.presentation.components.WalletFormDialog
import br.com.carteirainvest.presentation.navigation.LocalWalletHost

@Composable
fun WalletsScreen(onBack: () -> Unit) {
    val host = LocalWalletHost.current
    var showCreate by remember { mutableStateOf(false) }
    var editingWallet by remember { mutableStateOf<Wallet?>(null) }
    var deletingWallet by remember { mutableStateOf<Wallet?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        CarteiraTopBar(
            title = "Carteiras",
            subtitle = if (host == null) {
                "Carregando…"
            } else {
                "${host.wallets.size} carteira(s) neste aparelho"
            },
            onBack = onBack,
            actions = {
                IconButton(onClick = { showCreate = true }, enabled = host != null) {
                    Icon(Icons.Rounded.Add, contentDescription = "Nova carteira")
                }
            },
        )

        if (host == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Não foi possível carregar as carteiras.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoBanner(
                    text = "Cada carteira tem seus próprios ativos, lançamentos, proventos e alertas. " +
                        "A carteira ativa é usada em todo o aplicativo.",
                )
            }

            if (host.wallets.isEmpty()) {
                item {
                    AppCard {
                        EmptyState(
                            icon = Icons.Outlined.AccountBalanceWallet,
                            title = "Nenhuma carteira cadastrada",
                            message = "Crie uma carteira para começar a registrar seus investimentos.",
                            actionLabel = "Criar carteira",
                            onAction = { showCreate = true },
                        )
                    }
                }
            } else {
                items(host.wallets, key = { it.id }) { wallet ->
                    WalletCard(
                        wallet = wallet,
                        selected = wallet.id == host.selectedWallet?.id,
                        onSelect = { host.onSelectWallet(wallet.id) },
                        onEdit = { editingWallet = wallet },
                        onDelete = { deletingWallet = wallet },
                    )
                }
            }

            item {
                Button(
                    onClick = { showCreate = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Nova carteira")
                }
            }
        }
    }

    if (showCreate) {
        WalletFormDialog(
            title = "Nova carteira",
            initialName = "",
            initialDescription = "",
            onDismiss = { showCreate = false },
            onConfirm = { name, description ->
                host?.onCreateWallet(name, description)
                showCreate = false
            },
        )
    }

    editingWallet?.let { wallet ->
        WalletFormDialog(
            title = "Editar carteira",
            initialName = wallet.name,
            initialDescription = wallet.description,
            onDismiss = { editingWallet = null },
            onConfirm = { name, description ->
                host?.onUpdateWallet(wallet, name, description)
                editingWallet = null
            },
        )
    }

    deletingWallet?.let { wallet ->
        DeleteConfirmationDialog(
            title = "Excluir carteira?",
            message = "A carteira \"${wallet.name}\" e todos os ativos, lançamentos, proventos e alertas " +
                "vinculados a ela serão removidos deste aparelho. Esta ação não pode ser desfeita.",
            onDismiss = { deletingWallet = null },
            onConfirm = {
                host?.onDeleteWallet(wallet.id)
                deletingWallet = null
            },
        )
    }
}

@Composable
private fun WalletCard(
    wallet: Wallet,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(onClick = onSelect) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                },
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        wallet.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "Ativa",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                Text(
                    wallet.description.ifBlank {
                        "Criada em ${BrFormat.date(wallet.createdAt.toLocalDate())}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!selected) {
                Text(
                    "Toque para ativar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onSelect)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "Editar ${wallet.name}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Excluir ${wallet.name}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun Long.toLocalDate(): java.time.LocalDate =
    java.time.Instant.ofEpochMilli(this)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
