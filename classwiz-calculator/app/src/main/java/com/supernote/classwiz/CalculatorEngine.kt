package com.supernote.classwiz

import kotlin.math.*

/**
 * ClassWiz scientific calculator engine.
 *
 * Supports:
 *  - Arithmetic: + − × ÷ ^ %
 *  - Scientific: sin cos tan asin acos atan sinh cosh tanh asinh acosh atanh
 *  - Logarithms: ln log (base-10) log2 exp (eˣ) 10^x
 *  - Roots: √ ∛ arbitrary power
 *  - Other: abs ceil floor factorial x⁻¹ sign random
 *  - Constants: π e Ans
 *  - Variables: A B C D E F M
 *  - Implicit multiplication: 2π  2(3+4)  sin(x)cos(x)
 *  - Angle modes: DEGREE · RADIAN · GRADIAN
 */
class CalculatorEngine {

    // ── Public state ───────────────────────────────────────────────────────────

    enum class AngleMode { DEGREE, RADIAN, GRADIAN }

    var angleMode: AngleMode = AngleMode.DEGREE
    var lastAnswer: Double = 0.0
    val variables: MutableMap<String, Double> = mutableMapOf(
        "A" to 0.0, "B" to 0.0, "C" to 0.0,
        "D" to 0.0, "E" to 0.0, "F" to 0.0, "M" to 0.0
    )

    // ── Entry point ────────────────────────────────────────────────────────────

    /** Evaluate [expression] and update [lastAnswer]. Returns the numeric result. */
    fun evaluate(expression: String): Double {
        if (expression.isBlank()) return lastAnswer
        val tokens = tokenize(expression)
        val pos = intArrayOf(0)
        val result = parseExpr(tokens, pos)
        if (pos[0] != tokens.size)
            throw IllegalArgumentException("Unexpected token at position ${pos[0]}: ${tokens[pos[0]]}")
        lastAnswer = result
        return result
    }

    // ── Token types ────────────────────────────────────────────────────────────

    private sealed class Tok {
        data class Num(val v: Double) : Tok()
        data class BinOp(val c: Char) : Tok()   // + - * / ^ %
        object LPar : Tok()
        object RPar : Tok()
        data class Fn(val name: String) : Tok()  // sin cos log sqrt …
        data class Id(val name: String) : Tok()  // Ans π e A…F M
        object Bang : Tok()                       // postfix factorial !
    }

    // ── Tokenizer ──────────────────────────────────────────────────────────────

    private fun tokenize(src: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0

        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++

                // ── Number literals (including scientific notation) ──────────
                c.isDigit() || (c == '.' && i + 1 < src.length && src[i + 1].isDigit()) -> {
                    val start = i
                    while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                    // Optional E/e exponent — only consume if followed by digit (or ±digit)
                    if (i < src.length && src[i] in "Ee") {
                        val save = i
                        i++
                        if (i < src.length && src[i] in "+-") i++
                        if (i < src.length && src[i].isDigit()) {
                            while (i < src.length && src[i].isDigit()) i++
                        } else {
                            i = save   // backtrack: 'e' is the constant, not exponent
                        }
                    }
                    out += Tok.Num(src.substring(start, i).toDouble())
                }

                // ── Identifiers / function names ─────────────────────────────
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                    val word = src.substring(start, i)
                    out += classify(word)
                }

                // ── Unicode math symbols ──────────────────────────────────────
                c == '×' -> { out += Tok.BinOp('*'); i++ }
                c == '÷' -> { out += Tok.BinOp('/'); i++ }
                c == 'π' -> { out += Tok.Id("π"); i++ }
                c == '√' -> { out += Tok.Fn("sqrt"); i++ }
                c == '∛' -> { out += Tok.Fn("cbrt"); i++ }
                c == '²' -> { out += Tok.BinOp('^'); out += Tok.Num(2.0); i++ }
                c == '³' -> { out += Tok.BinOp('^'); out += Tok.Num(3.0); i++ }
                c == '⁻' && i + 1 < src.length && src[i + 1] == '¹' -> {
                    out += Tok.Fn("inv"); i += 2
                }
                c == '!' -> { out += Tok.Bang; i++ }
                c == '%' -> { out += Tok.BinOp('%'); i++ }
                c == '(' -> { out += Tok.LPar; i++ }
                c == ')' -> { out += Tok.RPar; i++ }
                c in "+-*/^" -> { out += Tok.BinOp(c); i++ }
                else -> throw IllegalArgumentException("Unknown character: '$c'")
            }
        }
        return out
    }

    /**
     * Map a word token to the correct Tok subtype.
     *
     * Single uppercase variable letters (A–F, M) are handled BEFORE any
     * case-insensitive matching so that variable "E" is never confused with
     * Euler's constant "e".
     */
    private fun classify(word: String): Tok {
        // Variable letters take priority — checked on the original (un-lowercased) word
        if (word.length == 1 && word[0] in "ABCDEFM") return Tok.Id(word)
        return when (word.lowercase()) {
            "pi"                                -> Tok.Id("π")
            "ans"                               -> Tok.Id("Ans")
            "e"                                 -> Tok.Id("e")    // Euler constant (lowercase e only)
            "sin", "cos", "tan",
            "asin", "acos", "atan",
            "arcsin", "arccos", "arctan",
            "sinh", "cosh", "tanh",
            "asinh", "acosh", "atanh",
            "ln", "log", "log2", "log10",
            "exp", "sqrt", "cbrt",
            "abs", "ceil", "floor", "round",
            "fact", "factorial",
            "sign", "sgn",
            "inv", "rand", "ranint"             -> Tok.Fn(word.lowercase())
            else                                -> {
                if (word.length == 1 && word[0] in "xyzXYZ") Tok.Id(word)
                else Tok.Fn(word.lowercase())
            }
        }
    }

    // ── Recursive-descent parser ───────────────────────────────────────────────
    //
    //  expr    := addExpr
    //  addExpr := mulExpr (('+' | '−') mulExpr)*
    //  mulExpr := powExpr (('*' | '/' | '%' | implicit) powExpr)*
    //  powExpr := unary   ('^' powExpr)?              ← right-associative
    //  unary   := ('+'|'−')* postfix
    //  postfix := atom     '!'*
    //  atom    := Num | Id | '(' expr ')' | Fn ('(' expr ')' | atom)

    private fun parseExpr(t: List<Tok>, p: IntArray): Double = parseAdd(t, p)

    private fun parseAdd(t: List<Tok>, p: IntArray): Double {
        var v = parseMul(t, p)
        while (p[0] < t.size) {
            val tok = t[p[0]]
            if (tok !is Tok.BinOp) break
            if (tok.c != '+' && tok.c != '-') break
            p[0]++
            val r = parseMul(t, p)
            v = if (tok.c == '+') v + r else v - r
        }
        return v
    }

    private fun parseMul(t: List<Tok>, p: IntArray): Double {
        var v = parsePow(t, p)
        while (p[0] < t.size) {
            when (val tok = t[p[0]]) {
                is Tok.BinOp -> {
                    if (tok.c != '*' && tok.c != '/' && tok.c != '%') break
                    p[0]++
                    val r = parsePow(t, p)
                    v = when (tok.c) {
                        '*'  -> v * r
                        '/'  -> if (r == 0.0) throw ArithmeticException("Division by zero") else v / r
                        else -> v % r
                    }
                }
                // Implicit multiplication: next token starts a new factor
                is Tok.LPar, is Tok.Fn, is Tok.Id -> v *= parsePow(t, p)
                is Tok.Num                         -> v *= parsePow(t, p)
                else                               -> break
            }
        }
        return v
    }

    private fun parsePow(t: List<Tok>, p: IntArray): Double {
        val base = parseUnary(t, p)
        if (p[0] < t.size && t[p[0]] is Tok.BinOp && (t[p[0]] as Tok.BinOp).c == '^') {
            p[0]++
            return base.pow(parsePow(t, p))   // right-associative
        }
        return base
    }

    private fun parseUnary(t: List<Tok>, p: IntArray): Double {
        if (p[0] < t.size && t[p[0]] is Tok.BinOp) {
            val c = (t[p[0]] as Tok.BinOp).c
            if (c == '-') { p[0]++; return -parsePostfix(t, p) }
            if (c == '+') { p[0]++; return  parsePostfix(t, p) }
        }
        return parsePostfix(t, p)
    }

    private fun parsePostfix(t: List<Tok>, p: IntArray): Double {
        var v = parseAtom(t, p)
        while (p[0] < t.size && t[p[0]] is Tok.Bang) {
            v = factorial(v); p[0]++
        }
        return v
    }

    private fun parseAtom(t: List<Tok>, p: IntArray): Double {
        if (p[0] >= t.size) throw IllegalArgumentException("Unexpected end of expression")

        return when (val tok = t[p[0]]) {

            is Tok.Num -> { p[0]++; tok.v }

            is Tok.Id  -> {
                p[0]++
                when (tok.name) {
                    "π"   -> PI
                    "e"   -> E
                    "Ans" -> lastAnswer
                    else  -> variables[tok.name]
                                ?: throw IllegalArgumentException("Unknown variable: ${tok.name}")
                }
            }

            is Tok.LPar -> {
                p[0]++
                val v = parseExpr(t, p)
                if (p[0] >= t.size || t[p[0]] !is Tok.RPar)
                    throw IllegalArgumentException("Missing closing parenthesis ')'")
                p[0]++
                v
            }

            is Tok.Fn -> {
                val fn = tok.name; p[0]++
                // Functions consume either a parenthesised arg or the very next atom
                val arg: Double = if (p[0] < t.size && t[p[0]] is Tok.LPar) {
                    p[0]++
                    val v = parseExpr(t, p)
                    if (p[0] >= t.size || t[p[0]] !is Tok.RPar)
                        throw IllegalArgumentException("Missing ')' after $fn")
                    p[0]++
                    v
                } else {
                    parseAtom(t, p)
                }
                applyFn(fn, arg)
            }

            is Tok.BinOp -> throw IllegalArgumentException("Unexpected operator '${tok.c}'")
            is Tok.RPar  -> throw IllegalArgumentException("Unexpected ')'")
            is Tok.Bang  -> throw IllegalArgumentException("Unexpected '!'")
        }
    }

    // ── Function evaluation ────────────────────────────────────────────────────

    private fun applyFn(name: String, x: Double): Double = when (name) {
        "sin"               -> sin(toRad(x))
        "cos"               -> cos(toRad(x))
        "tan"               -> {
            val r = toRad(x)
            if (abs(cos(r)) < 1e-15) throw ArithmeticException("tan is undefined for this angle")
            tan(r)
        }
        "asin", "arcsin"    -> fromRad(asin(x.coerceIn(-1.0, 1.0)))
        "acos", "arccos"    -> fromRad(acos(x.coerceIn(-1.0, 1.0)))
        "atan", "arctan"    -> fromRad(atan(x))
        "sinh"              -> sinh(x)
        "cosh"              -> cosh(x)
        "tanh"              -> tanh(x)
        "asinh"             -> ln(x + sqrt(x * x + 1.0))
        "acosh"             -> if (x < 1.0) throw ArithmeticException("acosh: argument < 1")
                               else ln(x + sqrt(x * x - 1.0))
        "atanh"             -> if (abs(x) >= 1.0) throw ArithmeticException("atanh: |x| ≥ 1")
                               else 0.5 * ln((1.0 + x) / (1.0 - x))
        "ln"                -> if (x <= 0.0) throw ArithmeticException("ln: argument ≤ 0") else ln(x)
        "log", "log10"      -> if (x <= 0.0) throw ArithmeticException("log: argument ≤ 0") else log10(x)
        "log2"              -> if (x <= 0.0) throw ArithmeticException("log2: argument ≤ 0") else log2(x)
        "exp"               -> exp(x)
        "sqrt"              -> if (x < 0.0) throw ArithmeticException("√: argument < 0") else sqrt(x)
        "cbrt"              -> cbrt(x)
        "abs"               -> abs(x)
        "ceil"              -> ceil(x)
        "floor"             -> floor(x)
        "round"             -> kotlin.math.round(x).toDouble()
        "fact", "factorial" -> factorial(x)
        "sign", "sgn"       -> sign(x)
        "inv"               -> if (x == 0.0) throw ArithmeticException("1/0 is undefined") else 1.0 / x
        "rand"              -> Math.random()
        "ranint"            -> floor(Math.random() * x)
        else                -> throw IllegalArgumentException("Unknown function: $name")
    }

    // ── Angle helpers ──────────────────────────────────────────────────────────

    private fun toRad(v: Double): Double = when (angleMode) {
        AngleMode.DEGREE  -> v * PI / 180.0
        AngleMode.RADIAN  -> v
        AngleMode.GRADIAN -> v * PI / 200.0
    }

    private fun fromRad(v: Double): Double = when (angleMode) {
        AngleMode.DEGREE  -> v * 180.0 / PI
        AngleMode.RADIAN  -> v
        AngleMode.GRADIAN -> v * 200.0 / PI
    }

    // ── Factorial ──────────────────────────────────────────────────────────────

    private fun factorial(n: Double): Double {
        if (n < 0.0 || n != floor(n))
            throw ArithmeticException("Factorial requires a non-negative integer")
        if (n > 170.0) return Double.POSITIVE_INFINITY
        var result = 1.0
        for (k in 2..n.toInt()) result *= k
        return result
    }

    // ── Result formatting ──────────────────────────────────────────────────────

    /**
     * Format [v] for display:
     *  - NaN / Infinity → error strings
     *  - Exact integers (|v| < 10¹⁵) → no decimal point
     *  - Very large / very small → scientific notation
     *  - Otherwise → strip trailing zeros
     */
    fun formatResult(v: Double): String {
        if (v.isNaN())          return "Math ERROR"
        if (v.isInfinite())     return if (v > 0) "+Inf" else "-Inf"
        if (v == 0.0)           return "0"
        if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()

        return if (abs(v) >= 1e10 || abs(v) < 1e-4) {
            // Scientific notation: keep 6 significant figures
            val s = "%.6e".format(v)
            formatSciNotation(s)
        } else {
            // Fixed: up to 10 decimal places, trim trailing zeros
            "%.10f".format(v).trimEnd('0').trimEnd('.')
        }
    }

    /** Turn Java's "1.234567e+08" into a readable "1.234567×10^8". */
    private fun formatSciNotation(s: String): String {
        val eIdx = s.indexOfFirst { it == 'e' || it == 'E' }
        if (eIdx < 0) return s
        val mantissa = s.substring(0, eIdx).trimEnd('0').trimEnd('.')
        val expPart  = s.substring(eIdx + 1)
        val expVal   = expPart.trimStart('+').toInt()
        return "$mantissa×10^$expVal"
    }

    /**
     * Attempt to express [v] as a simple fraction p/q (|q| ≤ 1000).
     * Returns null if no simple fraction is found.
     */
    fun toFraction(v: Double): Pair<Long, Long>? {
        if (v == floor(v)) return null  // already integer
        val sign = if (v < 0) -1L else 1L
        val av = abs(v)
        for (q in 1L..1000L) {
            val p = (av * q).roundToLong()
            if (abs(av - p.toDouble() / q) < 1e-9) {
                return Pair(sign * p, q)
            }
        }
        return null
    }

    private fun Double.roundToLong(): Long = toLong().let { l ->
        if (this - l >= 0.5) l + 1 else l
    }
}
