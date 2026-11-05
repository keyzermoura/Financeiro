package br.com.carteirainvest.domain.usecase.report

import br.com.carteirainvest.domain.model.ReportFile
import br.com.carteirainvest.domain.repository.PortfolioReportExporter
import javax.inject.Inject

class ExportPortfolioReportUseCase @Inject constructor(
    private val exporter: PortfolioReportExporter,
) {
    suspend operator fun invoke(walletId: String): ReportFile = exporter.export(walletId)
}
