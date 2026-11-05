package br.com.carteirainvest.presentation.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import br.com.carteirainvest.domain.model.AlertType
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.TransactionType
import br.com.carteirainvest.presentation.theme.BlueChart
import br.com.carteirainvest.presentation.theme.Emerald500
import br.com.carteirainvest.presentation.theme.Gold500
import br.com.carteirainvest.presentation.theme.OrangeChart
import br.com.carteirainvest.presentation.theme.PurpleChart

fun AssetType.label(): String = when (this) {
    AssetType.STOCK -> "Ação"
    AssetType.FII -> "FII"
    AssetType.ETF -> "ETF"
    AssetType.CRYPTO -> "Cripto"
    AssetType.FIXED_INCOME -> "Renda fixa"
}

fun AssetType.pluralLabel(): String = when (this) {
    AssetType.STOCK -> "Ações"
    AssetType.FII -> "Fundos imobiliários"
    AssetType.ETF -> "ETFs"
    AssetType.CRYPTO -> "Criptomoedas"
    AssetType.FIXED_INCOME -> "Renda fixa"
}

fun AssetType.icon(): ImageVector = when (this) {
    AssetType.STOCK -> Icons.AutoMirrored.Outlined.ShowChart
    AssetType.FII -> Icons.Outlined.Apartment
    AssetType.ETF -> Icons.Outlined.PieChart
    AssetType.CRYPTO -> Icons.Outlined.CurrencyBitcoin
    AssetType.FIXED_INCOME -> Icons.Outlined.AccountBalance
}

fun assetTypeColor(type: AssetType): Color = when (type) {
    AssetType.STOCK -> Emerald500
    AssetType.FII -> BlueChart
    AssetType.ETF -> Gold500
    AssetType.CRYPTO -> OrangeChart
    AssetType.FIXED_INCOME -> PurpleChart
}

fun TransactionType.label(): String = when (this) {
    TransactionType.BUY -> "Compra"
    TransactionType.SELL -> "Venda"
    TransactionType.DIVIDEND -> "Provento"
}

fun TransactionType.isBuy(): Boolean = this == TransactionType.BUY
fun TransactionType.isDividend(): Boolean = this == TransactionType.DIVIDEND

fun AlertType.label(): String = when (this) {
    AlertType.PRICE_CHANGE -> "Variação de preço"
    AlertType.MATURITY -> "Vencimento"
}
