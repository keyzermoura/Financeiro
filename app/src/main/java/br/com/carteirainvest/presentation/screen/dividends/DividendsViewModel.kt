package br.com.carteirainvest.presentation.screen.dividends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.carteirainvest.core.security.WalletSelectionManager
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.exception.toUserMessage
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.DividendRecord
import br.com.carteirainvest.domain.model.DividendsSummary
import br.com.carteirainvest.domain.model.TransactionType
import br.com.carteirainvest.domain.usecase.asset.AddTransactionUseCase
import br.com.carteirainvest.domain.usecase.asset.ObserveAssetsUseCase
import br.com.carteirainvest.domain.usecase.asset.ObserveDividendsUseCase
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class MonthlyDividend(val month: YearMonth, val amount: BigDecimal)

data class DividendsUiState(
    val summary: DividendsSummary = DividendsSummary(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
    val assets: List<Asset> = emptyList(),
    val availableYears: List<Int> = listOf(LocalDate.now().year),
    val selectedYear: Int? = LocalDate.now().year,
    val filteredRecords: List<DividendRecord> = emptyList(),
    val selectedTotal: BigDecimal = BigDecimal.ZERO,
    val monthlySeries: List<MonthlyDividend> = emptyList(),
    val isSaving: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DividendsViewModel @Inject constructor(
    observeDividends: ObserveDividendsUseCase,
    observeAssets: ObserveAssetsUseCase,
    private val addTransaction: AddTransactionUseCase,
    walletSelectionManager: WalletSelectionManager,
) : ViewModel() {

    private val selectedWalletId = walletSelectionManager.selectedWalletId
    private val summaryFlow = selectedWalletId.flatMapLatest { walletId ->
        if (walletId == null) flowOf(DividendsSummary(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
        else observeDividends(walletId)
    }
    private val assetsFlow = selectedWalletId.flatMapLatest { walletId ->
        if (walletId == null) flowOf(emptyList()) else observeAssets(walletId)
    }
    private val selectedYear = MutableStateFlow<Int?>(LocalDate.now().year)
    private val saving = MutableStateFlow(false)

    val uiState: StateFlow<DividendsUiState> = combine(
        summaryFlow,
        assetsFlow,
        selectedYear,
        saving,
    ) { summary, assets, year, saving ->
        val years = buildSet {
            add(LocalDate.now().year)
            summary.records.forEach { add(it.date.year) }
            year?.let(::add)
        }.sortedDescending()

        val filtered = if (year == null) {
            summary.records
        } else {
            summary.records.filter { it.date.year == year }
        }
        val selectedTotal = filtered.fold(BigDecimal.ZERO) { acc, record -> acc.add(record.amount) }
            .setScale(2, RoundingMode.HALF_UP)

        val months = if (year == null) {
            (0 until 12).map { offset -> YearMonth.now().minusMonths((11 - offset).toLong()) }
        } else {
            (1..12).map { month -> YearMonth.of(year, month) }
        }
        val totalsByMonth = filtered.groupBy { YearMonth.from(it.date) }
            .mapValues { (_, records) -> records.fold(BigDecimal.ZERO) { acc, record -> acc.add(record.amount) } }
        val monthlySeries = months.map { month ->
            MonthlyDividend(
                month = month,
                amount = totalsByMonth[month]?.setScale(2, RoundingMode.HALF_UP) ?: BigDecimal.ZERO,
            )
        }

        DividendsUiState(
            summary = summary,
            assets = assets,
            availableYears = years,
            selectedYear = year,
            filteredRecords = filtered,
            selectedTotal = selectedTotal,
            monthlySeries = monthlySeries,
            isSaving = saving,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DividendsUiState(),
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun selectYear(year: Int?) {
        selectedYear.value = year
    }

    fun addDividend(assetId: String, amountText: String, date: LocalDate, notes: String) {
        viewModelScope.launch {
            if (saving.value) return@launch
            saving.value = true
            try {
                if (assetId.isBlank()) throw ValidationException("Selecione o ativo.")
                val amount = BrFormat.parseDecimal(amountText)
                    ?: throw ValidationException("Informe o valor do provento.")
                addTransaction(
                    assetId = assetId,
                    type = TransactionType.DIVIDEND,
                    quantity = BigDecimal.ZERO,
                    unitPrice = BigDecimal.ZERO,
                    fees = BigDecimal.ZERO,
                    dividendAmount = amount,
                    date = date,
                    notes = notes,
                )
                _messages.emit("Provento registrado.")
            } catch (error: Exception) {
                _messages.emit(error.toUserMessage("registrar o provento"))
            } finally {
                saving.value = false
            }
        }
    }
}
