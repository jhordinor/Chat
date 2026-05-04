package com.example.chat.features.calculator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onOpenSudoku: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    var expression by rememberSaveable { mutableStateOf("") }
    var lastAnswer by rememberSaveable { mutableStateOf<String?>(null) }
    var lastOcrText by rememberSaveable { mutableStateOf("") }

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

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) return@rememberLauncherForActivityResult
        val uri = pendingPhotoUri ?: return@rememberLauncherForActivityResult
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
            ?: return@rememberLauncherForActivityResult
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val raw = visionText.text
                lastOcrText = raw
                val extracted = normalizeMathFromOcr(raw)
                if (extracted.isNotBlank()) {
                    expression = extracted
                    val result = CalculatorEngine.tryEvaluate(extracted).getOrNull()
                    if (result != null) {
                        lastAnswer = result
                        scope.launch {
                            CalcHistoryStore.addEntry(
                                context = context,
                                entry = CalcHistoryEntry(
                                    expression = extracted,
                                    result = result,
                                    timestampMs = System.currentTimeMillis(),
                                )
                            )
                        }
                        scope.launch { snackbarHostState.showSnackbar("Resultado: $result") }
                    } else {
                        showError("No pude resolver esto en la calculadora. Corrige el texto y toca =")
                    }
                } else {
                    showError("No pude leer una expresión matemática en la foto")
                }
            }
            .addOnFailureListener { e ->
                showError(e.message ?: "Error leyendo la foto")
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calculadora") },
                actions = {
                    TextButton(
                        onClick = {
                            val file = File.createTempFile("capture_", ".jpg", context.cacheDir)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            pendingPhotoUri = uri
                            cameraLauncher.launch(uri)
                        }
                    ) { Text("Cámara") }
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

private fun normalizeMathFromOcr(raw: String): String {
    val cleaned = raw
        .replace('\u2212', '-')
        .replace('\u00D7', '*')
        .replace('\u00F7', '/')
        .replace('·', '*')
        .replace('•', '*')
        .replace(',', '.')

    val lines = cleaned
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (lines.isEmpty()) return ""

    val last = lines.last()
    val lastIsNumber = last.matches(Regex("""[0-9]+(\.[0-9]+)?"""))
    if (lines.size >= 2 && lastIsNumber) {
        val numerator = lines.dropLast(1).joinToString(separator = "")
        val denom = last
        return normalizeExpression("($numerator)/($denom)")
    }

    val candidate = lines.maxByOrNull { it.length }.orEmpty()
    val leftSide = candidate.substringBefore('=').trim()
    return normalizeExpression(leftSide)
}

private fun normalizeExpression(expr: String): String {
    val raw = expr
        .replace("×", "*")
        .replace("x", "*", ignoreCase = true)
        .replace("÷", "/")
        .replace(":", "/")
        .replace(" ", "")

    val filtered = buildString(raw.length) {
        for (c in raw) {
            if (c.isDigit() || c == '.' || c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '(' || c == ')') {
                append(c)
            }
        }
    }

    if (filtered.isBlank()) return ""

    val out = StringBuilder(filtered.length + 8)
    fun isDigitOrDot(c: Char): Boolean = c.isDigit() || c == '.'
    fun isTokenRight(c: Char): Boolean = isDigitOrDot(c) || c == ')' || c == '%'
    fun isTokenLeft(c: Char): Boolean = isDigitOrDot(c) || c == '('

    for (i in filtered.indices) {
        val c = filtered[i]
        if (i > 0) {
            val p = filtered[i - 1]
            val needMul =
                (isTokenRight(p) && c == '(') ||
                    (p == ')' && isDigitOrDot(c)) ||
                    (isDigitOrDot(p) && c == '(') ||
                    (p == ')' && c == '(') ||
                    (p == '%' && isTokenLeft(c))
            if (needMul) out.append('*')
        }
        out.append(c)
    }

    return wrapDivisionImplicitProduct(out.toString())
}

private fun wrapDivisionImplicitProduct(expr: String): String {
    if (!expr.contains('/')) return expr
    val out = StringBuilder(expr.length + 8)
    var i = 0
    while (i < expr.length) {
        val c = expr[i]
        if (c == '/' && i + 2 < expr.length) {
            var j = i + 1
            if (expr[j].isDigit() || expr[j] == '.') {
                val startNum = j
                var dotCount = 0
                while (j < expr.length && (expr[j].isDigit() || expr[j] == '.')) {
                    if (expr[j] == '.') dotCount++
                    if (dotCount > 1) break
                    j++
                }

                val hasStar = j < expr.length && expr[j] == '*' && j + 1 < expr.length && expr[j + 1] == '('
                val hasParen = j < expr.length && expr[j] == '('
                if (hasStar || hasParen) {
                    if (hasStar) j += 1
                    out.append("/(")
                    out.append(expr.substring(startNum, j))
                    if (expr[j] == '(') {
                        var depth = 0
                        while (j < expr.length) {
                            val ch = expr[j]
                            out.append(ch)
                            if (ch == '(') depth += 1
                            if (ch == ')') {
                                depth -= 1
                                if (depth == 0) {
                                    break
                                }
                            }
                            j++
                        }
                    }
                    out.append(')')
                    i = j + 1
                    continue
                }
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}
