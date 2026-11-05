package br.com.carteirainvest.presentation.screen.alerts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.domain.model.AlertRule
import br.com.carteirainvest.domain.model.AlertType
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.presentation.components.AppCard
import br.com.carteirainvest.presentation.components.CarteiraTopBar
import br.com.carteirainvest.presentation.components.DecimalKeyboard
import br.com.carteirainvest.presentation.components.EmptyState
import br.com.carteirainvest.presentation.components.ObserveMessages
import br.com.carteirainvest.presentation.components.SectionHeader
import br.com.carteirainvest.presentation.navigation.LocalWalletHost
import br.com.carteirainvest.presentation.theme.LocalExtendedColors
import br.com.carteirainvest.presentation.util.label
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    onOpenAssets: () -> Unit,
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val host = LocalWalletHost.current
    val context = LocalContext.current
    ObserveMessages(viewModel.messages)

    var showCreateDialog by remember { mutableStateOf(false) }
    var deletingAlert by remember { mutableStateOf<AlertRule?>(null) }
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> notificationsGranted = granted },
    )

    Column(modifier = Modifier.fillMaxSize()) {
        CarteiraTopBar(
            title = "Alertas",
            subtitle = host?.selectedWallet?.name ?: "Avisos da carteira",
            onBack = onBack,
            actions = {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = viewModel::checkNow) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Verificar alertas agora")
                    }
                }
            },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!notificationsGranted) {
                    item {
                        NotificationPermissionCard(
                            onRequest = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                        )
                    }
                }

                item {
                    AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Verificação automática", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "As cotações são atualizadas a cada 30 minutos em segundo plano " +
                                        "e os alertas são avaliados mesmo sem internet, usando o cache local.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Seus alertas",
                        actionText = "${state.alerts.count { it.enabled }} ativo(s)",
                    )
                }

                if (state.alerts.isEmpty()) {
                    item {
                        AppCard {
                            EmptyState(
                                icon = Icons.Rounded.Notifications,
                                title = "Nenhum alerta configurado",
                                message = "Crie avisos de variação de preço para ações, FIIs, ETFs e criptos " +
                                    "ou de vencimento para títulos de renda fixa.",
                                actionLabel = "Criar alerta",
                                onAction = { showCreateDialog = true },
                            )
                        }
                    }
                } else {
                    items(state.alerts, key = { it.id }) { alert ->
                        AlertCard(
                            alert = alert,
                            asset = state.assets.firstOrNull { it.id == alert.assetId },
                            onToggle = { enabled -> viewModel.toggleAlert(alert, enabled) },
                            onDelete = { deletingAlert = alert },
                        )
                    }
                }

                item {
                    Text(
                        "Alertas de preço usam a variação percentual do dia da cotação mais recente. " +
                            "Depois de disparado, o mesmo alerta aguarda 12 horas antes de notificar novamente. " +
                            "Alertas de vencimento têm carência de 7 dias.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Criar alerta")
            }
        }
    }

    if (showCreateDialog) {
        CreateAlertDialog(
            assets = state.assets,
            isBusy = state.isBusy,
            onDismiss = { showCreateDialog = false },
            onOpenAssets = {
                showCreateDialog = false
                onOpenAssets()
            },
            onConfirm = { assetId, type, threshold ->
                viewModel.createAlert(assetId, type, threshold)
                showCreateDialog = false
            },
        )
    }

    deletingAlert?.let { alert ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deletingAlert = null },
            title = { Text("Excluir alerta?") },
            text = {
                Text(
                    "O alerta de ${alert.type.label().lowercase()} para ${alert.ticker} será removido.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAlert(alert)
                        deletingAlert = null
                    },
                ) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAlert = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun NotificationPermissionCard(onRequest: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Notificações desativadas",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "Autorize as notificações para receber os alertas no aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            TextButton(onClick = onRequest) { Text("Ativar") }
        }
    }
}

@Composable
private fun AlertCard(
    alert: AlertRule,
    asset: Asset?,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val isMaturity = alert.type == AlertType.MATURITY
    val icon = if (isMaturity) Icons.Rounded.Event else Icons.AutoMirrored.Rounded.ShowChart
    val tint = if (isMaturity) {
        MaterialTheme.colorScheme.tertiary
    } else {
        LocalExtendedColors.current.positive
    }
    val condition = if (isMaturity) {
        val daysLeft = asset?.maturityDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
        buildString {
            append("Avisar ${alert.threshold.stripTrailingZeros().toPlainString()} dia(s) antes do vencimento")
            if (daysLeft != null) {
                append(" · ")
                append(if (daysLeft >= 0) "faltam $daysLeft dia(s)" else "vencido")
            }
        }
    } else {
        "Avisar quando variar ±${BrFormat.percent(alert.threshold)} no dia"
    }

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(tint.copy(alpha = 0.13f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        alert.ticker,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        alert.type.label(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    condition,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (alert.lastTriggeredAt != null) {
                    Text(
                        "Último disparo ${BrFormat.relative(alert.lastTriggeredAt).lowercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
            Switch(checked = alert.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Excluir alerta",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAlertDialog(
    assets: List<Asset>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onOpenAssets: () -> Unit,
    onConfirm: (String, AlertType, String) -> Unit,
) {
    var selectedAsset by remember { mutableStateOf<Asset?>(assets.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(AlertType.PRICE_CHANGE) }
    var threshold by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(assets) {
        val current = selectedAsset
        if (current == null || assets.none { it.id == current.id }) {
            val fallback = assets.firstOrNull()
            selectedAsset = fallback
            if (fallback != null) {
                type = if (fallback.type == AssetType.FIXED_INCOME) {
                    AlertType.MATURITY
                } else {
                    AlertType.PRICE_CHANGE
                }
            }
        }
    }

    val asset = selectedAsset
    val allowPrice = asset != null && asset.type != AssetType.FIXED_INCOME
    val allowMaturity = asset?.type == AssetType.FIXED_INCOME && asset.maturityDate != null
    val effectiveType = when {
        allowMaturity && !allowPrice -> AlertType.MATURITY
        allowPrice -> type
        else -> type
    }
    val canSubmit = !isBusy && asset != null && threshold.isNotBlank() && (allowPrice || allowMaturity)

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Criar alerta") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (assets.isEmpty()) {
                    Text(
                        "Cadastre um ativo na carteira antes de criar alertas.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                    ) {
                        OutlinedTextField(
                            value = asset?.let { "${it.ticker} · ${it.displayName}" }.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Ativo") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            assets.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("${option.ticker} · ${option.displayName}") },
                                    onClick = {
                                        selectedAsset = option
                                        type = if (option.type == AssetType.FIXED_INCOME) {
                                            AlertType.MATURITY
                                        } else {
                                            AlertType.PRICE_CHANGE
                                        }
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (allowPrice) {
                            FilterChip(
                                selected = effectiveType == AlertType.PRICE_CHANGE,
                                onClick = { type = AlertType.PRICE_CHANGE },
                                label = { Text("Variação de preço") },
                            )
                        }
                        if (allowMaturity) {
                            FilterChip(
                                selected = effectiveType == AlertType.MATURITY,
                                onClick = { type = AlertType.MATURITY },
                                label = { Text("Vencimento") },
                            )
                        }
                    }

                    if (asset?.type == AssetType.FIXED_INCOME && asset.maturityDate == null) {
                        Text(
                            "Este título não tem data de vencimento cadastrada. Edite o ativo para " +
                                "informar o vencimento antes de criar o alerta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        OutlinedTextField(
                            value = threshold,
                            onValueChange = { threshold = it },
                            label = {
                                Text(
                                    if (effectiveType == AlertType.PRICE_CHANGE) {
                                        "Variação diária (%)"
                                    } else {
                                        "Dias de antecedência"
                                    },
                                )
                            },
                            supportingText = {
                                Text(
                                    if (effectiveType == AlertType.PRICE_CHANGE) {
                                        "Entre 0,1% e 50%. O alerta usa a variação do dia."
                                    } else {
                                        "Entre 1 e 365 dias antes do vencimento."
                                    },
                                )
                            },
                            singleLine = true,
                            keyboardOptions = DecimalKeyboard,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (assets.isEmpty()) {
                TextButton(onClick = onOpenAssets) { Text("Ir para ativos") }
            } else {
                TextButton(
                    enabled = canSubmit,
                    onClick = { onConfirm(asset?.id.orEmpty(), effectiveType, threshold) },
                ) { Text("Criar") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
