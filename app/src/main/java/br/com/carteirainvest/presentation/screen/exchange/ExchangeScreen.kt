package br.com.carteirainvest.presentation.screen.exchange

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
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import br.com.carteirainvest.domain.model.ExchangeRate
import br.com.carteirainvest.presentation.components.AppCard
import br.com.carteirainvest.presentation.components.CarteiraTopBar
import br.com.carteirainvest.presentation.components.EmptyState
import br.com.carteirainvest.presentation.components.InfoBanner
import br.com.carteirainvest.presentation.components.ObserveMessages
import br.com.carteirainvest.presentation.components.PercentText
import br.com.carteirainvest.presentation.theme.BlueChart
import br.com.carteirainvest.presentation.theme.Gold500
import java.math.BigDecimal
import java.math.RoundingMode

private val currencies = listOf("BRL", "USD", "EUR")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeScreen(
    onBack: () -> Unit,
    viewModel: ExchangeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveMessages(viewModel.messages)

    var amount by rememberSaveable { mutableStateOf("1000") }
    var from by rememberSaveable { mutableStateOf("BRL") }
    var to by rememberSaveable { mutableStateOf("USD") }

    val bidUsd = state.rates.firstOrNull { it.pair == "USD-BRL" }?.bid
    val bidEur = state.rates.firstOrNull { it.pair == "EUR-BRL" }?.bid
    val converted = convert(amount, from, to, bidUsd, bidEur)

    Column(modifier = Modifier.fillMaxSize()) {
        CarteiraTopBar(
            title = "Câmbio",
            subtitle = "Dólar e euro em reais",
            onBack = onBack,
            actions = {
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar câmbio")
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.rates.isEmpty()) {
                    item {
                        AppCard {
                            if (state.isRefreshing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                EmptyState(
                                    icon = Icons.Rounded.CurrencyExchange,
                                    title = "Cotações indisponíveis",
                                    message = state.error ?: "Não foi possível carregar as taxas de câmbio. " +
                                        "Verifique a conexão e tente novamente.",
                                    actionLabel = "Tentar novamente",
                                    onAction = viewModel::refresh,
                                )
                            }
                        }
                    }
                } else {
                    if (state.error != null) {
                        item {
                            InfoBanner(
                                text = "${state.error} Exibindo as últimas taxas salvas no aparelho.",
                            )
                        }
                    }
                    state.rates.forEach { rate ->
                        item(key = rate.pair) {
                            ExchangeRateCard(rate)
                        }
                    }
                }

                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Conversor", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text("Valor") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "De",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        CurrencySelector(selected = from, onSelected = { from = it })
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Para",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        CurrencySelector(selected = to, onSelected = { to = it })
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    MaterialTheme.shapes.medium,
                                )
                                .padding(14.dp),
                        ) {
                            Column {
                                Text(
                                    "Resultado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    when {
                                        converted == null -> "—"
                                        to == "BRL" -> BrFormat.currency(converted)
                                        else -> "${BrFormat.decimal(converted)} $to"
                                    },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (from == to) {
                            Text(
                                "Escolha moedas diferentes para converter.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }

                item {
                    InfoBanner(
                        text = "Cotações de câmbio da AwesomeAPI, com cache local de 10 minutos. " +
                            "Valores de referência, sem spread de banco ou corretora.",
                    )
                }
            }
        }
    }
}

@Composable
private fun ExchangeRateCard(rate: ExchangeRate) {
    val accent = if (rate.pair.startsWith("USD")) BlueChart else Gold500
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    rate.pair.take(3),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rate.pair.replace("-", "/"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    rate.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "R$ ${BrFormat.decimal(rate.bid)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                PercentText(
                    percent = rate.percentChange,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                "Compra R$ ${BrFormat.decimal(rate.bid)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Venda R$ ${BrFormat.decimal(rate.ask)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                BrFormat.relative(rate.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySelector(selected: String, onSelected: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        currencies.forEachIndexed { index, currency ->
            SegmentedButton(
                selected = selected == currency,
                onClick = { onSelected(currency) },
                shape = SegmentedButtonDefaults.itemShape(index, currencies.size),
                label = { Text(currency) },
            )
        }
    }
}

private fun convert(
    amountText: String,
    from: String,
    to: String,
    usd: BigDecimal?,
    eur: BigDecimal?,
): BigDecimal? {
    val amount = BrFormat.parseDecimal(amountText) ?: return null
    if (from == to) return amount.setScale(2, RoundingMode.HALF_UP)
    val rateFrom = when (from) {
        "BRL" -> BigDecimal.ONE
        "USD" -> usd
        "EUR" -> eur
        else -> null
    } ?: return null
    val rateTo = when (to) {
        "BRL" -> BigDecimal.ONE
        "USD" -> usd
        "EUR" -> eur
        else -> null
    } ?: return null
    if (rateFrom.signum() <= 0 || rateTo.signum() <= 0) return null
    val inBrl = amount.multiply(rateFrom)
    val result = inBrl.divide(rateTo, 6, RoundingMode.HALF_UP)
    return result.setScale(if (to == "BRL") 2 else 4, RoundingMode.HALF_UP)
}
