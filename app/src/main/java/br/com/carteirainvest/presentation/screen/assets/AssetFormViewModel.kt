package br.com.carteirainvest.presentation.screen.assets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.carteirainvest.core.security.WalletSelectionManager
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.core.util.CryptoCatalog
import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.exception.toUserMessage
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.TransactionType
import br.com.carteirainvest.domain.usecase.asset.CreateAssetUseCase
import br.com.carteirainvest.domain.usecase.asset.GetAssetUseCase
import br.com.carteirainvest.domain.usecase.asset.UpdateAssetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

data class AssetFormUiState(
    val isEditing: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val ticker: String = "",
    val name: String = "",
    val type: AssetType = AssetType.STOCK,
    val cryptoId: String = "",
    val annualRate: String = "",
    val maturityDate: LocalDate? = null,
    val initialQuantity: String = "1",
    val initialPrice: String = "",
    val initialFees: String = "0",
    val initialDate: LocalDate = LocalDate.now(),
    val notes: String = "",
)

data class SavedAsset(val assetId: String, val created: Boolean)

@HiltViewModel
class AssetFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAsset: GetAssetUseCase,
    private val createAsset: CreateAssetUseCase,
    private val updateAsset: UpdateAssetUseCase,
    private val walletSelectionManager: WalletSelectionManager,
) : ViewModel() {

    private val assetId: String = savedStateHandle.get<String>("assetId") ?: NEW_ASSET
    private val isEditing = assetId != NEW_ASSET

    private val _uiState = MutableStateFlow(AssetFormUiState(isEditing = isEditing, loading = isEditing))
    val uiState: StateFlow<AssetFormUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _saved = MutableSharedFlow<SavedAsset>(extraBufferCapacity = 1)
    val saved: SharedFlow<SavedAsset> = _saved.asSharedFlow()

    init {
        if (isEditing) {
            viewModelScope.launch {
                val asset = getAsset(assetId)
                if (asset == null) {
                    _messages.emit("Ativo não encontrado.")
                    _uiState.update { it.copy(loading = false) }
                } else {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            ticker = asset.ticker,
                            name = asset.name,
                            type = asset.type,
                            cryptoId = asset.cryptoId.orEmpty(),
                            annualRate = asset.annualRate?.let(BrFormat::inputDecimal).orEmpty(),
                            maturityDate = asset.maturityDate,
                        )
                    }
                }
            }
        }
    }

    fun updateTicker(value: String) {
        val normalized = value.uppercase().take(40)
        _uiState.update { state ->
            val suggestion = if (state.type == AssetType.CRYPTO) {
                CryptoCatalog.resolveId(normalized, null)
            } else {
                null
            }
            state.copy(
                ticker = normalized,
                cryptoId = when {
                    suggestion == null -> state.cryptoId
                    state.cryptoId.isBlank() -> suggestion
                    else -> state.cryptoId
                },
            )
        }
    }

    fun updateName(value: String) = _uiState.update { it.copy(name = value.take(80)) }

    fun selectType(type: AssetType) {
        _uiState.update { state ->
            val suggestedCryptoId = if (type == AssetType.CRYPTO) {
                CryptoCatalog.resolveId(state.ticker, state.cryptoId)
            } else {
                state.cryptoId
            }
            state.copy(
                type = type,
                cryptoId = suggestedCryptoId.orEmpty(),
            )
        }
    }

    fun updateCryptoId(value: String) =
        _uiState.update { it.copy(cryptoId = value.lowercase().filter { char -> char.isLetterOrDigit() || char == '-' }.take(64)) }

    fun updateAnnualRate(value: String) =
        _uiState.update { it.copy(annualRate = value.filter { char -> char.isDigit() || char == ',' || char == '.' }) }

    fun updateMaturityDate(date: LocalDate?) = _uiState.update { it.copy(maturityDate = date) }

    fun updateInitialQuantity(value: String) = _uiState.update { it.copy(initialQuantity = value) }

    fun updateInitialPrice(value: String) = _uiState.update { it.copy(initialPrice = value) }

    fun updateInitialFees(value: String) = _uiState.update { it.copy(initialFees = value) }

    fun updateInitialDate(date: LocalDate) = _uiState.update { it.copy(initialDate = date) }

    fun updateNotes(value: String) = _uiState.update { it.copy(notes = value.take(500)) }

    fun save() {
        val form = _uiState.value
        if (form.saving || form.loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            try {
                if (form.isEditing) {
                    updateAsset(
                        assetId = assetId,
                        ticker = form.ticker,
                        name = form.name,
                        type = form.type,
                        cryptoId = form.cryptoId.ifBlank { null },
                        annualRate = parseRate(form),
                        maturityDate = form.maturityDate,
                    )
                    _messages.emit("Ativo atualizado.")
                    _saved.emit(SavedAsset(assetId, created = false))
                } else {
                    val walletId = walletSelectionManager.selectedWalletId.value
                        ?: throw ValidationException("Selecione uma carteira antes de cadastrar o ativo.")
                    val quantity = BrFormat.parseDecimal(form.initialQuantity)
                        ?: throw ValidationException("Informe a quantidade inicial.")
                    val price = BrFormat.parseDecimal(form.initialPrice)
                        ?: throw ValidationException("Informe o preço de compra.")
                    val fees = BrFormat.parseDecimal(form.initialFees) ?: BigDecimal.ZERO
                    val asset = createAsset(
                        walletId = walletId,
                        ticker = form.ticker,
                        name = form.name,
                        type = form.type,
                        cryptoId = form.cryptoId.ifBlank { null },
                        annualRate = parseRate(form),
                        maturityDate = form.maturityDate,
                        initialQuantity = quantity,
                        initialPrice = price,
                        initialFees = fees,
                        initialDate = form.initialDate,
                        notes = form.notes,
                    )
                    _messages.emit("Ativo cadastrado.")
                    _saved.emit(SavedAsset(asset.id, created = true))
                }
            } catch (error: Exception) {
                _messages.emit(error.toUserMessage("salvar o ativo"))
            } finally {
                _uiState.update { it.copy(saving = false) }
            }
        }
    }

    private fun parseRate(form: AssetFormUiState): BigDecimal? {
        if (form.type != AssetType.FIXED_INCOME) return null
        if (form.annualRate.isBlank()) return null
        return BrFormat.parseDecimal(form.annualRate)
            ?: throw ValidationException("Informe uma taxa anual válida.")
    }

    private companion object {
        const val NEW_ASSET = "new"
    }
}
