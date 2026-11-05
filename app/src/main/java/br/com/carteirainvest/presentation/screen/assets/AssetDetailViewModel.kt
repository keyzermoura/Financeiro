package br.com.carteirainvest.presentation.screen.assets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.exception.toUserMessage
import br.com.carteirainvest.domain.model.AssetDetail
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.RemoteDividend
import br.com.carteirainvest.domain.model.Resource
import br.com.carteirainvest.domain.model.TransactionType
import br.com.carteirainvest.domain.model.userMessage
import br.com.carteirainvest.domain.usecase.asset.AddTransactionUseCase
import br.com.carteirainvest.domain.usecase.asset.DeleteAssetUseCase
import br.com.carteirainvest.domain.usecase.asset.DeleteTransactionUseCase
import br.com.carteirainvest.domain.usecase.asset.GetAssetUseCase
import br.com.carteirainvest.domain.usecase.asset.ObserveAssetDetailUseCase
import br.com.carteirainvest.domain.usecase.asset.UpdateTransactionUseCase
import br.com.carteirainvest.domain.usecase.market.FetchRemoteDividendsUseCase
import br.com.carteirainvest.domain.usecase.market.ObserveRemoteDividendsUseCase
import br.com.carteirainvest.domain.usecase.market.RefreshAssetQuotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AssetDetailUiState(
    val isLoading: Boolean = true,
    val detail: AssetDetail? = null,
    val remoteDividends: List<RemoteDividend> = emptyList(),
    val isRefreshingQuote: Boolean = false,
    val isLoadingRemoteDividends: Boolean = false,
    val isSavingTransaction: Boolean = false,
    val isDeletingAsset: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeAssetDetail: ObserveAssetDetailUseCase,
    observeRemoteDividends: ObserveRemoteDividendsUseCase,
    private val fetchRemoteDividendsUseCase: FetchRemoteDividendsUseCase,
    private val refreshAssetQuotes: RefreshAssetQuotesUseCase,
    private val addTransaction: AddTransactionUseCase,
    private val updateTransaction: UpdateTransactionUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val deleteAssetUseCase: DeleteAssetUseCase,
    private val getAsset: GetAssetUseCase,
) : ViewModel() {

    private val assetId: String = checkNotNull(savedStateHandle["assetId"]) {
        "AssetDetailViewModel exige o argumento assetId."
    }

    private val detailFlow = observeAssetDetail(assetId)
    private val remoteDividendsFlow = detailFlow
        .map { it?.asset?.ticker }
        .distinctUntilChanged()
        .flatMapLatest { ticker ->
            if (ticker == null) flowOf(emptyList())
            else observeRemoteDividends(ticker)
        }

    private val refreshingQuote = MutableStateFlow(false)
    private val loadingRemoteDividends = MutableStateFlow(false)
    private val savingTransaction = MutableStateFlow(false)
    private val deletingAsset = MutableStateFlow(false)

    private val flagsFlow = combine(
        refreshingQuote,
        loadingRemoteDividends,
        savingTransaction,
        deletingAsset,
    ) { refreshing, loadingDividends, saving, deleting ->
        DetailFlags(refreshing, loadingDividends, saving, deleting)
    }

    val uiState: StateFlow<AssetDetailUiState> = combine(
        detailFlow,
        remoteDividendsFlow,
        flagsFlow,
    ) { detail, remoteDividends, flags ->
        AssetDetailUiState(
            isLoading = false,
            detail = detail,
            remoteDividends = remoteDividends,
            isRefreshingQuote = flags.refreshingQuote,
            isLoadingRemoteDividends = flags.loadingRemoteDividends,
            isSavingTransaction = flags.savingTransaction,
            isDeletingAsset = flags.deletingAsset,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssetDetailUiState(),
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    init {
        viewModelScope.launch {
            val asset = getAsset(assetId) ?: return@launch
            if (asset.type == AssetType.STOCK || asset.type == AssetType.FII || asset.type == AssetType.ETF) {
                loadRemoteDividends(asset.ticker, force = false, quiet = true)
            }
        }
    }

    fun refreshQuote() {
        viewModelScope.launch {
            if (refreshingQuote.value) return@launch
            refreshingQuote.value = true
            try {
                when (val result = refreshAssetQuotes(assetId, force = true)) {
                    is Resource.Success -> {
                        val summary = result.data
                        _messages.emit(
                            when {
                                summary.messages.isNotEmpty() -> summary.messages.first()
                                result.isStale -> "Sem conexão. Exibindo a cotação salva."
                                summary.updated > 0 -> "Cotação atualizada."
                                else -> "Não há uma nova cotação disponível."
                            },
                        )
                    }

                    is Resource.Failure -> _messages.emit(
                        result.error.userMessage("atualizar a cotação"),
                    )
                }
            } catch (error: Exception) {
                _messages.emit(error.toUserMessage("atualizar a cotação"))
            } finally {
                refreshingQuote.value = false
            }
        }
    }

    fun refreshRemoteDividends() {
        val ticker = uiState.value.detail?.asset?.ticker ?: return
        viewModelScope.launch { loadRemoteDividends(ticker, force = true, quiet = false) }
    }

    private suspend fun loadRemoteDividends(ticker: String, force: Boolean, quiet: Boolean) {
        if (loadingRemoteDividends.value) return
        loadingRemoteDividends.value = true
        try {
            when (val result = fetchRemoteDividendsUseCase(ticker, force)) {
                is Resource.Success -> {
                    if (!quiet && force) {
                        _messages.emit("Proventos anunciados atualizados.")
                    }
                }

                is Resource.Failure -> {
                    if (!quiet) {
                        _messages.emit(
                            result.error.userMessage("carregar os proventos anunciados"),
                        )
                    }
                }
            }
        } catch (error: Exception) {
            if (!quiet) _messages.emit(error.toUserMessage("carregar os proventos anunciados"))
        } finally {
            loadingRemoteDividends.value = false
        }
    }

    fun saveTransaction(
        transactionId: String?,
        type: TransactionType,
        quantityText: String,
        unitPriceText: String,
        feesText: String,
        dividendText: String,
        date: LocalDate,
        notes: String,
    ) {
        viewModelScope.launch {
            if (savingTransaction.value) return@launch
            savingTransaction.value = true
            try {
                val quantity = BrFormat.parseDecimal(quantityText)
                val unitPrice = BrFormat.parseDecimal(unitPriceText)
                val fees = BrFormat.parseDecimal(feesText) ?: java.math.BigDecimal.ZERO
                val dividend = BrFormat.parseDecimal(dividendText)

                if (type == TransactionType.DIVIDEND) {
                    if (dividend == null) throw ValidationException("Informe o valor do provento.")
                } else {
                    if (quantity == null) throw ValidationException("Informe a quantidade.")
                    if (unitPrice == null) throw ValidationException("Informe o preço unitário.")
                }

                if (transactionId == null) {
                    addTransaction(
                        assetId = assetId,
                        type = type,
                        quantity = quantity ?: java.math.BigDecimal.ZERO,
                        unitPrice = unitPrice ?: java.math.BigDecimal.ZERO,
                        fees = fees,
                        dividendAmount = dividend,
                        date = date,
                        notes = notes,
                    )
                    _messages.emit("Lançamento adicionado.")
                } else {
                    updateTransaction(
                        transactionId = transactionId,
                        assetId = assetId,
                        type = type,
                        quantity = quantity ?: java.math.BigDecimal.ZERO,
                        unitPrice = unitPrice ?: java.math.BigDecimal.ZERO,
                        fees = fees,
                        dividendAmount = dividend,
                        date = date,
                        notes = notes,
                    )
                    _messages.emit("Lançamento atualizado.")
                }
            } catch (error: Exception) {
                _messages.emit(error.toUserMessage("salvar o lançamento"))
            } finally {
                savingTransaction.value = false
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            runCatching { deleteTransactionUseCase(transactionId) }
                .onSuccess { _messages.emit("Lançamento excluído.") }
                .onFailure { _messages.emit(it.toUserMessage("excluir o lançamento")) }
        }
    }

    fun deleteAsset() {
        viewModelScope.launch {
            if (deletingAsset.value) return@launch
            deletingAsset.value = true
            try {
                deleteAssetUseCase(assetId)
                _deleted.emit(Unit)
            } catch (error: Exception) {
                _messages.emit(error.toUserMessage("excluir o ativo"))
            } finally {
                deletingAsset.value = false
            }
        }
    }
}

private data class DetailFlags(
    val refreshingQuote: Boolean,
    val loadingRemoteDividends: Boolean,
    val savingTransaction: Boolean,
    val deletingAsset: Boolean,
)
