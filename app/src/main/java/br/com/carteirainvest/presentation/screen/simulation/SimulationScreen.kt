package br.com.carteirainvest.presentation.screen.simulation

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import br.com.carteirainvest.presentation.components.DecimalKeyboard
import br.com.carteirainvest.presentation.components.InfoBanner
import br.com.carteirainvest.presentation.components.ObserveMessages
import br.com.carteirainvest.presentation.components.PerformanceLineChart
import br.com.carteirainvest.presentation.theme.BlueChart
import br.com.carteirainvest.presentation.theme.LocalExtendedColors
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

@Composable
fun SimulationScreen(
    onBack: () -> Unit,
    viewModel: SimulationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveMessages(viewModel.messages)
    val extended = LocalExtendedColors.current

    var initial by rememberSaveable { mutableStateOf("10000") }
    var monthly by rememberSaveable { mutableStateOf("1000") }
    var annualRate by rememberSaveable { mutableStateOf("10") }
    var months by rememberSaveable { mutableStateOf("120") }

    val result = state.result
    val series = if (result != null) {
        listOf(
            ChartSeries(
                label = "Investido",
                points = result.points.map {
                    ChartPoint(it.month.toFloat(), it.invested.toFloat())
                },
                color = BlueChart,
            ),
            ChartSeries(
                label = "Projetado",
                points = result.points.map {
                    ChartPoint(it.month.toFloat(), it.value.toFloat())
                },
                color = extended.positive,
            ),
        )
    } else {
        emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CarteiraTopBar(
            title = "Simulador",
            subtitle = "Juros compostos e aportes mensais",
            onBack = onBack,
            actions = {
                IconButton(onClick = { viewModel.clear() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Limpar resultado")
                }
            },
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Calculate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Parâmetros", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Projeção educativa, sem taxas ou impostos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = initial,
                        onValueChange = { initial = it },
                        label = { Text("Valor inicial") },
                        prefix = { Text("R$ ") },
                        singleLine = true,
                        keyboardOptions = DecimalKeyboard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = monthly,
                        onValueChange = { monthly = it },
                        label = { Text("Aporte mensal") },
                        prefix = { Text("R$ ") },
                        singleLine = true,
                        keyboardOptions = DecimalKeyboard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = annualRate,
                            onValueChange = { annualRate = it },
                            label = { Text("Rentabilidade anual") },
                            suffix = { Text("%") },
                            singleLine = true,
                            keyboardOptions = DecimalKeyboard,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = months,
                            onValueChange = { months = it.filter(Char::isDigit).take(3) },
                            label = { Text("Prazo (meses)") },
                            singleLine = true,
                            keyboardOptions = DecimalKeyboard,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(12, 60, 120, 240, 360)) { option ->
                            FilterChip(
                                selected = months == option.toString(),
                                onClick = { months = option.toString() },
                                label = { Text("${option / 12} ano(s)") },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { viewModel.simulate(initial, monthly, annualRate, months) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(Icons.Rounded.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Calcular projeção", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (result == null) {
                item {
                    AppCard {
                        Text(
                            "Preencha os valores e toque em calcular para visualizar a evolução do " +
                                "patrimônio, o total aportado e os rendimentos estimados.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ResultMetric(
                            label = "Valor final",
                            value = BrFormat.compactCurrency(result.finalValue),
                            modifier = Modifier.weight(1f),
                        )
                        ResultMetric(
                            label = "Total investido",
                            value = BrFormat.compactCurrency(result.totalInvested),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ResultMetric(
                            label = "Rendimentos",
                            value = BrFormat.signedCurrency(result.earnings),
                            modifier = Modifier.weight(1f),
                            valueColor = if (result.earnings.signum() >= 0) extended.positive else extended.negative,
                        )
                        ResultMetric(
                            label = "Prazo",
                            value = "${result.months} meses",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Evolução projetada",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                BrFormat.percent(
                                    result.earnings.multiply(BigDecimal("100"))
                                        .divide(result.totalInvested, 4, RoundingMode.HALF_UP),
                                    signed = true,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = extended.positive,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        PerformanceLineChart(
                            series = series,
                            xLabelFormatter = { value -> "${value.roundToInt()}m" },
                            yLabelFormatter = { value ->
                                BrFormat.compactCurrency(BigDecimal.valueOf(value.toDouble()))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val extendedColors = LocalExtendedColors.current
                            LegendDot("Investido", BlueChart)
                            LegendDot("Projetado", extendedColors.positive)
                        }
                    }
                }

                item {
                    AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)) {
                        Row {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Aporte inicial",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    BrFormat.currency(result.initialAmount),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Aporte mensal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    BrFormat.currency(result.monthlyContribution),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Taxa anual",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    BrFormat.percent(result.annualRatePercent),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            item {
                InfoBanner(
                    text = "Simulação com juros compostos mensais equivalentes à taxa anual informada. " +
                        "Não inclui inflação, impostos, taxas de corretagem ou variações de mercado.",
                )
            }
        }
    }
}

@Composable
private fun ResultMetric(
    label: String,
    value: String,
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
    }
}

@Composable
private fun LegendDot(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
