package com.example.chat.features.sudoku

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class Cell(
    val value: Int?,
    val isGiven: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SudokuScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val puzzle = remember { defaultPuzzle() }
    val solution = remember { defaultSolution() }

    val cells = remember {
        mutableStateListOf<Cell>().apply {
            for (i in 0 until 81) {
                val v = puzzle[i].takeIf { it != 0 }
                add(Cell(value = v, isGiven = v != null))
            }
        }
    }

    var selectedIndex by remember { mutableIntStateOf(-1) }

    fun setCell(index: Int, value: Int?) {
        if (index !in 0 until 81) return
        if (cells[index].isGiven) return
        cells[index] = cells[index].copy(value = value)
    }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun verify() {
        for (i in 0 until 81) {
            val expected = solution[i]
            val actual = cells[i].value ?: 0
            if (expected != actual) {
                showMessage("Todavía hay errores")
                return
            }
        }
        showMessage("¡Correcto!")
    }

    fun reset() {
        for (i in 0 until 81) {
            val v = puzzle[i].takeIf { it != 0 }
            cells[i] = Cell(value = v, isGiven = v != null)
        }
        selectedIndex = -1
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sudoku") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Volver") }
                },
                actions = {
                    TextButton(onClick = ::verify) { Text("Verificar") }
                    TextButton(onClick = ::reset) { Text("Reiniciar") }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    SudokuBoard(
                        cells = cells,
                        selectedIndex = selectedIndex,
                        onSelect = { selectedIndex = it },
                    )
                }
            }

            NumberPad(
                enabled = selectedIndex != -1 && !cells.getOrNull(selectedIndex)?.isGiven.orDefault(false),
                onNumber = { n -> setCell(selectedIndex, n) },
                onErase = { setCell(selectedIndex, null) },
            )
        }
    }
}

@Composable
private fun SudokuBoard(
    cells: List<Cell>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        verticalArrangement = Arrangement.Center
    ) {
        for (row in 0 until 9) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center
            ) {
                for (col in 0 until 9) {
                    val index = row * 9 + col
                    SudokuCell(
                        cell = cells[index],
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f)
                    )
                    if (col == 2 || col == 5) Spacer(modifier = Modifier.width(6.dp))
                }
            }
            if (row == 2 || row == 5) Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SudokuCell(
    cell: Cell,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bgColor =
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .border(width = 1.dp, color = borderColor)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = cell.value?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (cell.isGiven) FontWeight.SemiBold else FontWeight.Normal,
            color = if (cell.isGiven) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NumberPad(
    enabled: Boolean,
    onNumber: (Int) -> Unit,
    onErase: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (n in 1..3) {
                FilledTonalButton(
                    onClick = { onNumber(n) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) { Text(n.toString()) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (n in 4..6) {
                FilledTonalButton(
                    onClick = { onNumber(n) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) { Text(n.toString()) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (n in 7..9) {
                FilledTonalButton(
                    onClick = { onNumber(n) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) { Text(n.toString()) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onErase,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) { Text("Borrar") }
        }
    }
}

private fun Boolean?.orDefault(default: Boolean): Boolean = this ?: default

private fun defaultPuzzle(): IntArray {
    return intArrayOf(
        5, 3, 0, 0, 7, 0, 0, 0, 0,
        6, 0, 0, 1, 9, 5, 0, 0, 0,
        0, 9, 8, 0, 0, 0, 0, 6, 0,
        8, 0, 0, 0, 6, 0, 0, 0, 3,
        4, 0, 0, 8, 0, 3, 0, 0, 1,
        7, 0, 0, 0, 2, 0, 0, 0, 6,
        0, 6, 0, 0, 0, 0, 2, 8, 0,
        0, 0, 0, 4, 1, 9, 0, 0, 5,
        0, 0, 0, 0, 8, 0, 0, 7, 9,
    )
}

private fun defaultSolution(): IntArray {
    return intArrayOf(
        5, 3, 4, 6, 7, 8, 9, 1, 2,
        6, 7, 2, 1, 9, 5, 3, 4, 8,
        1, 9, 8, 3, 4, 2, 5, 6, 7,
        8, 5, 9, 7, 6, 1, 4, 2, 3,
        4, 2, 6, 8, 5, 3, 7, 9, 1,
        7, 1, 3, 9, 2, 4, 8, 5, 6,
        9, 6, 1, 5, 3, 7, 2, 8, 4,
        2, 8, 7, 4, 1, 9, 6, 3, 5,
        3, 4, 5, 2, 8, 6, 1, 7, 9,
    )
}
