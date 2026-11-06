package br.com.carteirainvest.presentation.screen.cash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.domain.model.CashTransaction
import br.com.carteirainvest.domain.model.CashTransactionType
import br.com.carteirainvest.presentation.components.CarteiraTopBar
import br.com.carteirainvest.presentation.components.ObserveMessages

@Composable
fun CashScreen(
    onBack: () -> Unit,
    viewModel: CashViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveMessages(viewModel.messages)
    var dialogType by remember { mutableStateOf<CashTransactionType?>(null) }

    Column(Modifier.fillMaxSize()) {
        CarteiraTopBar(title = "Controle de caixa", subtitle = "Saldo e movimentações da carteira", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Saldo disponível", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            BrFormat.currency(state.balance),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (state.hasOpeningBalance) "Saldo atualizado pelas movimentações registradas"
                            else "Cadastre o saldo inicial para começar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            if (state.walletId == null) {
                item { Text("Selecione ou crie uma carteira para controlar o caixa.") }
            } else {
                if (!state.hasOpeningBalance) {
                    item {
                        Button(onClick = { dialogType = CashTransactionType.OPENING_BALANCE }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Cadastrar saldo inicial")
                        }
                    }
                } else {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(onClick = { dialogType = CashTransactionType.DEPOSIT }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.ArrowDownward, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Aportar")
                            }
                            FilledTonalButton(onClick = { dialogType = CashTransactionType.WITHDRAWAL }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Retirar")
                            }
                        }
                    }
                }
                item { Text("Histórico", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                if (state.transactions.isEmpty()) {
                    item { Text("Nenhuma movimentação registrada.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(state.transactions, key = { it.id }) { transaction -> CashTransactionRow(transaction) }
                }
            }
        }
    }

    dialogType?.let { type ->
        CashEntryDialog(type, onDismiss = { dialogType = null }) { amount, description ->
            viewModel.register(type, amount, description)
            dialogType = null
        }
    }
}

@Composable
private fun CashTransactionRow(transaction: CashTransaction) {
    val (label, signed, icon) = when (transaction.type) {
        CashTransactionType.OPENING_BALANCE -> Triple("Saldo inicial", transaction.amount, Icons.Rounded.AccountBalanceWallet)
        CashTransactionType.DEPOSIT -> Triple("Aporte", transaction.amount, Icons.Rounded.ArrowDownward)
        CashTransactionType.WITHDRAWAL -> Triple("Retirada", transaction.amount.negate(), Icons.Rounded.ArrowUpward)
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (signed.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(BrFormat.date(transaction.date), transaction.description).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                BrFormat.signedCurrency(signed),
                fontWeight = FontWeight.Bold,
                color = if (signed.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CashEntryDialog(
    type: CashTransactionType,
    onDismiss: () -> Unit,
    onConfirm: (java.math.BigDecimal, String) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val title = when (type) {
        CashTransactionType.OPENING_BALANCE -> "Saldo inicial"
        CashTransactionType.DEPOSIT -> "Novo aporte"
        CashTransactionType.WITHDRAWAL -> "Nova retirada"
    }
    val amount = BrFormat.parseDecimal(amountText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Valor (R$)") }, placeholder = { Text("0,00") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descrição (opcional)") }, singleLine = true)
                Text(
                    "Data do lançamento: ${BrFormat.date(java.time.LocalDate.now())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amount?.let { onConfirm(it, description) } },
                enabled = amount != null && (if (type == CashTransactionType.OPENING_BALANCE) amount >= java.math.BigDecimal.ZERO else amount > java.math.BigDecimal.ZERO),
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
