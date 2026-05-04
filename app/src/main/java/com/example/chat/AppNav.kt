package com.example.chat

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.chat.features.calculator.CalculatorScreen
import com.example.chat.features.sudoku.SudokuScreen

object Routes {
    const val Calculator = "calculator"
    const val Sudoku = "sudoku"
}

@Composable
fun ChatApp() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.Calculator
    ) {
        composable(Routes.Calculator) {
            CalculatorScreen(
                onOpenSudoku = { navController.navigate(Routes.Sudoku) }
            )
        }
        composable(Routes.Sudoku) {
            SudokuScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
