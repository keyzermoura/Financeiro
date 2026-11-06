package br.com.carteirainvest.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.carteirainvest.presentation.components.ObserveMessages
import br.com.carteirainvest.presentation.screen.alerts.AlertsScreen
import br.com.carteirainvest.presentation.screen.assets.AssetDetailScreen
import br.com.carteirainvest.presentation.screen.assets.AssetFormScreen
import br.com.carteirainvest.presentation.screen.assets.AssetsScreen
import br.com.carteirainvest.presentation.screen.dashboard.DashboardScreen
import br.com.carteirainvest.presentation.screen.cash.CashScreen
import br.com.carteirainvest.presentation.screen.dividends.DividendsScreen
import br.com.carteirainvest.presentation.screen.exchange.ExchangeScreen
import br.com.carteirainvest.presentation.screen.main.MainViewModel
import br.com.carteirainvest.presentation.screen.more.MoreScreen
import br.com.carteirainvest.presentation.screen.more.SecurityScreen
import br.com.carteirainvest.presentation.screen.more.WalletsScreen
import br.com.carteirainvest.presentation.screen.performance.PerformanceScreen
import br.com.carteirainvest.presentation.screen.simulation.SimulationScreen

@Composable
fun MainScaffold(
    onLock: () -> Unit,
    onLogout: () -> Unit,
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val state by mainViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || BottomDestinations.any { it.route == currentRoute }

    ObserveMessages(mainViewModel.messages, snackbarHostState)

    val walletHost = WalletHostState(
        userName = state.userName,
        userEmail = state.userEmail,
        wallets = state.wallets,
        selectedWallet = state.selectedWallet,
        isOnline = state.isOnline,
        onSelectWallet = mainViewModel::selectWallet,
        onCreateWallet = mainViewModel::createWallet,
        onUpdateWallet = mainViewModel::updateWallet,
        onDeleteWallet = mainViewModel::deleteWallet,
    )

    CompositionLocalProvider(
        LocalWalletHost provides walletHost,
        LocalSnackbarHostState provides snackbarHostState,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                    ) {
                        BottomDestinations.forEach { destination ->
                            val selected = currentRoute == destination.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) navController.navigateToTab(destination.route)
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                                        contentDescription = destination.label,
                                    )
                                },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        onOpenAsset = { assetId -> navController.navigate(Routes.assetDetail(assetId)) },
                        onAddAsset = { navController.navigate(Routes.assetForm()) },
                        onManageWallets = { navController.navigate(Routes.WALLETS) },
                        onOpenAlerts = { navController.navigate(Routes.ALERTS) },
                        onOpenPerformance = { navController.navigateToTab(Routes.PERFORMANCE) },
                        onOpenDividends = { navController.navigateToTab(Routes.DIVIDENDS) },
                        onOpenAssets = { navController.navigateToTab(Routes.ASSETS) },
                    )
                }

                composable(Routes.ASSETS) {
                    AssetsScreen(
                        onOpenAsset = { assetId -> navController.navigate(Routes.assetDetail(assetId)) },
                        onAddAsset = { navController.navigate(Routes.assetForm()) },
                        onManageWallets = { navController.navigate(Routes.WALLETS) },
                    )
                }

                composable(Routes.PERFORMANCE) {
                    PerformanceScreen()
                }

                composable(Routes.DIVIDENDS) {
                    DividendsScreen(
                        onOpenAsset = { assetId -> navController.navigate(Routes.assetDetail(assetId)) },
                        onOpenAssets = { navController.navigateToTab(Routes.ASSETS) },
                    )
                }

                composable(Routes.MORE) {
                    MoreScreen(
                        onOpenWallets = { navController.navigate(Routes.WALLETS) },
                        onOpenCash = { navController.navigate(Routes.CASH) },
                        onOpenAlerts = { navController.navigate(Routes.ALERTS) },
                        onOpenExchange = { navController.navigate(Routes.EXCHANGE) },
                        onOpenSimulation = { navController.navigate(Routes.SIMULATION) },
                        onOpenSecurity = { navController.navigate(Routes.SECURITY) },
                        onLock = onLock,
                        onLogout = onLogout,
                    )
                }

                composable(Routes.ALERTS) {
                    AlertsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAssets = { navController.navigateToTab(Routes.ASSETS) },
                    )
                }

                composable(Routes.EXCHANGE) {
                    ExchangeScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.SIMULATION) {
                    SimulationScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.WALLETS) {
                    WalletsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.SECURITY) {
                    SecurityScreen(
                        onBack = { navController.popBackStack() },
                        onLock = onLock,
                    )
                }

                composable(
                    route = Routes.ASSET_DETAIL,
                    arguments = listOf(
                        navArgument("assetId") { type = NavType.StringType },
                    ),
                ) {
                    AssetDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEditAsset = { assetId -> navController.navigate(Routes.assetForm(assetId)) },
                    )
                }

                composable(
                    route = Routes.ASSET_FORM,
                    arguments = listOf(
                        navArgument(Routes.ASSET_FORM_ARG) {
                            type = NavType.StringType
                            defaultValue = Routes.NEW_ASSET
                        },
                    ),
                ) {
                    AssetFormScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { saved ->
                            navController.popBackStack()
                            if (saved.created) {
                                navController.navigate(Routes.assetDetail(saved.assetId))
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
