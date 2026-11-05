package br.com.carteirainvest.presentation.screen.assets

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.PortfolioPosition
import br.com.carteirainvest.presentation.components.AppCard
import br.com.carteirainvest.presentation.components.AssetAvatar
import br.com.carteirainvest.presentation.components.ChangeText
import br.com.carteirainvest.presentation.components.CarteiraTopBar
import br.com.carteirainvest.presentation.components.EmptyState
import br.com.carteirainvest.presentation.components.ObserveMessages
import br.com.carteirainvest.presentation.components.OfflineBanner
import br.com.carteirainvest.presentation.components.WalletPicker
import br.com.carteirainvest.presentation.navigation.LocalWalletHost
import br.com.carteirainvest.presentation.util.assetTypeColor
import br.com.carteirainvest.presentation.util.icon
import br.com.carteirainvest.presentation.util.label
import br.com.carteirainvest.presentation.util.pluralLabel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(
    onOpenAsset: (String) -> Unit,
    onAddAsset: () -> Unit,
    onManageWallets: () -> Unit,
    viewModel: AssetsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val host = LocalWalletHost.current
    ObserveMessages(viewModel.messages)

    var search by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }

    val filtered = state.positions.filter { position ->
        val matchesType = selectedType == null || position.asset.type.name == selectedType
        val query = search.trim()
        val matchesSearch = query.isBlank() ||
            position.asset.ticker.contains(query, ignoreCase = true) ||
            position.asset.name.contains(query, ignoreCase = true)
        matchesType && matchesSearch
    }
    val openPositions = filtered.filterNot { it.isClosed }
    val totalValue = openPositions.fold(BigDecimal.ZERO) { acc, position -> acc.add(position.currentValue) }
    val totalResult = openPositions.fold(BigDecimal.ZERO) { acc, position -> acc.add(position.profitLoss) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CarteiraTopBar(
                title = "Ativos",
                subtitle = host?.selectedWallet?.name ?: "Carteira atual",
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar cotações")
                    }
                },
            )

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 98.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        WalletPicker(onManageWallets = onManageWallets)
                        Spacer(Modifier.height(8.dp))
                        OfflineBanner()
                    }

                    item {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = { Text("Buscar por ticker ou nome") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (search.isNotBlank()) {
                                    IconButton(onClick = { search = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Limpar busca")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedType == null,
                                    onClick = { selectedType = null },
                                    label = { Text("Todos") },
                                )
                            }
                            items(AssetType.entries) { type ->
                                FilterChip(
                                    selected = selectedType == type.name,
                                    onClick = {
                                        selectedType = if (selectedType == type.name) null else type.name
                                    },
                                    label = { Text(type.pluralLabel()) },
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SummaryPill(
                                label = "Valor atual",
                                value = BrFormat.compactCurrency(totalValue),
                                modifier = Modifier.weight(1f),
                            )
                            SummaryPill(
                                label = "Resultado",
                                value = BrFormat.signedCurrency(totalResult),
                                modifier = Modifier.weight(1f),
                                positive = totalResult.signum() >= 0,
                            )
                            SummaryPill(
                                label = "Ativos",
                                value = filtered.size.toString(),
                                modifier = Modifier.weight(0.72f),
                            )
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            AppCard {
                                EmptyState(
                                    icon = if (search.isBlank() && selectedType == null) {
                                        Icons.Rounded.Add
                                    } else {
                                        Icons.Rounded.SearchOff
                                    },
                                    title = if (state.positions.isEmpty()) {
                                        "Nenhum ativo cadastrado"
                                    } else {
                                        "Nenhum ativo encontrado"
                                    },
                                    message = if (state.positions.isEmpty()) {
                                        "Cadastre seu primeiro ativo para acompanhar posições, proventos e rentabilidade."
                                    } else {
                                        "Ajuste a busca ou os filtros para encontrar seus ativos."
                                    },
                                    actionLabel = if (state.positions.isEmpty()) "Adicionar ativo" else null,
                                    onAction = if (state.positions.isEmpty()) onAddAsset else null,
                                )
                            }
                        }
                    } else {
                        items(filtered, key = { it.asset.id }) { position ->
                            AssetPositionCard(position = position, onClick = { onOpenAsset(position.asset.id) })
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddAsset,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Adicionar ativo")
        }
    }
}

@Composable
private fun SummaryPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    positive: Boolean = true,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (positive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AssetPositionCard(position: PortfolioPosition, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssetAvatar(
                ticker = position.asset.ticker,
                icon = position.asset.type.icon(),
                color = assetTypeColor(position.asset.type),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        position.asset.ticker,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                    if (position.isClosed) {
                        Text(
                            "Encerrada",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape,
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    } else {
                        Text(
                            position.asset.type.label(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    position.asset.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (position.isClosed) {
                        "Resultado realizado ${BrFormat.signedCurrency(position.realizedGain)}"
                    } else {
                        "${BrFormat.quantity(position.quantity)} un · PM ${BrFormat.currency(position.averagePrice)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    BrFormat.currency(if (position.isClosed) position.realizedGain else position.currentValue),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (position.isClosed) {
                    Text(
                        "Encerrada",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ChangeText(
                        value = position.dailyChange,
                        percent = position.dailyChangePercent,
                        showValue = false,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        BrFormat.percent(position.profitLossPercent, signed = true),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (position.profitLoss.signum() >= 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
    }
}
