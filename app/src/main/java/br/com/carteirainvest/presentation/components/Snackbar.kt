package br.com.carteirainvest.presentation.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import br.com.carteirainvest.presentation.navigation.LocalSnackbarHostState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun ObserveMessages(
    messages: Flow<String>,
    snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(messages, snackbarHostState) {
        messages.collect { message ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }
}

@Composable
fun ObserveEvents(
    events: Flow<String>,
    onEvent: (String) -> Unit,
) {
    LaunchedEffect(events, onEvent) {
        events.collect(onEvent)
    }
}
