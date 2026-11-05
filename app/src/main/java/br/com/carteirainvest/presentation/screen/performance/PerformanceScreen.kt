package br.com.carteirainvest.presentation.screen.performance

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
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import br.com.carteirainvest.presentation.components.AppCard
import br.com.carteirainvest.presentation.components.CarteiraTopBar
import br.com.carteirainvest.presentation.components.ChartPoint
import br.com.carteirainvest.presentation.components.ChartSeries
import br.com.carteirainvest.presentation.components.EmptyState
import br.com.carteirainvest.presentation.components.InfoBanner
import br.com.carteirainvest.presentation.components.ObserveMessages
import br.com.carteirainvest.presentation.components.PerformanceLineChart
import br.com.carteirainvest.presentation.navigation.LocalWalletHost
import br.com.carteirainvest.presentation.theme.BlueChart
import br.com.carteirainvest.presentation.theme.Gold500
import br.com.carteirainvest.presentation.theme.LocalExtendedColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.compose.material3.ExperimentalMaterial3Api

private enum class ChartMode { EVOLUTION, COMPARISON }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(
    viewModel: PerformanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val host = LocalWalletHost.current
    ObserveMessages(viewModel.messages)

    var mode by rememberSaveable { mutableStateOf(ChartMode.EVOLUTION.name) }
    val chartMode = ChartMode.valueOf(mode)
    val extended = LocalExtendedColors.current

    val portfolioSeries = if (state.snapshots.size >= 2) {
        listOf(
            ChartSeries(
                label = "Carteira",
                points = state.snapshots.map {
                    ChartPoint(
                        x = it.date.toEpochDay().toFloat(),
                        y = it.value.toFloat(),
                    )
                },
                color = extended.positive,
            ),
        )
    } else {
        emptyList()
    }

    val comparisonSeries = buildList {
        if (state.snapshots.size >= 2) {
            val first = state.snapshots.first().value
            if (first.signum() > 0) {
                add(
                    ChartSeries(
                        label = "Carteira",
                        points = state.snapshots.map {
                            ChartPoint(
                                x = it.date.toEpochDay().toFloat(),
                                y = it.value.multiply(BigDecimal("100"))
                                    .divide(first, 6, RoundingMode.HALF_UP)
                                    .toFloat(),
                            )
                        },
                        color = extended.positive,
                    ),
                )
            }
        }
        if (state.benchmark.size >= 2) {
            val first = state.benchmark.first().close
            if (first.signum() > 0) {
                add(
                    ChartSeries(
                        label = "BOVA11",
                        points = state.benchmark.map {
                            ChartPoint(
                                x = it.date.toEpochDay().toFloat(),
                                y = it.close.multiply(BigDecimal("100"))
                                    .divide(first, 6, RoundingMode.HALF_UP)
                                    .toFloat(),
                            )
                        },
                        color = BlueChart,
                    ),
                )
            }
        }
    }

    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")
    val xLabelFormatter: (Float) -> String = { value ->
        runCatching { LocalDate.ofEpochDay(value.roundToInt().toLong()).format(dateFormatter) }
            .getOrDefault("")
    }
    val yLabelFormatter: (Float) -> String = { value ->
        if (chartMode == ChartMode.EVOLUTION) {
            BrFormat.compactCurrency(BigDecimal.valueOf(value.toDouble()))
        } else {
            value.roundToInt().toString()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CarteiraTopBar(
            title = "Evolução",
            subtitle = host?.selectedWallet?.name ?: "Histórico do patrimônio",
            actions = {
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar histórico")
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
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PerformancePeriod.entries) { period ->
                            FilterChip(
                                selected = state.period == period,
                                onClick = { viewModel.selectPeriod(period) },
                                label = { Text(period.label) },
                            )
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard(
                            label = "Patrimônio atual",
                            value = BrFormat.compactCurrency(state.portfolio.totalValue),
                            supporting = "${state.snapshots.size} registro(s) no período",
                            modifier = Modifier.weight(1f),
                        )
                        MetricCard(
                            label = "Retorno no período",
                            value = BrFormat.signedCurrency(state.periodReturn),
                            supporting = BrFormat.percent(state.periodReturnPercent, signed = true),
                            valueColor = if (state.periodReturn.signum() >= 0) extended.positive else extended.negative,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (chartMode == ChartMode.EVOLUTION) "Evolução patrimonial" else "Comparação indexada",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    if (chartMode == ChartMode.EVOLUTION) {
                                        "Valores registrados nas atualizações de cotação"
                                    } else {
                                        "Base 100 no início do período · BOVA11 como proxy do Ibovespa"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ChartMode.entries.forEachIndexed { index, option ->
                                SegmentedButton(
                                    selected = chartMode == option,
                                    onClick = { mode = option.name },
                                    shape = SegmentedButtonDefaults.itemShape(index, ChartMode.entries.size),
                                    label = {
                                        Text(if (option == ChartMode.EVOLUTION) "Evolução" else "Comparação")
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (chartMode == ChartMode.EVOLUTION) {
                            if (portfolioSeries.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Rounded.Insights,
                                    title = "Histórico em construção",
                                    message = if (state.snapshots.isEmpty()) {
                                        "Atualize as cotações para registrar o primeiro snapshot do patrimônio."
                                    } else {
                                        "É preciso pelo menos dois registros em datas diferentes para desenhar a evolução."
                                    },
                                    actionLabel = "Atualizar agora",
                                    onAction = viewModel::refresh,
                                )
                            } else {
                                PerformanceLineChart(
                                    series = portfolioSeries,
                                    xLabelFormatter = xLabelFormatter,
                                    yLabelFormatter = yLabelFormatter,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(250.dp),
                                )
                            }
                        } else {
                            if (comparisonSeries.size < 2) {
                                EmptyState(
                                    icon = Icons.Rounded.Insights,
                                    title = "Comparativo indisponível",
                                    message = "São necessários pelo menos dois snapshots da carteira e o " +
                                        "histórico da BOVA11 para comparar os retornos.",
                                    actionLabel = "Atualizar agora",
                                    onAction = viewModel::refresh,
                                )
                            } else {
                                PerformanceLineChart(
                                    series = comparisonSeries,
                                    xLabelFormatter = xLabelFormatter,
                                    yLabelFormatter = yLabelFormatter,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(250.dp),
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    comparisonSeries.forEach { series ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .background(series.color, CircleShape),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(series.label, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Gold500.copy(alpha = 0.18f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "IB",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ibovespa via BOVA11", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Referência de mercado no mesmo período",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                BrFormat.percent(state.benchmarkReturnPercent, signed = true),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (state.benchmarkReturnPercent.signum() >= 0) {
                                    extended.positive
                                } else {
                                    extended.negative
                                },
                            )
                        }
                    }
                }

                item {
                    InfoBanner(
                        text = "Os snapshots são gravados quando as cotações são atualizadas e uma vez por dia. " +
                            "A comparação usa a BOVA11 como proxy do Ibovespa; não é o índice oficial.",
                    )
                }

                item {
                    Text(
                        "Atualizado ${BrFormat.relative(state.portfolio.lastUpdatedAt).lowercase()} · " +
                            "A rentabilidade considera apenas os snapshots deste aparelho.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    AppCard(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            supporting,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
