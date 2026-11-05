package br.com.carteirainvest.presentation.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.domain.model.AllocationSlice
import br.com.carteirainvest.presentation.util.label
import br.com.carteirainvest.presentation.util.assetTypeColor
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.math.BigDecimal

@Composable
fun AllocationDonut(
    slices: List<AllocationSlice>,
    total: BigDecimal,
    modifier: Modifier = Modifier,
    centerLabel: String = "Patrimônio",
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                PieChart(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    description.isEnabled = false
                    legend.isEnabled = false
                    setDrawEntryLabels(false)
                    setUsePercentValues(false)
                    isRotationEnabled = false
                    isHighlightPerTapEnabled = false
                    holeRadius = 62f
                    transparentCircleRadius = 64f
                    setHoleColor(AndroidColor.TRANSPARENT)
                    setDrawSlicesUnderHole(false)
                    setExtraOffsets(4f, 4f, 4f, 4f)
                    setTouchEnabled(false)
                }
            },
            update = { chart ->
                val entries = slices
                    .filter { it.value.signum() > 0 }
                    .map { PieEntry(it.value.toFloat(), it.type.label()) }
                if (entries.isEmpty()) {
                    chart.clear()
                    chart.invalidate()
                } else {
                    val dataSet = PieDataSet(entries, "").apply {
                        colors = slices.filter { it.value.signum() > 0 }
                            .map { assetTypeColor(it.type).toArgb() }
                        sliceSpace = 2.5f
                        selectionShift = 0f
                        setDrawValues(false)
                    }
                    chart.data = PieData(dataSet)
                    chart.notifyDataSetChanged()
                    chart.invalidate()
                }
            },
            modifier = modifier.fillMaxSize(),
        )
        ColumnCenter(label = centerLabel, value = BrFormat.currency(total))
    }
}

@Composable
private fun ColumnCenter(label: String, value: String) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

data class ChartPoint(val x: Float, val y: Float)

data class ChartSeries(
    val label: String,
    val points: List<ChartPoint>,
    val color: Color,
)

@Composable
fun PerformanceLineChart(
    series: List<ChartSeries>,
    xLabelFormatter: (Float) -> String,
    yLabelFormatter: (Float) -> String,
    modifier: Modifier = Modifier,
    emptyText: String = "Sem dados para o período",
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f).toArgb()
    val emptyColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                description.isEnabled = false
                setNoDataText(emptyText)
                setNoDataTextColor(emptyColor)
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(false)
                setPinchZoom(false)
                setDrawGridBackground(false)
                setDrawBorders(false)
                minOffset = 0f
                extraBottomOffset = 6f
                legend.isEnabled = false
                axisRight.isEnabled = false
                axisLeft.setDrawAxisLine(false)
                axisLeft.setDrawGridLines(true)
                axisLeft.gridColor = gridColor
                axisLeft.textColor = textColor
                axisLeft.textSize = 9.5f
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.setDrawAxisLine(false)
                xAxis.textColor = textColor
                xAxis.textSize = 9.5f
                xAxis.granularity = 1f
                xAxis.setAvoidFirstLastClipping(true)
            }
        },
        update = { chart ->
            chart.setNoDataText(emptyText)
            chart.setNoDataTextColor(emptyColor)
            chart.axisLeft.gridColor = gridColor
            chart.axisLeft.textColor = textColor
            chart.xAxis.textColor = textColor

            val visibleSeries = series.filter { it.points.isNotEmpty() }
            if (visibleSeries.isEmpty()) {
                chart.clear()
                chart.invalidate()
                return@AndroidView
            }

            val datasets = visibleSeries.map { item ->
                LineDataSet(
                    item.points.map { Entry(it.x, it.y) },
                    item.label,
                ).apply {
                    color = item.color.toArgb()
                    setCircleColor(item.color.toArgb())
                    lineWidth = 2.2f
                    circleRadius = 3.2f
                    setDrawCircles(item.points.size <= 24)
                    setDrawValues(false)
                    setDrawFilled(visibleSeries.size == 1)
                    fillColor = item.color.toArgb()
                    fillAlpha = 20
                    mode = LineDataSet.Mode.LINEAR
                    setDrawHorizontalHighlightIndicator(false)
                    highLightColor = item.color.copy(alpha = 0.5f).toArgb()
                    valueTextColor = textColor
                }
            }

            chart.data = LineData(datasets)
            val allX = visibleSeries.flatMap { it.points }.map { it.x }
            val minX = allX.minOrNull() ?: 0f
            val maxX = allX.maxOrNull() ?: 1f
            chart.xAxis.axisMinimum = if (minX == maxX) minX - 1f else minX
            chart.xAxis.axisMaximum = if (minX == maxX) maxX + 1f else maxX
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = xLabelFormatter(value)
            }
            chart.axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = yLabelFormatter(value)
            }
            chart.xAxis.setLabelCount(minOf(4, maxOf(2, visibleSeries.first().points.size)), false)
            chart.axisLeft.setLabelCount(4, false)
            chart.legend.isEnabled = visibleSeries.size > 1
            if (visibleSeries.size > 1) {
                chart.legend.textColor = textColor
                chart.legend.textSize = 10f
                chart.legend.form = Legend.LegendForm.CIRCLE
                chart.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
                chart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
                chart.legend.orientation = Legend.LegendOrientation.HORIZONTAL
                chart.legend.xEntrySpace = 14f
                chart.extraTopOffset = 24f
            } else {
                chart.extraTopOffset = 4f
            }
            chart.notifyDataSetChanged()
            chart.invalidate()
        },
        modifier = modifier,
    )
}

@Composable
fun Sparkline(
    values: List<BigDecimal>,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width = 64.dp, height = 24.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.minOf { it.toDouble() }
        val max = values.maxOf { it.toDouble() }
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val step = size.width / (values.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { index, value ->
            val x = index * step
            val y = (size.height - ((value.toDouble() - min) / range) * size.height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}
