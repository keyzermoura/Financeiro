package br.com.carteirainvest.presentation.screen.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.carteirainvest.core.security.WalletSelectionManager
import br.com.carteirainvest.core.util.FinancialMath
import br.com.carteirainvest.domain.exception.toUserMessage
import br.com.carteirainvest.domain.model.BenchmarkPoint
import br.com.carteirainvest.domain.model.PortfolioSnapshot
import br.com.carteirainvest.domain.model.PortfolioSummary
import br.com.carteirainvest.domain.model.Resource
import br.com.carteirainvest.domain.model.userMessage
import br.com.carteirainvest.domain.usecase.market.FetchBenchmarkUseCase
import br.com.carteirainvest.domain.usecase.market.RefreshWalletQuotesUseCase
import br.com.carteirainvest.domain.usecase.portfolio.ObservePortfolioUseCase
import br.com.carteirainvest.domain.usecase.portfolio.ObserveSnapshotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

enum class PerformancePeriod(val label: String, val range: String, val days: Long?) {
    ONE_MONTH("1M", "1mo", 30),
    THREE_MONTHS("3M", "3mo", 90),
    SIX_MONTHS("6M", "6mo", 180),
    ONE_YEAR("1A", "1y", 365),
    ALL("Tudo", "5y", null),
}

data class PerformanceUiState(
    val period: PerformancePeriod = PerformancePeriod.SIX_MONTHS,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val portfolio: PortfolioSummary = PortfolioSummary.empty(null),
    val snapshots: List<PortfolioSnapshot> = emptyList(),
    val benchmark: List<BenchmarkPoint> = emptyList(),
    val periodReturn: BigDecimal = BigDecimal.ZERO,
    val periodReturnPercent: BigDecimal = BigDecimal.ZERO,
    val benchmarkReturnPercent: BigDecimal = BigDecimal.ZERO,
)

private data class BenchmarkState(
    val points: List<BenchmarkPoint> = emptyList(),
    val period: PerformancePeriod? = null,
    val loading: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val observeSnapshots: ObserveSnapshotsUseCase,
    private val observePortfolio: ObservePortfolioUseCase,
    private val fetchBenchmark: FetchBenchmarkUseCase,
    private val refreshWalletQuotes: RefreshWalletQuotesUseCase,
    walletSelectionManager: WalletSelectionManager,
) : ViewModel() {

    private val selectedWalletId = walletSelectionManager.selectedWalletId
    private val period = MutableStateFlow(PerformancePeriod.SIX_MONTHS)
    private val benchmarkState = MutableStateFlow(BenchmarkState())
    private val refreshing = MutableStateFlow(false)
    private val benchmarkCache = mutableMapOf<PerformancePeriod, List<BenchmarkPoint>>()

    private val snapshotsFlow = selectedWalletId.flatMapLatest { observeSnapshots(it) }
    private val portfolioFlow = selectedWalletId.flatMapLatest { observePortfolio(it) }

    val uiState: StateFlow<PerformanceUiState> = combine(
        snapshotsFlow,
        portfolioFlow,
        period,
        benchmarkState,
        refreshing,
    ) { snapshots, portfolio, selectedPeriod, benchmark, refreshing ->
        val cutoff = selectedPeriod.days?.let { LocalDate.now().minusDays(it) }
        val filtered = if (cutoff == null) {
            snapshots
        } else {
            snapshots.filter { !it.date.isBefore(cutoff) }
        }
        val first = filtered.firstOrNull()
        val last = filtered.lastOrNull()
        val periodReturn = if (first != null && last != null) {
            last.value.subtract(first.value).setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
        val periodReturnPercent = if (first != null && last != null) {
            FinancialMath.percentChange(last.value, first.value)
        } else {
            BigDecimal.ZERO
        }
        val benchmarkVisible = benchmark.points.takeIf { benchmark.period == selectedPeriod }.orEmpty()
        val benchmarkReturn = benchmarkVisible.let { points ->
            val start = points.firstOrNull()?.close
            val end = points.lastOrNull()?.close
            if (start != null && end != null) FinancialMath.percentChange(end, start)
            else BigDecimal.ZERO
        }

        PerformanceUiState(
            period = selectedPeriod,
            isLoading = benchmark.loading,
            isRefreshing = refreshing,
            portfolio = portfolio,
            snapshots = filtered,
            benchmark = benchmarkVisible,
            periodReturn = periodReturn,
            periodReturnPercent = periodReturnPercent,
            benchmarkReturnPercent = benchmarkReturn,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PerformanceUiState(),
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            period.collect { selectedPeriod ->
                loadBenchmark(selectedPeriod, force = false)
            }
        }
    }

    fun selectPeriod(selected: PerformancePeriod) {
        period.value = selected
    }

    fun refresh() {
        viewModelScope.launch {
            if (refreshing.value) return@launch
            refreshing.value = true
            try {
                val walletId = selectedWalletId.value
                if (walletId != null) {
                    when (val result = refreshWalletQuotes(walletId, force = true)) {
                        is Resource.Success -> {
                            if (result.data.messages.isNotEmpty()) {
                                _messages.emit(result.data.messages.first())
                            } else if (result.isStale) {
                                _messages.emit("Sem conexão. Exibindo cotações salvas.")
                            }
                        }

                        is Resource.Failure -> _messages.emit(
                            result.error.userMessage("atualizar o histórico"),
                        )
                    }
                }
                benchmarkCache.remove(period.value)
                loadBenchmark(period.value, force = true)
                _messages.emit("Histórico atualizado.")
            } catch (error: Exception) {
                _messages.emit(error.toUserMessage("atualizar o histórico"))
            } finally {
                refreshing.value = false
            }
        }
    }

    private suspend fun loadBenchmark(selectedPeriod: PerformancePeriod, force: Boolean) {
        if (!force) {
            benchmarkCache[selectedPeriod]?.let { cached ->
                benchmarkState.value = BenchmarkState(cached, selectedPeriod, loading = false)
                return
            }
        }
        benchmarkState.value = BenchmarkState(
            points = benchmarkState.value.points,
            period = benchmarkState.value.period,
            loading = true,
        )
        try {
            when (val result = fetchBenchmark(ticker = "BOVA11", range = selectedPeriod.range)) {
                is Resource.Success -> {
                    benchmarkCache[selectedPeriod] = result.data
                    benchmarkState.value = BenchmarkState(
                        points = result.data,
                        period = selectedPeriod,
                        loading = false,
                    )
                }

                is Resource.Failure -> {
                    benchmarkState.value = BenchmarkState(
                        points = emptyList(),
                        period = selectedPeriod,
                        loading = false,
                    )
                    _messages.emit(
                        result.error.userMessage("carregar o comparativo com o Ibovespa"),
                    )
                }
            }
        } catch (error: Exception) {
            benchmarkState.value = BenchmarkState(emptyList(), selectedPeriod, loading = false)
            _messages.emit(error.toUserMessage("carregar o comparativo com o Ibovespa"))
        }
    }
}
