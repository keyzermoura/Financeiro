package br.com.carteirainvest.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val DASHBOARD = "dashboard"
    const val ASSETS = "assets"
    const val PERFORMANCE = "performance"
    const val DIVIDENDS = "dividends"
    const val MORE = "more"

    const val ALERTS = "alerts"
    const val EXCHANGE = "exchange"
    const val SIMULATION = "simulation"
    const val WALLETS = "wallets"
    const val SECURITY = "security"

    const val ASSET_DETAIL = "asset/{assetId}"
    const val ASSET_FORM = "asset_form?assetId={assetId}"
    const val ASSET_FORM_ARG = "assetId"
    const val NEW_ASSET = "new"

    fun assetDetail(assetId: String): String = "asset/$assetId"

    fun assetForm(assetId: String = NEW_ASSET): String = "asset_form?assetId=$assetId"
}

data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

val BottomDestinations = listOf(
    BottomDestination(Routes.DASHBOARD, "Início", Icons.Outlined.Home, Icons.Rounded.Home),
    BottomDestination(Routes.ASSETS, "Ativos", Icons.AutoMirrored.Outlined.ShowChart, Icons.AutoMirrored.Rounded.ShowChart),
    BottomDestination(Routes.PERFORMANCE, "Evolução", Icons.Outlined.Timeline, Icons.Rounded.Timeline),
    BottomDestination(Routes.DIVIDENDS, "Proventos", Icons.Outlined.Payments, Icons.Rounded.Payments),
    BottomDestination(Routes.MORE, "Mais", Icons.Outlined.MoreHoriz, Icons.Rounded.MoreHoriz),
)
