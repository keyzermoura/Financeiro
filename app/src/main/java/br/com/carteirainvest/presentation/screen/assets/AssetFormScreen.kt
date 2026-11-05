package br.com.carteirainvest.presentation.screen.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.carteirainvest.core.util.CryptoCatalog
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.presentation.components.CarteiraTopBar
import br.com.carteirainvest.presentation.components.DatePickerField
import br.com.carteirainvest.presentation.components.DecimalKeyboard
import br.com.carteirainvest.presentation.components.InfoBanner
import br.com.carteirainvest.presentation.components.ObserveMessages
import br.com.carteirainvest.presentation.components.SectionHeader
import br.com.carteirainvest.presentation.util.label

@Composable
fun AssetFormScreen(
    onBack: () -> Unit,
    onSaved: (SavedAsset) -> Unit,
    viewModel: AssetFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveMessages(viewModel.messages)

    LaunchedEffect(Unit) {
        viewModel.saved.collect { saved -> onSaved(saved) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CarteiraTopBar(
            title = if (state.isEditing) "Editar ativo" else "Novo ativo",
            subtitle = if (state.isEditing) state.ticker else "Adicione um ativo à carteira",
            onBack = onBack,
        )

        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeader(title = "Identificação")
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AssetType.entries) { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { viewModel.selectType(type) },
                            label = { Text(type.label()) },
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.ticker,
                    onValueChange = viewModel::updateTicker,
                    label = { Text("Ticker ou símbolo") },
                    placeholder = {
                        Text(
                            if (state.type == AssetType.CRYPTO) "Ex.: BTC" else "Ex.: PETR4",
                        )
                    },
                    supportingText = {
                        Text(
                            when (state.type) {
                                AssetType.STOCK -> "Código negociado na B3. Ex.: PETR4, VALE3."
                                AssetType.FII -> "Código do fundo imobiliário. Ex.: HGLG11."
                                AssetType.ETF -> "Código do ETF. Ex.: BOVA11, IVVB11."
                                AssetType.CRYPTO -> "Símbolo usado na carteira. Ex.: BTC, ETH."
                                AssetType.FIXED_INCOME -> "Identificação livre. Ex.: TESOURO_IPCA_2029."
                            },
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    label = { Text("Nome do ativo (opcional)") },
                    placeholder = { Text("Ex.: Petrobras PN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.type == AssetType.CRYPTO) {
                item {
                    InfoBanner(
                        text = "A CoinGecko usa um identificador próprio. Os símbolos mais comuns " +
                            "já são preenchidos automaticamente.",
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.cryptoId,
                        onValueChange = viewModel::updateCryptoId,
                        label = { Text("ID CoinGecko") },
                        placeholder = { Text("Ex.: bitcoin") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CryptoCatalog.popular.take(10)) { (ticker, id) ->
                            FilterChip(
                                selected = state.cryptoId == id,
                                onClick = {
                                    viewModel.updateTicker(ticker)
                                    viewModel.updateCryptoId(id)
                                },
                                label = { Text(ticker) },
                            )
                        }
                    }
                }
            }

            if (state.type == AssetType.FIXED_INCOME) {
                item {
                    SectionHeader(title = "Renda fixa")
                }
                item {
                    InfoBanner(
                        text = "Sem fonte pública de preços para renda fixa. O app estima a evolução " +
                            "pela taxa anual informada desde a primeira compra.",
                        icon = Icons.Rounded.Info,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.annualRate,
                            onValueChange = viewModel::updateAnnualRate,
                            label = { Text("Taxa anual") },
                            suffix = { Text("%") },
                            singleLine = true,
                            keyboardOptions = DecimalKeyboard,
                            modifier = Modifier.weight(1f),
                        )
                        DatePickerField(
                            label = "Vencimento",
                            date = state.maturityDate,
                            onDateSelected = viewModel::updateMaturityDate,
                            supportingText = "Opcional",
                            modifier = Modifier.weight(1.35f),
                            allowFuture = true,
                        )
                    }
                }
            }

            if (!state.isEditing) {
                item {
                    SectionHeader(title = "Primeira compra")
                }
                item {
                    InfoBanner(
                        text = "Informe a quantidade e o preço pago. Você poderá adicionar outras " +
                            "compras, vendas e proventos depois.",
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.initialQuantity,
                            onValueChange = viewModel::updateInitialQuantity,
                            label = { Text("Quantidade") },
                            singleLine = true,
                            keyboardOptions = DecimalKeyboard,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.initialPrice,
                            onValueChange = viewModel::updateInitialPrice,
                            label = { Text("Preço unitário") },
                            prefix = { Text("R$ ") },
                            singleLine = true,
                            keyboardOptions = DecimalKeyboard,
                            modifier = Modifier.weight(1.2f),
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.initialFees,
                            onValueChange = viewModel::updateInitialFees,
                            label = { Text("Taxas") },
                            prefix = { Text("R$ ") },
                            singleLine = true,
                            keyboardOptions = DecimalKeyboard,
                            modifier = Modifier.weight(1f),
                        )
                        DatePickerField(
                            label = "Data da compra",
                            date = state.initialDate,
                            onDateSelected = viewModel::updateInitialDate,
                            allowFuture = false,
                            modifier = Modifier.weight(1.45f),
                        )
                    }
                }
            } else {
                item {
                    InfoBanner(
                        text = "Para alterar lançamentos, abra o ativo e edite a transação desejada.",
                    )
                }
            }

            if (!state.isEditing) {
                item {
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = viewModel::updateNotes,
                        label = { Text("Observações (opcional)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = viewModel::save,
                    enabled = !state.saving && state.ticker.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(20.dp)
                                .height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.isEditing) "Salvar alterações" else "Cadastrar ativo",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            item {
                Text(
                    "Cotações de ações, FIIs e ETFs vêm da Brapi. Criptomoedas são cotadas em reais " +
                        "pela CoinGecko. O cache local mantém os dados disponíveis sem internet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
