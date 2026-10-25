package com.ictools.ichomelauncher.ui.terminal

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** 計算式の誤り（メッセージをそのままターミナルに表示する） */
class CalcException(message: String) : Exception(message)

/**
 * calc コマンドの電卓（再帰下降構文解析）。
 * 対応：+ - * / % ^、括弧、単項マイナス、小数、× ÷、関数 sqrt abs sin cos tan（度）log（常用）ln、定数 pi e
 */
object Calculator {

    /** 式を計算して表示用の文字列を返す */
    fun evaluate(expression: String): String = format(Parser(normalize(expression)).parseAll())

    private fun normalize(s: String): String = s
        .replace('×', '*').replace('÷', '/').replace('－', '-').replace('＋', '+')
        .replace('（', '(').replace('）', ')').replace('＊', '*').replace('／', '/')
        .lowercase()

    /** 整数ならそのまま、小数は有効桁 12 桁で末尾の 0 を省く */
    fun format(v: Double): String {
        if (v.isNaN()) throw CalcException("calc: not a number")
        if (v.isInfinite()) throw CalcException("calc: division by zero or overflow")
        if (v == Math.rint(v) && abs(v) < 1e15) return v.toLong().toString()
        return BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString()
    }

    private class Parser(val s: String) {
        var pos = 0

        fun parseAll(): Double {
            val v = expr()
            skipSpaces()
            if (pos < s.length) throw CalcException("calc: unexpected '${s[pos]}'")
            return v
        }

        private fun skipSpaces() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun eat(c: Char): Boolean {
            skipSpaces()
            if (pos < s.length && s[pos] == c) {
                pos++
                return true
            }
            return false
        }

        // 加減
        fun expr(): Double {
            var v = term()
            while (true) {
                v = when {
                    eat('+') -> v + term()
                    eat('-') -> v - term()
                    else -> return v
                }
            }
        }

        // 乗除・剰余
        private fun term(): Double {
            var v = unary()
            while (true) {
                v = when {
                    eat('*') -> v * unary()
                    eat('/') -> {
                        val d = unary()
                        if (d == 0.0) throw CalcException("calc: division by zero")
                        v / d
                    }
                    eat('%') -> {
                        val d = unary()
                        if (d == 0.0) throw CalcException("calc: division by zero")
                        v % d
                    }
                    else -> return v
                }
            }
        }

        // 単項の符号（-2^2 は -(2^2) になるよう、べき乗より弱くする）
        private fun unary(): Double = when {
            eat('-') -> -unary()
            eat('+') -> unary()
            else -> power()
        }

        // べき乗（右結合。指数側は 2^-1 のような符号付きも可）
        private fun power(): Double {
            val base = primary()
            return if (eat('^')) base.pow(unary()) else base
        }

        private fun primary(): Double {
            skipSpaces()
            if (eat('(')) {
                val v = expr()
                if (!eat(')')) throw CalcException("calc: missing ')'")
                return v
            }
            if (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) return number()
            if (pos < s.length && s[pos].isLetter()) return identifier()
            throw CalcException(if (pos < s.length) "calc: unexpected '${s[pos]}'" else "calc: incomplete expression")
        }

        private fun number(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.' || s[pos] == ',')) pos++
            val text = s.substring(start, pos).replace(",", "")
            return text.toDoubleOrNull() ?: throw CalcException("calc: bad number '$text'")
        }

        private fun identifier(): Double {
            val start = pos
            while (pos < s.length && s[pos].isLetter()) pos++
            val name = s.substring(start, pos)
            when (name) {
                "pi" -> return Math.PI
                "e" -> return Math.E
            }
            if (!eat('(')) throw CalcException("calc: unknown name '$name'")
            val arg = expr()
            if (!eat(')')) throw CalcException("calc: missing ')'")
            return when (name) {
                "sqrt" -> if (arg < 0) throw CalcException("calc: sqrt of negative") else sqrt(arg)
                "abs" -> abs(arg)
                "sin" -> sin(Math.toRadians(arg))
                "cos" -> cos(Math.toRadians(arg))
                "tan" -> tan(Math.toRadians(arg))
                "log" -> log10(arg)
                "ln" -> ln(arg)
                else -> throw CalcException("calc: unknown function '$name'")
            }
        }
    }
}
