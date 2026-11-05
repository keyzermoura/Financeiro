package br.com.carteirainvest.data.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.core.util.IoDispatcher
import br.com.carteirainvest.domain.exception.ReportException
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.PortfolioPosition
import br.com.carteirainvest.domain.model.ReportFile
import br.com.carteirainvest.domain.model.TransactionType
import br.com.carteirainvest.domain.repository.AssetRepository
import br.com.carteirainvest.domain.repository.PortfolioReportExporter
import br.com.carteirainvest.domain.repository.PortfolioRepository
import br.com.carteirainvest.domain.repository.WalletRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfPortfolioReportExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletRepository: WalletRepository,
    private val portfolioRepository: PortfolioRepository,
    private val assetRepository: AssetRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PortfolioReportExporter {

    override suspend fun export(walletId: String): ReportFile = withContext(ioDispatcher) {
        val wallet = walletRepository.getWallet(walletId)
            ?: throw ReportException("Carteira não encontrada para exportação.")
        val portfolio = portfolioRepository.getPortfolio(walletId)
        val assets = assetRepository.getAssets(walletId)
        val transactions = assetRepository.getWalletTransactions(walletId)
        val assetsById = assets.associateBy { it.id }
        val dividends = transactions
            .filter { it.type == TransactionType.DIVIDEND }
            .mapNotNull { transaction ->
                val amount = transaction.dividendAmount ?: return@mapNotNull null
                val ticker = assetsById[transaction.assetId]?.ticker ?: "—"
                Triple(transaction.date, ticker, amount)
            }
            .sortedByDescending { it.first }
        val totalDividends = dividends.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.third) }

        val directory = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeName = wallet.name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(32)
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val file = File(directory, "relatorio_${safeName}_$stamp.pdf")

        val document = PdfDocument()
        try {
            val renderer = Renderer(
                document = document,
                walletName = wallet.name,
                generatedAt = LocalDateTime.now(),
                portfolio = portfolio,
                dividends = dividends,
                totalDividends = totalDividends,
            )
            renderer.render()
            FileOutputStream(file).use { document.writeTo(it) }
        } catch (error: Exception) {
            file.delete()
            throw ReportException("Não foi possível gerar o PDF: ${error.message}", error)
        } finally {
            runCatching { document.close() }
        }

        ReportFile(
            absolutePath = file.absolutePath,
            displayName = file.name,
            walletName = wallet.name,
        )
    }

    private class Renderer(
        private val document: PdfDocument,
        private val walletName: String,
        private val generatedAt: LocalDateTime,
        private val portfolio: br.com.carteirainvest.domain.model.PortfolioSummary,
        private val dividends: List<Triple<LocalDate, String, BigDecimal>>,
        private val totalDividends: BigDecimal,
    ) {
        private val pageWidth = 595
        private val pageHeight = 842
        private val margin = 36f
        private val contentWidth = pageWidth - margin * 2
        private val bottomLimit = pageHeight - 52f

        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas = Canvas()
        private var y = 0f

        private val normal = Typeface.create("sans-serif", Typeface.NORMAL)
        private val bold = Typeface.create("sans-serif", Typeface.BOLD)

        private val paint = Paint().apply { isAntiAlias = true }

        fun render() {
            beginPage()
            drawPageOneHeader()
            drawSummary()
            drawAllocation()
            drawPositions()
            drawDividends()
            drawDisclaimer()
            finishPage()
        }

        private fun beginPage() {
            finishOpenPage()
            pageNumber += 1
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = document.startPage(info)
            canvas = page!!.canvas
            canvas.drawColor(Color.WHITE)
            drawRunningHeader()
            y = if (pageNumber == 1) 142f else 84f
        }

        private fun finishOpenPage() {
            val current = page ?: return
            drawFooter()
            document.finishPage(current)
            page = null
        }

        private fun finishPage() {
            finishOpenPage()
        }

        private fun ensureSpace(needed: Float) {
            if (y + needed > bottomLimit) {
                beginPage()
            }
        }

        private fun drawRunningHeader() {
            paint.color = Color.rgb(11, 61, 46)
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), if (pageNumber == 1) 118f else 52f, paint)
            paint.color = Color.WHITE
            paint.typeface = bold
            paint.textSize = if (pageNumber == 1) 24f else 14f
            canvas.drawText("Relatório da Carteira", margin, if (pageNumber == 1) 48f else 32f, paint)
            paint.typeface = normal
            paint.textSize = 10.5f
            paint.color = Color.argb(220, 255, 255, 255)
            if (pageNumber == 1) {
                canvas.drawText(walletName, margin, 72f, paint)
                paint.textSize = 9f
                canvas.drawText(
                    "Gerado em ${generatedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))}",
                    margin,
                    92f,
                    paint,
                )
            } else {
                canvas.drawText("$walletName · continuação", margin, 48f, paint)
            }
            paint.color = Color.rgb(245, 201, 92)
            canvas.drawRect(0f, if (pageNumber == 1) 113f else 47f, pageWidth.toFloat(), if (pageNumber == 1) 118f else 52f, paint)
        }

        private fun drawPageOneHeader() {
            // Header already drawn by beginPage; this is intentionally empty to keep layout explicit.
        }

        private fun drawFooter() {
            paint.color = Color.rgb(170, 180, 174)
            paint.strokeWidth = 0.6f
            canvas.drawLine(margin, pageHeight - 36f, pageWidth - margin, pageHeight - 36f, paint)
            paint.color = Color.rgb(120, 130, 124)
            paint.typeface = normal
            paint.textSize = 8f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(
                "Carteira Invest · relatório gerado localmente",
                margin,
                pageHeight - 22f,
                paint,
            )
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Página $pageNumber", pageWidth - margin, pageHeight - 22f, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        private fun sectionTitle(title: String) {
            ensureSpace(38f)
            y += 6f
            paint.color = Color.rgb(11, 61, 46)
            paint.typeface = bold
            paint.textSize = 12.5f
            canvas.drawText(title, margin, y, paint)
            paint.color = Color.rgb(0, 108, 76)
            canvas.drawRect(margin, y + 5f, margin + 32f, y + 7.5f, paint)
            y += 20f
            paint.typeface = normal
        }

        private fun drawSummary() {
            sectionTitle("Resumo patrimonial")
            val cards = listOf(
                "Patrimônio atual" to BrFormat.currency(portfolio.totalValue),
                "Custo investido" to BrFormat.currency(portfolio.totalInvested),
                "Rentabilidade" to "${BrFormat.percent(portfolio.totalGainPercent, signed = true)} · ${BrFormat.signedCurrency(portfolio.totalGain)}",
                "Proventos recebidos" to BrFormat.currency(totalDividends),
            )
            val cardWidth = (contentWidth - 10f) / 2
            val cardHeight = 50f
            cards.chunked(2).forEach { row ->
                ensureSpace(cardHeight + 8f)
                row.forEachIndexed { index, card ->
                    val x = margin + index * (cardWidth + 10f)
                    paint.color = Color.rgb(244, 249, 246)
                    canvas.drawRoundRect(x, y, x + cardWidth, y + cardHeight, 6f, 6f, paint)
                    paint.color = Color.rgb(110, 124, 116)
                    paint.typeface = normal
                    paint.textSize = 8f
                    canvas.drawText(card.first, x + 10f, y + 16f, paint)
                    paint.color = Color.rgb(18, 31, 25)
                    paint.typeface = bold
                    paint.textSize = if (card.second.length > 28) 11f else 13f
                    canvas.drawText(
                        ellipsize(card.second, paint, cardWidth - 20f),
                        x + 10f,
                        y + 36f,
                        paint,
                    )
                }
                y += cardHeight + 8f
            }
            y += 4f
        }

        private fun drawAllocation() {
            if (portfolio.allocation.isEmpty()) return
            sectionTitle("Alocação por categoria")
            portfolio.allocation.forEach { slice ->
                ensureSpace(25f)
                val label = typeLabel(slice.type)
                paint.typeface = bold
                paint.textSize = 9f
                paint.color = Color.rgb(30, 46, 38)
                canvas.drawText(label, margin, y, paint)
                paint.typeface = normal
                paint.color = Color.rgb(80, 96, 87)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(
                    "${BrFormat.percent(slice.percentage)} · ${BrFormat.currency(slice.value)}",
                    pageWidth - margin,
                    y,
                    paint,
                )
                paint.textAlign = Paint.Align.LEFT
                y += 7f
                paint.color = Color.rgb(225, 236, 230)
                canvas.drawRoundRect(margin, y, margin + contentWidth, y + 5f, 2.5f, 2.5f, paint)
                paint.color = Color.rgb(0, 108, 76)
                val fill = contentWidth * (slice.percentage.toFloat() / 100f).coerceIn(0f, 1f)
                if (fill > 0f) {
                    canvas.drawRoundRect(margin, y, margin + fill, y + 5f, 2.5f, 2.5f, paint)
                }
                y += 16f
            }
            y += 4f
        }

        private fun drawPositions() {
            sectionTitle("Posições abertas")
            if (portfolio.positions.isEmpty()) {
                paint.typeface = normal
                paint.color = Color.rgb(100, 112, 105)
                paint.textSize = 9f
                canvas.drawText("Nenhuma posição em aberto.", margin, y, paint)
                y += 20f
                return
            }
            drawPositionsHeader()
            portfolio.positions.forEach { position ->
                if (y + 22f > bottomLimit) {
                    beginPage()
                    paint.typeface = bold
                    paint.color = Color.rgb(11, 61, 46)
                    paint.textSize = 11f
                    canvas.drawText("Posições (continuação)", margin, y, paint)
                    y += 16f
                    drawPositionsHeader()
                }
                drawPositionRow(position)
            }
            y += 8f
        }

        private fun drawPositionsHeader() {
            paint.color = Color.rgb(11, 61, 46)
            paint.typeface = bold
            paint.textSize = 7.8f
            canvas.drawText("Ativo", margin, y, paint)
            textRight("Qtd.", 175f, y)
            textRight("Preço médio", 255f, y)
            textRight("Preço atual", 340f, y)
            textRight("Valor", 435f, y)
            textRight("Resultado", pageWidth - margin, y)
            y += 4f
            paint.color = Color.rgb(212, 224, 217)
            paint.strokeWidth = 0.6f
            canvas.drawLine(margin, y, pageWidth - margin, y, paint)
            y += 12f
            paint.typeface = normal
        }

        private fun drawPositionRow(position: PortfolioPosition) {
            paint.textSize = 8.6f
            paint.typeface = bold
            paint.color = Color.rgb(30, 46, 38)
            canvas.drawText(ellipsize(position.asset.ticker, paint, 80f), margin, y, paint)
            paint.typeface = normal
            paint.textSize = 8.2f
            paint.color = Color.rgb(70, 84, 76)
            textRight(BrFormat.quantity(position.quantity), 175f, y)
            textRight(BrFormat.currency(position.averagePrice), 255f, y)
            textRight(BrFormat.currency(position.currentPrice), 340f, y)
            textRight(BrFormat.currency(position.currentValue), 435f, y)
            paint.color = resultColor(position.profitLoss)
            textRight(
                "${BrFormat.signedCurrency(position.profitLoss)} (${BrFormat.percent(position.profitLossPercent, signed = true)})",
                pageWidth - margin,
                y,
            )
            paint.color = Color.rgb(236, 242, 238)
            canvas.drawLine(margin, y + 5f, pageWidth - margin, y + 5f, paint)
            y += 20f
        }

        private fun drawDividends() {
            if (dividends.isEmpty()) return
            sectionTitle("Proventos recebidos")
            paint.typeface = normal
            paint.textSize = 9f
            paint.color = Color.rgb(70, 84, 76)
            canvas.drawText(
                "Total no histórico: ${BrFormat.currency(totalDividends)} · ${dividends.size} lançamento(s)",
                margin,
                y,
                paint,
            )
            y += 18f
            paint.typeface = bold
            paint.textSize = 7.8f
            paint.color = Color.rgb(90, 104, 96)
            canvas.drawText("Data", margin, y, paint)
            canvas.drawText("Ativo", 160f, y, paint)
            textRight("Valor", pageWidth - margin, y)
            y += 4f
            paint.color = Color.rgb(212, 224, 217)
            canvas.drawLine(margin, y, pageWidth - margin, y, paint)
            y += 12f
            paint.typeface = normal

            dividends.take(15).forEach { (date, ticker, amount) ->
                if (y + 18f > bottomLimit) {
                    beginPage()
                    paint.typeface = bold
                    paint.textSize = 11f
                    paint.color = Color.rgb(11, 61, 46)
                    canvas.drawText("Proventos (continuação)", margin, y, paint)
                    y += 18f
                    paint.typeface = normal
                }
                paint.textSize = 8.4f
                paint.color = Color.rgb(70, 84, 76)
                canvas.drawText(BrFormat.date(date), margin, y, paint)
                paint.color = Color.rgb(30, 46, 38)
                paint.typeface = bold
                canvas.drawText(ticker, 160f, y, paint)
                paint.typeface = normal
                paint.color = Color.rgb(0, 108, 76)
                textRight(BrFormat.currency(amount), pageWidth - margin, y)
                y += 18f
            }
            if (dividends.size > 15) {
                paint.color = Color.rgb(110, 124, 116)
                paint.textSize = 8f
                canvas.drawText("... e mais ${dividends.size - 15} lançamento(s).", margin, y, paint)
                y += 18f
            }
            y += 6f
        }

        private fun drawDisclaimer() {
            ensureSpace(70f)
            y += 8f
            paint.color = Color.rgb(225, 236, 230)
            canvas.drawRoundRect(margin, y, pageWidth - margin, y + 52f, 6f, 6f, paint)
            paint.color = Color.rgb(100, 112, 105)
            paint.typeface = normal
            paint.textSize = 7.6f
            drawWrapped(
                "Este relatório foi gerado localmente com os dados cadastrados pelo usuário. " +
                    "Cotações de mercado podem estar defasadas em relação ao horário de geração. " +
                    "Rentabilidade passada não representa garantia de rentabilidade futura. " +
                    "Confira as informações junto à sua corretora antes de tomar decisões de investimento.",
                margin + 10f,
                y + 16f,
                contentWidth - 20f,
                11f,
            )
            y += 62f
        }

        private fun drawWrapped(text: String, x: Float, startY: Float, maxWidth: Float, lineHeight: Float): Float {
            var currentY = startY
            val words = text.split(" ")
            var line = ""
            words.forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                    canvas.drawText(line, x, currentY, paint)
                    currentY += lineHeight
                    line = word
                } else {
                    line = candidate
                }
            }
            if (line.isNotEmpty()) {
                canvas.drawText(line, x, currentY, paint)
                currentY += lineHeight
            }
            return currentY
        }

        private fun textRight(text: String, x: Float, y: Float) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(text, x, y, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            var end = text.length
            while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
                end -= 1
            }
            return text.substring(0, end) + "…"
        }

        private fun resultColor(value: BigDecimal): Int =
            when {
                value.signum() > 0 -> Color.rgb(0, 130, 85)
                value.signum() < 0 -> Color.rgb(190, 55, 55)
                else -> Color.rgb(70, 84, 76)
            }

        private fun typeLabel(type: AssetType): String = when (type) {
            AssetType.STOCK -> "Ações"
            AssetType.FII -> "Fundos imobiliários"
            AssetType.ETF -> "ETFs"
            AssetType.CRYPTO -> "Criptomoedas"
            AssetType.FIXED_INCOME -> "Renda fixa"
        }
    }
}
