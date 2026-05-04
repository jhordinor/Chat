package com.example.chat.features.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object CalculatorEngine {
    private val mathContext = MathContext.DECIMAL64

    fun tryEvaluate(expression: String): Result<String> {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Expresión vacía"))
        return runCatching {
            val tokens = tokenize(trimmed)
            val rpn = toRpn(tokens)
            val value = evalRpn(rpn)
            format(value)
        }
    }

    private sealed interface Token {
        data class Number(val value: BigDecimal) : Token
        data class Operator(val op: Op) : Token
        data object LeftParen : Token
        data object RightParen : Token
    }

    private enum class Assoc { Left, Right }

    private enum class Op(
        val precedence: Int,
        val assoc: Assoc,
        val arity: Int
    ) {
        Add(1, Assoc.Left, 2),
        Sub(1, Assoc.Left, 2),
        Mul(2, Assoc.Left, 2),
        Div(2, Assoc.Left, 2),
        Neg(3, Assoc.Right, 1),
        Percent(4, Assoc.Left, 1),
    }

    private fun tokenize(input: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        var prev: Token? = null

        fun isUnaryMinus(): Boolean {
            val p = prev
            return p == null || p is Token.Operator || p is Token.LeftParen
        }

        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    var dotCount = 0
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) {
                        if (input[i] == '.') dotCount++
                        if (dotCount > 1) throw IllegalArgumentException("Número inválido")
                        i++
                    }
                    val raw = input.substring(start, i)
                    val value = raw.toBigDecimalOrNull() ?: throw IllegalArgumentException("Número inválido")
                    val t = Token.Number(value)
                    out += t
                    prev = t
                }
                c == '(' -> {
                    val t = Token.LeftParen
                    out += t
                    prev = t
                    i++
                }
                c == ')' -> {
                    val t = Token.RightParen
                    out += t
                    prev = t
                    i++
                }
                c == '+' -> {
                    val t = Token.Operator(Op.Add)
                    out += t
                    prev = t
                    i++
                }
                c == '-' -> {
                    val op = if (isUnaryMinus()) Op.Neg else Op.Sub
                    val t = Token.Operator(op)
                    out += t
                    prev = t
                    i++
                }
                c == '*' || c == '×' -> {
                    val t = Token.Operator(Op.Mul)
                    out += t
                    prev = t
                    i++
                }
                c == '/' || c == '÷' -> {
                    val t = Token.Operator(Op.Div)
                    out += t
                    prev = t
                    i++
                }
                c == '%' -> {
                    val t = Token.Operator(Op.Percent)
                    out += t
                    prev = t
                    i++
                }
                else -> throw IllegalArgumentException("Carácter no soportado: $c")
            }
        }
        return out
    }

    private fun toRpn(tokens: List<Token>): List<Token> {
        val output = ArrayList<Token>()
        val ops = ArrayDeque<Token>()

        for (t in tokens) {
            when (t) {
                is Token.Number -> output += t
                is Token.Operator -> {
                    while (true) {
                        val top = ops.lastOrNull() as? Token.Operator ?: break
                        val shouldPop =
                            top.op.precedence > t.op.precedence ||
                                (top.op.precedence == t.op.precedence && t.op.assoc == Assoc.Left)
                        if (!shouldPop) break
                        output += ops.removeLast() as Token.Operator
                    }
                    ops.addLast(t)
                }
                Token.LeftParen -> ops.addLast(t)
                Token.RightParen -> {
                    while (true) {
                        val top = ops.lastOrNull() ?: throw IllegalArgumentException("Paréntesis desbalanceados")
                        if (top is Token.LeftParen) {
                            ops.removeLast()
                            break
                        }
                        output += ops.removeLast() as Token.Operator
                    }
                }
            }
        }

        while (ops.isNotEmpty()) {
            val top = ops.removeLast()
            if (top is Token.LeftParen) throw IllegalArgumentException("Paréntesis desbalanceados")
            output += top as Token.Operator
        }

        return output
    }

    private fun evalRpn(tokens: List<Token>): BigDecimal {
        val stack = ArrayDeque<BigDecimal>()
        for (t in tokens) {
            when (t) {
                is Token.Number -> stack.addLast(t.value)
                is Token.Operator -> {
                    when (t.op) {
                        Op.Add -> {
                            val b = stack.removeLastOrThrow()
                            val a = stack.removeLastOrThrow()
                            stack.addLast(a.add(b, mathContext))
                        }
                        Op.Sub -> {
                            val b = stack.removeLastOrThrow()
                            val a = stack.removeLastOrThrow()
                            stack.addLast(a.subtract(b, mathContext))
                        }
                        Op.Mul -> {
                            val b = stack.removeLastOrThrow()
                            val a = stack.removeLastOrThrow()
                            stack.addLast(a.multiply(b, mathContext))
                        }
                        Op.Div -> {
                            val b = stack.removeLastOrThrow()
                            val a = stack.removeLastOrThrow()
                            if (b.compareTo(BigDecimal.ZERO) == 0) throw ArithmeticException("División por cero")
                            stack.addLast(a.divide(b, mathContext))
                        }
                        Op.Neg -> {
                            val a = stack.removeLastOrThrow()
                            stack.addLast(a.negate(mathContext))
                        }
                        Op.Percent -> {
                            val a = stack.removeLastOrThrow()
                            stack.addLast(a.divide(BigDecimal(100), mathContext))
                        }
                    }
                }
                Token.LeftParen, Token.RightParen -> throw IllegalStateException("Token inesperado")
            }
        }
        if (stack.size != 1) throw IllegalArgumentException("Expresión inválida")
        return stack.last()
    }

    private fun <T> ArrayDeque<T>.removeLastOrThrow(): T {
        return removeLastOrNull() ?: throw IllegalArgumentException("Expresión inválida")
    }

    private fun format(value: BigDecimal): String {
        val normalized = value.stripTrailingZeros()
        val scaled = if (normalized.scale() < 0) normalized.setScale(0, RoundingMode.UNNECESSARY) else normalized
        val asString = scaled.toPlainString()
        return if (asString.length > 24) scaled.round(MathContext(16)).stripTrailingZeros().toPlainString() else asString
    }
}
