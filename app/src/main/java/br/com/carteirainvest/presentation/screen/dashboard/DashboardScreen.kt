package br.com.carteirainvest.presentation.screen.dashboard

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.domain.model.AllocationSlice
import br.com.carteirainvest.domain.model.PortfolioPosition
import br.com.carteirainvest.presentation.components.AllocationDonut
import br.com.carteirainvest.presentation.components.AppCard
import br.com.carteirainvest.presentation.components.AssetAvatar
import br.com.carteirainvest.presentation.components.ChangeText
import br.com.carteirainvest.presentation.components.CarteiraTopBar
import br.com.carteirainvest.presentation.components.EmptyState
import br.com.carteirainvest.presentation.components.OfflineBanner
import br.com.carteirainvest.presentation.components.ObserveMessages
import br.com.carteirainvest.presentation.components.PercentText
import br.com.carteirainvest.presentation.components.SectionHeader
import br.com.carteirainvest.presentation.components.WalletPicker
import br.com.carteirainvest.presentation.navigation.LocalWalletHost
import br.com.carteirainvest.presentation.theme.LocalExtendedColors
import br.com.carteirainvest.presentation.util.assetTypeColor
import br.com.carteirainvest.presentation.util.icon
import br.com.carteirainvest.presentation.util.label
import java.math.BigDecimal
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenAsset: (String) -> Unit,
    onAddAsset: () -> Unit,
    onManageWallets: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenPerformance: () -> Unit,
    onOpenDividends: () -> Unit,
    onOpenAssets: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val host = LocalWalletHost.current
    ObserveMessages(viewModel.messages)
    val portfolio = state.portfolio
    val extended = LocalExtendedColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        CarteiraTopBar(
            title = greeting(host?.userName.orEmpty()),
            subtitle = host?.selectedWallet?.name ?: "Sua carteira de investimentos",
            actions = {
                IconButton(onClick = onOpenAlerts) {
                    Icon(Icons.Rounded.Notifications, contentDescription = "Alertas")
                }
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    WalletPicker(onManageWallets = onManageWallets)
                    Spacer(Modifier.height(10.dp))
                    OfflineBanner()
                }

                if (host?.selectedWallet == null) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.AccountBalanceWallet,
                            title = "Sua primeira carteira começa aqui",
                            message = "Crie uma carteira para organizar ativos, acompanhar a rentabilidade e receber alertas.",
                            actionLabel = "Gerenciar carteiras",
                            onAction = onManageWallets,
                        )
                    }
                } else {
                    item {
                        HeroCard(
                            portfolio = portfolio,
                            isRefreshing = state.isRefreshing,
                            onRefresh = viewModel::refresh,
                        )
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickAction(
                                label = "Novo ativo",
                                icon = Icons.Rounded.Add,
                                modifier = Modifier.weight(1f),
                                onClick = onAddAsset,
                            )
                            QuickAction(
                                label = "Proventos",
                                icon = Icons.Rounded.Payments,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenDividends,
                            )
                            QuickAction(
                                label = "Evolução",
                                icon = Icons.Rounded.Timeline,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenPerformance,
                            )
                        }
                    }

                    if (portfolio.allocation.isNotEmpty()) {
                        item {
                            AppCard {
                                SectionHeader(
                                    title = "Alocação por categoria",
                                    actionText = "Ver evolução",
                                    onAction = onOpenPerformance,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AllocationDonut(
                                        slices = portfolio.allocation,
                                        total = portfolio.totalValue,
                                        modifier = Modifier.size(148.dp),
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(9.dp),
                                    ) {
                                        portfolio.allocation.forEach { slice ->
                                            AllocationLegendRow(slice)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        AppCard(
                            onClick = onOpenDividends,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(42.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Payments,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Proventos recebidos",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        BrFormat.currency(portfolio.dividendsReceived),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item {
                        SectionHeader(
                            title = "Maiores posições",
                            actionText = "Ver todos",
                            onAction = onOpenAssets,
                        )
                    }

                    if (portfolio.positions.isEmpty()) {
                        item {
                            AppCard {
                                EmptyState(
                                    icon = Icons.Rounded.Add,
                                    title = "Nenhum ativo nesta carteira",
                                    message = "Cadastre ações, FIIs, ETFs, criptomoedas ou renda fixa para começar.",
                                    actionLabel = "Adicionar ativo",
                                    onAction = onAddAsset,
                                )
                            }
                        }
                    } else {
                        items(portfolio.positions.take(5).size) { index ->
                            val position = portfolio.positions[index]
                            PositionCard(position = position, onClick = { onOpenAsset(position.asset.id) })
                        }
                        item {
                            Text(
                                "Atualizado ${BrFormat.relative(portfolio.lastUpdatedAt).lowercase()} · " +
                                    "${portfolio.assetCount} ativo(s) em carteira",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }

                    item {
                        Text(
                            "Rentabilidade passada não garante resultados futuros. " +
                                "As cotações podem variar após a última atualização.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    portfolio: br.com.carteirainvest.domain.model.PortfolioSummary,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val extended = LocalExtendedColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(listOf(extended.heroStart, extended.heroEnd)),
                shape = RoundedCornerShape(26.dp),
            )
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Patrimônio total",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1f),
                )
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Atualizar",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Text(
                BrFormat.currency(portfolio.totalValue),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            val dailyColor = when {
                portfolio.dailyChange.signum() > 0 -> Color(0xFFB9F6D5)
                portfolio.dailyChange.signum() < 0 -> Color(0xFFFFC9C4)
                else -> Color.White.copy(alpha = 0.75f)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Hoje ${BrFormat.signedCurrency(portfolio.dailyChange)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = dailyColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "(${BrFormat.percent(portfolio.dailyChangePercent, signed = true)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = dailyColor,
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
            Spacer(Modifier.height(13.dp))
            Row {
                HeroMetric(
                    label = "Investido",
                    value = BrFormat.compactCurrency(portfolio.totalInvested),
                    modifier = Modifier.weight(1f),
                )
                HeroMetric(
                    label = "Ganho total",
                    value = BrFormat.signedCurrency(portfolio.totalGain),
                    supporting = BrFormat.percent(portfolio.totalGainPercent, signed = true),
                    modifier = Modifier.weight(1f),
                )
                HeroMetric(
                    label = "Atualizado",
                    value = BrFormat.relative(portfolio.lastUpdatedAt),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier, supporting: String? = null) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.65f),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFB9F6D5),
            )
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AllocationLegendRow(slice: AllocationSlice) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(assetTypeColor(slice.type), CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            slice.type.label(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            BrFormat.percent(slice.percentage),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PositionCard(position: PortfolioPosition, onClick: () -> Unit) {
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
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        position.asset.type.label(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${BrFormat.quantity(position.quantity)} un · PM ${BrFormat.currency(position.averagePrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    BrFormat.currency(position.currentValue),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                ChangeText(
                    value = position.profitLoss,
                    percent = position.profitLossPercent,
                    showValue = false,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun greeting(name: String): String {
    val firstName = name.trim().substringBefore(' ').ifBlank { "investidor" }
    val hour = LocalTime.now().hour
    val prefix = when (hour) {
        in 0..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }
    return "$prefix, $firstName"
}
