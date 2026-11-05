package br.com.carteirainvest.presentation.screen.dividends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.DividendRecord
import br.com.carteirainvest.presentation.components.AppCard
import br.com.carteirainvest.presentation.components.CarteiraTopBar
import br.com.carteirainvest.presentation.components.DatePickerField
import br.com.carteirainvest.presentation.components.DecimalKeyboard
import br.com.carteirainvest.presentation.components.EmptyState
import br.com.carteirainvest.presentation.components.InfoBanner
import br.com.carteirainvest.presentation.components.ObserveMessages
import br.com.carteirainvest.presentation.components.SectionHeader
import br.com.carteirainvest.presentation.navigation.LocalWalletHost
import br.com.carteirainvest.presentation.theme.LocalExtendedColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DividendsScreen(
    onOpenAsset: (String) -> Unit,
    onOpenAssets: () -> Unit,
    viewModel: DividendsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val host = LocalWalletHost.current
    ObserveMessages(viewModel.messages)
    var showAddDialog by remember { mutableStateOf(false) }
    val extended = LocalExtendedColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        CarteiraTopBar(
            title = "Proventos",
            subtitle = host?.selectedWallet?.name ?: "Dividendos e rendimentos",
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Registrar provento")
                }
            },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DividendMetric(
                            label = "Este ano",
                            value = BrFormat.compactCurrency(state.summary.currentYearTotal),
                            modifier = Modifier.weight(1f),
                            accent = true,
                        )
                        DividendMetric(
                            label = "Este mês",
                            value = BrFormat.compactCurrency(state.summary.monthlyTotal),
                            modifier = Modifier.weight(1f),
                        )
                        DividendMetric(
                            label = "Histórico",
                            value = BrFormat.compactCurrency(state.summary.total),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    AppCard {
                        SectionHeader(title = "Fluxo mensal")
                        Text(
                            state.selectedYear?.let { "Proventos de $it" } ?: "Últimos doze meses",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        DividendMonthlyChart(
                            series = state.monthlySeries,
                            barColor = extended.positive,
                        )
                    }
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = state.selectedYear == null,
                                onClick = { viewModel.selectYear(null) },
                                label = { Text("Todos") },
                            )
                        }
                        items(state.availableYears) { year ->
                            FilterChip(
                                selected = state.selectedYear == year,
                                onClick = { viewModel.selectYear(year) },
                                label = { Text(year.toString()) },
                            )
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Lançamentos",
                        actionText = if (state.filteredRecords.isEmpty()) null else
                            BrFormat.currency(state.selectedTotal),
                    )
                }

                if (state.filteredRecords.isEmpty()) {
                    item {
                        AppCard {
                            EmptyState(
                                icon = Icons.Rounded.Payments,
                                title = "Nenhum provento registrado",
                                message = "Registre dividendos, JCP e rendimentos recebidos para acompanhar " +
                                    "o retorno total da carteira.",
                                actionLabel = "Registrar provento",
                                onAction = { showAddDialog = true },
                            )
                        }
                    }
                } else {
                    items(state.filteredRecords, key = { it.transactionId }) { record ->
                        DividendRecordCard(
                            record = record,
                            onClick = { onOpenAsset(record.assetId) },
                        )
                    }
                }

                item {
                    InfoBanner(
                        text = "Os proventos são lançados manualmente. O histórico de proventos anunciados " +
                            "da B3 aparece nos detalhes de cada ação ou FII, mas não é somado automaticamente.",
                    )
                }
            }

            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Registrar provento")
            }
        }
    }

    if (showAddDialog) {
        AddDividendDialog(
            assets = state.assets,
            isSaving = state.isSaving,
            onDismiss = { showAddDialog = false },
            onOpenAssets = {
                showAddDialog = false
                onOpenAssets()
            },
            onConfirm = { assetId, amount, date, notes ->
                viewModel.addDividend(assetId, amount, date, notes)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun DividendMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Column(
        modifier = modifier
            .background(
                if (accent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                else MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
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
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DividendMonthlyChart(
    series: List<MonthlyDividend>,
    barColor: androidx.compose.ui.graphics.Color,
) {
    val max = series.maxOfOrNull { it.amount } ?: BigDecimal.ZERO
    if (max.signum() == 0) {
        Text(
            "Ainda não há proventos no período selecionado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 18.dp),
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(122.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        series.forEach { item ->
            val fraction = item.amount.divide(max, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .fillMaxHeight(if (fraction > 0f) fraction else 0.01f)
                            .background(
                                barColor.copy(alpha = if (fraction > 0f) 0.8f else 0.12f),
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                            ),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    item.month.month.getDisplayName(TextStyle.NARROW, BrFormat.BRAZIL).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DividendRecordCard(record: DividendRecord, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(LocalExtendedColors.current.positive.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Payments,
                    contentDescription = null,
                    tint = LocalExtendedColors.current.positive,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.ticker,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    record.notes.ifBlank { record.assetName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    BrFormat.currency(record.amount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalExtendedColors.current.positive,
                )
                Text(
                    BrFormat.date(record.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDividendDialog(
    assets: List<Asset>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onOpenAssets: () -> Unit,
    onConfirm: (String, String, LocalDate, String) -> Unit,
) {
    var selectedAsset by remember { mutableStateOf<Asset?>(assets.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var notes by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(assets) {
        val current = selectedAsset
        if (current == null || assets.none { it.id == current.id }) {
            selectedAsset = assets.firstOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Registrar provento") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (assets.isEmpty()) {
                    Text(
                        "Cadastre um ativo antes de registrar proventos.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                    ) {
                        OutlinedTextField(
                            value = selectedAsset?.let { "${it.ticker} · ${it.displayName}" }.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Ativo") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            assets.forEach { asset ->
                                DropdownMenuItem(
                                    text = { Text("${asset.ticker} · ${asset.displayName}") },
                                    onClick = {
                                        selectedAsset = asset
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Valor recebido") },
                        prefix = { Text("R$ ") },
                        singleLine = true,
                        keyboardOptions = DecimalKeyboard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DatePickerField(
                        label = "Data do pagamento",
                        date = date,
                        onDateSelected = { date = it },
                        allowFuture = false,
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it.take(500) },
                        label = { Text("Observações (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "O valor é somado aos proventos recebidos e não altera a quantidade do ativo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (assets.isEmpty()) {
                TextButton(onClick = onOpenAssets) { Text("Ir para ativos") }
            } else {
                TextButton(
                    enabled = !isSaving && selectedAsset != null && amount.isNotBlank(),
                    onClick = {
                        onConfirm(selectedAsset?.id.orEmpty(), amount, date, notes)
                    },
                ) { Text("Registrar") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
