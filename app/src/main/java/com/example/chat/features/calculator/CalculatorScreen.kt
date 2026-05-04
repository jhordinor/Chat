package com.example.chat.features.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onOpenSudoku: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var expression by rememberSaveable { mutableStateOf("") }
    var lastAnswer by rememberSaveable { mutableStateOf<String?>(null) }

    val previewResult = remember(expression, lastAnswer) {
        val expanded = expression.replace("ANS", lastAnswer.orEmpty())
        CalculatorEngine.tryEvaluate(expanded).getOrNull()
    }

    val history by CalcHistoryStore.historyFlow(context).collectAsState(initial = emptyList())

    fun showError(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun onKey(label: String) {
        when (label) {
            "C" -> expression = ""
            "⌫" -> expression = expression.dropLast(1)
            "=" -> {
                val expanded = expression.replace("ANS", lastAnswer.orEmpty())
                val result = CalculatorEngine.tryEvaluate(expanded).getOrElse { e ->
                    showError(e.message ?: "Expresión inválida")
                    return
                }
                lastAnswer = result
                if (expression.isNotBlank()) {
                    scope.launch {
                        CalcHistoryStore.addEntry(
                            context = context,
                            entry = CalcHistoryEntry(
                                expression = expression,
                                result = result,
                                timestampMs = System.currentTimeMillis(),
                            )
                        )
                    }
                }
                expression = result
            }
            "ANS" -> {
                if (lastAnswer != null) expression += "ANS"
            }
            else -> expression += label
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calculadora") },
                actions = {
                    TextButton(onClick = onOpenSudoku) { Text("Sudoku") }
                    TextButton(
                        onClick = {
                            scope.launch { CalcHistoryStore.clear(context) }
                        }
                    ) { Text("Borrar historial") }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Keypad(
                onKey = ::onKey,
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = expression.ifBlank { "0" }.toDisplayExpression(),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = previewResult?.let { "= $it" }.orEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = "Historial",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history.size) { index ->
                    val entry = history[index]
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expression = entry.expression },
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = entry.expression.toDisplayExpression(),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "= ${entry.result}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }
}

@Composable
private fun Keypad(
    onKey: (String) -> Unit,
) {
    val keys = remember {
        listOf(
            "C", "(", ")", "⌫",
            "7", "8", "9", "/",
            "4", "5", "6", "*",
            "1", "2", "3", "-",
            "0", ".", "%", "+",
            "ANS", "=", "", "",
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(keys) { label ->
            if (label.isBlank()) {
                Box(modifier = Modifier.height(52.dp))
                return@items
            }
            FilledTonalButton(
                onClick = { onKey(label) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(label)
            }
        }
    }
}

private fun String.toDisplayExpression(): String {
    return replace("*", "×").replace("/", "÷")
}
