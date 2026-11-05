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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.domain.model.AssetDetail
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.PortfolioPosition
import br.com.carteirainvest.domain.model.RemoteDividend
import br.com.carteirainvest.domain.model.Transaction
import br.com.carteirainvest.domain.model.TransactionType
import br.com.carteirainvest.presentation.components.AppCard
import br.com.carteirainvest.presentation.components.AssetAvatar
import br.com.carteirainvest.presentation.components.CarteiraTopBar
import br.com.carteirainvest.presentation.components.DatePickerField
import br.com.carteirainvest.presentation.components.DecimalKeyboard
import br.com.carteirainvest.presentation.components.DeleteConfirmationDialog
import br.com.carteirainvest.presentation.components.InfoBanner
import br.com.carteirainvest.presentation.components.LabeledValueRow
import br.com.carteirainvest.presentation.components.MetricValue
import br.com.carteirainvest.presentation.components.ObserveMessages
import br.com.carteirainvest.presentation.components.SectionHeader
import br.com.carteirainvest.presentation.navigation.LocalSnackbarHostState
import br.com.carteirainvest.presentation.theme.LocalExtendedColors
import br.com.carteirainvest.presentation.util.assetTypeColor
import br.com.carteirainvest.presentation.util.icon
import br.com.carteirainvest.presentation.util.isDividend
import br.com.carteirainvest.presentation.util.label
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    onBack: () -> Unit,
    onEditAsset: (String) -> Unit,
    viewModel: AssetDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveMessages(viewModel.messages)

    var showTransactionDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var newTransactionType by remember { mutableStateOf(TransactionType.BUY) }
    var deletingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var confirmDeleteAsset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.deleted.collect { onBack() }
    }

    val detail = state.detail

    Column(modifier = Modifier.fillMaxSize()) {
        val title = detail?.asset?.ticker ?: "Ativo"
        CarteiraTopBar(
            title = title,
            subtitle = detail?.asset?.displayName,
            onBack = onBack,
            actions = {
                if (detail != null) {
                    IconButton(onClick = viewModel::refreshQuote) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar cotação")
                    }
                    AssetMenu(
                        onEdit = { onEditAsset(detail.asset.id) },
                        onDelete = { confirmDeleteAsset = true },
                    )
                }
            },
        )

        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            detail == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Este ativo não está mais disponível.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ele pode ter sido excluído em outro dispositivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onBack) { Text("Voltar") }
                    }
                }
            }

            else -> {
                AssetDetailContent(
                    detail = detail,
                    state = state,
                    onRefreshQuote = viewModel::refreshQuote,
                    onRefreshRemoteDividends = viewModel::refreshRemoteDividends,
                    onAddTransaction = { type ->
                        newTransactionType = type
                        editingTransaction = null
                        showTransactionDialog = true
                    },
                    onEditTransaction = { transaction ->
                        editingTransaction = transaction
                        newTransactionType = transaction.type
                        showTransactionDialog = true
                    },
                    onDeleteTransaction = { deletingTransaction = it },
                )
            }
        }
    }

    if (showTransactionDialog && detail != null) {
        TransactionFormDialog(
            asset = detail.asset,
            initial = editingTransaction,
            defaultType = newTransactionType,
            isSaving = state.isSavingTransaction,
            onDismiss = { showTransactionDialog = false },
            onConfirm = { type, quantity, price, fees, dividend, date, notes ->
                viewModel.saveTransaction(
                    transactionId = editingTransaction?.id,
                    type = type,
                    quantityText = quantity,
                    unitPriceText = price,
                    feesText = fees,
                    dividendText = dividend,
                    date = date,
                    notes = notes,
                )
                showTransactionDialog = false
            },
        )
    }

    deletingTransaction?.let { transaction ->
        DeleteConfirmationDialog(
            title = "Excluir lançamento?",
            message = "A transação de ${transaction.type.label().lowercase()} de " +
                "${BrFormat.date(transaction.date)} será removida e o preço médio recalculado.",
            onDismiss = { deletingTransaction = null },
            onConfirm = {
                viewModel.deleteTransaction(transaction.id)
                deletingTransaction = null
            },
        )
    }

    if (confirmDeleteAsset && detail != null) {
        DeleteConfirmationDialog(
            title = "Excluir ${detail.asset.ticker}?",
            message = "O ativo e todos os seus lançamentos serão removidos desta carteira. " +
                "Esta ação não pode ser desfeita.",
            onDismiss = { confirmDeleteAsset = false },
            onConfirm = {
                confirmDeleteAsset = false
                viewModel.deleteAsset()
            },
        )
    }
}

@Composable
private fun AssetMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Mais opções")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Editar ativo") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("Excluir ativo", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun AssetDetailContent(
    detail: AssetDetail,
    state: AssetDetailUiState,
    onRefreshQuote: () -> Unit,
    onRefreshRemoteDividends: () -> Unit,
    onAddTransaction: (TransactionType) -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
) {
    val asset = detail.asset
    val position = detail.position
    val quote = detail.quote
    val extended = LocalExtendedColors.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssetAvatar(
                    ticker = asset.ticker,
                    icon = asset.type.icon(),
                    color = assetTypeColor(asset.type),
                    size = 54.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        asset.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${asset.type.label()} · ${asset.ticker}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (asset.supportsMarketQuote) {
                        Text(
                            BrFormat.currency(position.currentPrice),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (quote != null) {
                            Text(
                                BrFormat.percent(quote.changePercent, signed = true),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (quote.changePercent.signum() >= 0) extended.positive else extended.negative,
                            )
                        } else {
                            Text(
                                "Sem cotação",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            BrFormat.currency(position.currentPrice),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "valor estimado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (position.priceIsEstimated) {
            item {
                InfoBanner(
                    text = if (asset.type == AssetType.FIXED_INCOME) {
                        "Renda fixa é estimada pela taxa anual informada desde a primeira compra. " +
                            "Não substitui o extrato da corretora."
                    } else {
                        "Ainda não há cotação de mercado para este ativo. Os valores usam o preço médio pago."
                    },
                )
            }
        }

        if (quote != null) {
            item {
                Text(
                    "Cotação atualizada ${BrFormat.relative(quote.updatedAt).lowercase()}" +
                        (quote.name?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        item {
            AppCard {
                SectionHeader(title = "Sua posição")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricValue(
                        label = "Quantidade",
                        value = BrFormat.quantity(position.quantity),
                        modifier = Modifier.weight(1f),
                    )
                    MetricValue(
                        label = "Preço médio",
                        value = BrFormat.currency(position.averagePrice),
                        modifier = Modifier.weight(1f),
                    )
                    MetricValue(
                        label = "Valor atual",
                        value = BrFormat.currency(position.currentValue),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
                LabeledValueRow("Custo investido", BrFormat.currency(position.investedAmount))
                LabeledValueRow(
                    label = "Resultado não realizado",
                    value = "${BrFormat.signedCurrency(position.profitLoss)} · " +
                        BrFormat.percent(position.profitLossPercent, signed = true),
                    valueColor = if (position.profitLoss.signum() >= 0) extended.positive else extended.negative,
                )
                LabeledValueRow("Proventos recebidos", BrFormat.currency(position.dividendsReceived))
                LabeledValueRow(
                    label = "Ganho total",
                    value = BrFormat.signedCurrency(position.totalGain),
                    valueColor = if (position.totalGain.signum() >= 0) extended.positive else extended.negative,
                    supporting = "Inclui resultado realizado e proventos",
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { onAddTransaction(TransactionType.BUY) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.ShoppingCart, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Comprar", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { onAddTransaction(TransactionType.SELL) },
                    modifier = Modifier.weight(1f),
                    enabled = position.quantity.signum() > 0,
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Vender", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { onAddTransaction(TransactionType.DIVIDEND) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Provento", maxLines = 1)
                }
            }
        }

        item {
            SectionHeader(
                title = "Lançamentos",
                actionText = if (detail.transactions.isEmpty()) null else "Adicionar",
                onAction = { onAddTransaction(TransactionType.BUY) },
            )
        }

        if (detail.transactions.isEmpty()) {
            item {
                AppCard {
                    Text(
                        "Nenhum lançamento registrado. Adicione compras, vendas ou proventos " +
                            "para acompanhar o custo médio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(detail.transactions, key = { it.id }) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    onClick = { onEditTransaction(transaction) },
                    onDelete = { onDeleteTransaction(transaction) },
                )
            }
        }

        if (asset.type == AssetType.STOCK || asset.type == AssetType.FII || asset.type == AssetType.ETF) {
            item {
                RemoteDividendsSection(
                    dividends = state.remoteDividends,
                    isLoading = state.isLoadingRemoteDividends,
                    onRefresh = onRefreshRemoteDividends,
                )
            }
        }

        item {
            AssetInfoCard(detail)
        }

        item {
            Text(
                "Preço médio ponderado calculado com compras e vendas. Vendas usam o custo médio " +
                    "da posição. Confira os dados com a sua corretora.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (position.isClosed) {
            item {
                InfoBanner("Posição encerrada. O histórico e o resultado realizado continuam disponíveis.")
            }
        }

        if (state.isRefreshingQuote) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: Transaction,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val isDividend = transaction.type.isDividend()
    val icon = when (transaction.type) {
        TransactionType.BUY -> Icons.Rounded.ShoppingCart
        TransactionType.SELL -> Icons.Rounded.Remove
        TransactionType.DIVIDEND -> Icons.Rounded.Payments
    }
    val tint = when (transaction.type) {
        TransactionType.BUY -> MaterialTheme.colorScheme.primary
        TransactionType.SELL -> MaterialTheme.colorScheme.tertiary
        TransactionType.DIVIDEND -> LocalExtendedColors.current.positive
    }
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(tint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isDividend) {
                        "Provento"
                    } else {
                        "${transaction.type.label()} · ${BrFormat.quantity(transaction.quantity)} un"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    buildString {
                        append(BrFormat.date(transaction.date))
                        if (!isDividend) append(" · ${BrFormat.currency(transaction.unitPrice)}/un")
                        if (transaction.notes.isNotBlank()) append(" · ${transaction.notes}")
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
                    BrFormat.currency(transaction.grossValue),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDividend) tint else MaterialTheme.colorScheme.onSurface,
                )
                if (!isDividend && transaction.fees.signum() > 0) {
                    Text(
                        "taxas ${BrFormat.currency(transaction.fees)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Excluir lançamento",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun RemoteDividendsSection(
    dividends: List<RemoteDividend>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    AppCard {
        SectionHeader(
            title = "Proventos anunciados",
            actionText = if (isLoading) "..." else "Atualizar",
            onAction = onRefresh,
        )
        Text(
            "Histórico público da Brapi. Registre os proventos recebidos em Lançamentos para somá-los à carteira.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        if (isLoading && dividends.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else if (dividends.isEmpty()) {
            Text(
                "Nenhum provento anunciado encontrado para este ativo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            dividends.take(6).forEach { dividend ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            dividend.label.ifBlank { "Provento" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Pagamento ${BrFormat.date(dividend.paymentDate)}" +
                                (dividend.relatedTo?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "${BrFormat.currency(dividend.ratePerShare)}/cota",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalExtendedColors.current.positive,
                    )
                }
            }
            if (dividends.size > 6) {
                Text(
                    "+ ${dividends.size - 6} outros proventos anunciados",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AssetInfoCard(detail: AssetDetail) {
    val asset = detail.asset
    val position = detail.position
    AppCard {
        SectionHeader(title = "Detalhes do ativo")
        Spacer(Modifier.height(6.dp))
        LabeledValueRow("Tipo", asset.type.label())
        if (asset.type == AssetType.CRYPTO) {
            LabeledValueRow("ID CoinGecko", asset.cryptoId ?: "—")
        }
        if (asset.type == AssetType.FIXED_INCOME) {
            LabeledValueRow("Taxa anual informada", asset.annualRate?.let { BrFormat.percent(it) } ?: "—")
            LabeledValueRow("Vencimento", asset.maturityDate?.let(BrFormat::date) ?: "—")
            asset.maturityDate?.let { maturity ->
                val days = ChronoUnit.DAYS.between(LocalDate.now(), maturity)
                LabeledValueRow(
                    "Dias até o vencimento",
                    if (days >= 0) "$days dia(s)" else "Vencido há ${-days} dia(s)",
                )
            }
        }
        LabeledValueRow("Lançamentos", detail.transactions.size.toString())
        LabeledValueRow("Proventos acumulados", BrFormat.currency(position.dividendsReceived))
        LabeledValueRow("Resultado realizado", BrFormat.signedCurrency(position.realizedGain))
        LabeledValueRow("Atualizado em", BrFormat.relative(position.lastQuoteAt))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionFormDialog(
    asset: br.com.carteirainvest.domain.model.Asset,
    initial: Transaction?,
    defaultType: TransactionType,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        TransactionType,
        String,
        String,
        String,
        String,
        LocalDate,
        String,
    ) -> Unit,
) {
    var type by remember(initial?.id, defaultType) { mutableStateOf(initial?.type ?: defaultType) }
    var quantity by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.quantity?.let(BrFormat::inputDecimal) ?: "1")
    }
    var price by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.unitPrice?.let(BrFormat::inputDecimal).orEmpty())
    }
    var fees by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.fees?.let { BrFormat.inputDecimal(it) } ?: "0")
    }
    var dividend by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.dividendAmount?.let(BrFormat::inputDecimal).orEmpty())
    }
    var date by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.date ?: LocalDate.now())
    }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }

    val estimatedTotal = if (type == TransactionType.DIVIDEND) {
        BrFormat.parseDecimal(dividend) ?: BigDecimal.ZERO
    } else {
        val qty = BrFormat.parseDecimal(quantity) ?: BigDecimal.ZERO
        val unit = BrFormat.parseDecimal(price) ?: BigDecimal.ZERO
        val fee = BrFormat.parseDecimal(fees) ?: BigDecimal.ZERO
        qty.multiply(unit).add(fee).setScale(2, RoundingMode.HALF_UP)
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                if (initial == null) {
                    "Novo lançamento"
                } else {
                    "Editar lançamento"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "${asset.ticker} · ${asset.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TransactionType.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = type == option,
                            onClick = { type = option },
                            shape = SegmentedButtonDefaults.itemShape(index, TransactionType.entries.size),
                            label = { Text(option.label(), maxLines = 1) },
                        )
                    }
                }

                if (type == TransactionType.DIVIDEND) {
                    OutlinedTextField(
                        value = dividend,
                        onValueChange = { dividend = it },
                        label = { Text("Valor recebido") },
                        prefix = { Text("R$ ") },
                        singleLine = true,
                        keyboardOptions = DecimalKeyboard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "O provento não altera a quantidade nem o preço médio. " +
                            "Ele entra no total de proventos da carteira.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it },
                            label = { Text("Quantidade") },
                            singleLine = true,
                            keyboardOptions = DecimalKeyboard,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = { Text("Preço unit.") },
                            prefix = { Text("R$ ") },
                            singleLine = true,
                            keyboardOptions = DecimalKeyboard,
                            modifier = Modifier.weight(1.2f),
                        )
                    }
                    OutlinedTextField(
                        value = fees,
                        onValueChange = { fees = it },
                        label = { Text("Taxas e corretagem") },
                        prefix = { Text("R$ ") },
                        singleLine = true,
                        keyboardOptions = DecimalKeyboard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (type == TransactionType.BUY) {
                            "As taxas são somadas ao custo de aquisição e ao preço médio."
                        } else {
                            "As taxas são descontadas do valor da venda no resultado realizado."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DatePickerField(
                    label = "Data do lançamento",
                    date = date,
                    onDateSelected = { date = it },
                    allowFuture = false,
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(500) },
                    label = { Text("Observações (opcional)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    "Total estimado: ${BrFormat.currency(estimatedTotal)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    onConfirm(type, quantity, price, fees, dividend, date, notes)
                },
            ) {
                Text(if (initial == null) "Adicionar" else "Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
        },
    )
}
