package com.supernote.cfx9960gt

import kotlin.math.*
import kotlin.random.Random
import java.util.Locale

/**
 * Casio CFX-9960GT calculator engine.
 *
 * Supports:
 *  - Arithmetic:  + − × ÷ ^ %
 *  - Trig:        sin cos tan  asin acos atan  (respects angle mode)
 *  - Hyperbolic:  sinh cosh tanh  asinh acosh atanh
 *  - Logarithms:  ln log (base-10) log2  exp (eˣ) 10^x
 *  - Roots:       √ ∛  arbitrary power
 *  - Other:       abs ceil floor round  factorial  sign  1/x  Ran# RanInt
 *  - Constants:   π e Ans
 *  - Variables:   A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
 *  - Implicit multiplication: 2π  2(3+4)  sin(x)cos(x)
 *  - Angle modes: DEGREE · RADIAN · GRADIAN
 *  - Statistics:  1-variable descriptive stats (mean, σ, min, max)
 */
class CfxEngine {

    // ── Public state ───────────────────────────────────────────────────────────

    enum class AngleMode { DEGREE, RADIAN, GRADIAN }
    enum class DisplayMode { NORMAL, FIX, SCI }

    var angleMode: AngleMode = AngleMode.DEGREE
    var displayMode: DisplayMode = DisplayMode.NORMAL
    var fixPlaces: Int = 2
    var lastAnswer: Double = 0.0

    /** Single-letter variables A–Z, initialised to 0. */
    val variables: MutableMap<String, Double> = ('A'..'Z').associate {
        it.toString() to 0.0
    }.toMutableMap()

    /** Statistics data list (used in STAT mode). */
    val statData: MutableList<Double> = mutableListOf()

    // ── Entry point ────────────────────────────────────────────────────────────

    /** Evaluate [expression] and update [lastAnswer]. Returns the numeric result. */
    fun evaluate(expression: String): Double {
        if (expression.isBlank()) return lastAnswer
        val tokens = tokenize(expression)
        val pos = intArrayOf(0)
        val result = parseExpr(tokens, pos)
        if (pos[0] != tokens.size)
            throw IllegalArgumentException("Unexpected token: ${tokens[pos[0]]}")
        lastAnswer = result
        return result
    }

    // ── Token types ────────────────────────────────────────────────────────────

    private sealed class Tok {
        data class Num(val v: Double) : Tok()
        data class BinOp(val c: Char) : Tok()        // + - * / ^ %
        object LPar : Tok()
        object RPar : Tok()
        data class Fn(val name: String) : Tok()       // sin cos log sqrt …
        data class Id(val name: String) : Tok()       // Ans π e A…Z
        object Bang : Tok()                            // postfix !
        object Comma : Tok()                           // , (for 2-arg functions)
    }

    // ── Tokenizer ──────────────────────────────────────────────────────────────

    private fun tokenize(src: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++

                // Number literals (including scientific notation)
                c.isDigit() || (c == '.' && i + 1 < src.length && src[i + 1].isDigit()) -> {
                    val start = i
                    while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                    if (i < src.length && src[i] in "Ee") {
                        val save = i; i++
                        if (i < src.length && src[i] in "+-") i++
                        if (i < src.length && src[i].isDigit()) {
                            while (i < src.length && src[i].isDigit()) i++
                        } else { i = save }
                    }
                    out += Tok.Num(src.substring(start, i).toDouble())
                }

                // Identifiers / function names
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                    out += classify(src.substring(start, i))
                }

                // Unicode math symbols
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
                c == ',' -> { out += Tok.Comma; i++ }
                c in "+-*/^" -> { out += Tok.BinOp(c); i++ }
                else -> throw IllegalArgumentException("Unknown character: '$c'")
            }
        }
        return out
    }

    private fun classify(word: String): Tok {
        // Single uppercase variable letters take priority over constant 'E'
        if (word.length == 1 && word[0] in 'A'..'Z') {
            // Exception: lowercase 'e' = Euler constant, uppercase single letter = variable
            return Tok.Id(word)
        }
        return when (word.lowercase()) {
            "pi"                                -> Tok.Id("π")
            "ans"                               -> Tok.Id("Ans")
            "e"                                 -> Tok.Id("e")
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
            "inv", "rand", "ranint",
            "logab", "npr", "ncr", "permr",
            "gcd", "lcm"                        -> Tok.Fn(word.lowercase())
            else                                -> Tok.Fn(word.lowercase())
        }
    }

    // ── Recursive-descent parser ───────────────────────────────────────────────
    //  expr    := addExpr
    //  addExpr := mulExpr (('+' | '-') mulExpr)*
    //  mulExpr := powExpr (('*' | '/' | '%' | implicit) powExpr)*
    //  powExpr := unary   ('^' powExpr)?   ← right-associative
    //  unary   := ('+'|'-')* postfix
    //  postfix := atom  '!'*
    //  atom    := Num | Id | '(' expr ')' | Fn '(' argList ')'

    private fun parseExpr(t: List<Tok>, p: IntArray): Double = parseAdd(t, p)

    private fun parseAdd(t: List<Tok>, p: IntArray): Double {
        var v = parseMul(t, p)
        while (p[0] < t.size) {
            val tok = t[p[0]]
            if (tok !is Tok.BinOp || (tok.c != '+' && tok.c != '-')) break
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
                // Implicit multiplication
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
            return base.pow(parsePow(t, p))
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
                    throw IllegalArgumentException("Missing ')'")
                p[0]++; v
            }
            is Tok.Fn -> {
                val fn = tok.name; p[0]++
                // Functions with two arguments: logab(base, x), nPr(n,r), nCr(n,r)
                if (fn in listOf("logab", "npr", "ncr", "ranint", "gcd", "lcm")) {
                    if (p[0] >= t.size || t[p[0]] !is Tok.LPar)
                        throw IllegalArgumentException("'$fn' requires (arg1, arg2)")
                    p[0]++
                    val a1 = parseExpr(t, p)
                    if (p[0] >= t.size || t[p[0]] !is Tok.Comma)
                        throw IllegalArgumentException("'$fn' requires two arguments")
                    p[0]++
                    val a2 = parseExpr(t, p)
                    if (p[0] >= t.size || t[p[0]] !is Tok.RPar)
                        throw IllegalArgumentException("Missing ')' after $fn")
                    p[0]++
                    applyFn2(fn, a1, a2)
                } else {
                    val arg: Double = if (p[0] < t.size && t[p[0]] is Tok.LPar) {
                        p[0]++
                        val v = parseExpr(t, p)
                        if (p[0] >= t.size || t[p[0]] !is Tok.RPar)
                            throw IllegalArgumentException("Missing ')' after $fn")
                        p[0]++; v
                    } else {
                        parseAtom(t, p)
                    }
                    applyFn(fn, arg)
                }
            }
            is Tok.BinOp -> throw IllegalArgumentException("Unexpected operator '${tok.c}'")
            is Tok.RPar  -> throw IllegalArgumentException("Unexpected ')'")
            is Tok.Bang  -> throw IllegalArgumentException("Unexpected '!'")
            is Tok.Comma -> throw IllegalArgumentException("Unexpected ','")
        }
    }

    // ── Single-argument functions ──────────────────────────────────────────────

    private fun applyFn(name: String, x: Double): Double = when (name) {
        "sin"               -> sin(toRad(x))
        "cos"               -> cos(toRad(x))
        "tan"               -> {
            val r = toRad(x)
            if (abs(cos(r)) < 1e-15) throw ArithmeticException("tan undefined (angle near π/2 + nπ)")
            tan(r)
        }
        "asin", "arcsin"    -> fromRad(asin(x.coerceIn(-1.0, 1.0)))
        "acos", "arccos"    -> fromRad(acos(x.coerceIn(-1.0, 1.0)))
        "atan", "arctan"    -> fromRad(atan(x))
        "sinh"              -> sinh(x)
        "cosh"              -> cosh(x)
        "tanh"              -> tanh(x)
        "asinh"             -> ln(x + sqrt(x * x + 1.0))
        "acosh"             -> if (x < 1.0) throw ArithmeticException("acosh: arg < 1")
                               else ln(x + sqrt(x * x - 1.0))
        "atanh"             -> if (abs(x) >= 1.0) throw ArithmeticException("atanh: |x| ≥ 1")
                               else 0.5 * ln((1.0 + x) / (1.0 - x))
        "ln"                -> if (x <= 0.0) throw ArithmeticException("ln: arg ≤ 0") else ln(x)
        "log", "log10"      -> if (x <= 0.0) throw ArithmeticException("log: arg ≤ 0") else log10(x)
        "log2"              -> if (x <= 0.0) throw ArithmeticException("log2: arg ≤ 0") else log2(x)
        "exp"               -> exp(x)
        "sqrt"              -> if (x < 0.0) throw ArithmeticException("√: arg < 0") else sqrt(x)
        "cbrt"              -> cbrt(x)
        "abs"               -> abs(x)
        "ceil"              -> ceil(x)
        "floor"             -> floor(x)
        "round"             -> round(x).toDouble()
        "fact", "factorial" -> factorial(x)
        "sign", "sgn"       -> sign(x)
        "inv"               -> if (x == 0.0) throw ArithmeticException("1/0 undefined") else 1.0 / x
        "rand"              -> Random.nextDouble()
        "ranint"            -> floor(Random.nextDouble() * x)
        else                -> throw IllegalArgumentException("Unknown function: $name")
    }

    // ── Two-argument functions ─────────────────────────────────────────────────

    private fun applyFn2(name: String, a: Double, b: Double): Double = when (name) {
        "logab"  -> if (a <= 0.0 || a == 1.0 || b <= 0.0)
                        throw ArithmeticException("logab: invalid arguments")
                    else log(b, a)
        "npr"    -> permutation(a.toInt(), b.toInt()).toDouble()
        "ncr"    -> combination(a.toInt(), b.toInt()).toDouble()
        "ranint" -> floor(a + Random.nextDouble() * (b - a + 1))
        "gcd"    -> gcd(a.toLong(), b.toLong()).toDouble()
        "lcm"    -> abs(a * b) / gcd(a.toLong(), b.toLong())
        else     -> throw IllegalArgumentException("Unknown 2-arg function: $name")
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

    // ── Combinatorics ─────────────────────────────────────────────────────────

    private fun factorial(n: Double): Double {
        if (n < 0.0 || n != floor(n))
            throw ArithmeticException("Factorial requires non-negative integer")
        if (n > 170.0) return Double.POSITIVE_INFINITY
        var r = 1.0
        for (k in 2..n.toInt()) r *= k
        return r
    }

    private fun permutation(n: Int, r: Int): Long {
        if (r > n || r < 0) throw ArithmeticException("nPr: invalid arguments")
        var result = 1L
        for (i in (n - r + 1)..n) result *= i
        return result
    }

    private fun combination(n: Int, r: Int): Long {
        val k = minOf(r, n - r)
        if (k < 0) throw ArithmeticException("nCr: invalid arguments")
        return permutation(n, k) / factorial(k.toDouble()).toLong()
    }

    private fun gcd(a: Long, b: Long): Long = if (b == 0L) abs(a) else gcd(b, a % b)

    // ── Statistics ────────────────────────────────────────────────────────────

    data class StatResult(
        val n: Int,
        val sum: Double,
        val sumSq: Double,
        val mean: Double,
        val populationStd: Double,    // σx
        val sampleStd: Double,        // sx
        val min: Double,
        val max: Double
    )

    fun calcStats(): StatResult {
        if (statData.isEmpty()) throw ArithmeticException("No data in list")
        val n = statData.size
        val sum = statData.sum()
        val sumSq = statData.sumOf { it * it }
        val mean = sum / n
        val variance = sumSq / n - mean * mean
        val popStd = sqrt(variance.coerceAtLeast(0.0))
        val sampleStd = if (n > 1) sqrt(((sumSq - n * mean * mean) / (n - 1)).coerceAtLeast(0.0)) else 0.0
        return StatResult(n, sum, sumSq, mean, popStd, sampleStd,
            statData.min(), statData.max())
    }

    // ── Result formatting ─────────────────────────────────────────────────────

    fun formatResult(v: Double): String {
        if (v.isNaN())      return "Math ERROR"
        if (v.isInfinite()) return if (v > 0) "+∞" else "-∞"
        if (v == 0.0)       return "0"

        return when (displayMode) {
            DisplayMode.FIX  -> "%.${fixPlaces}f".format(Locale.US, v)
            DisplayMode.SCI  -> formatSci(v, fixPlaces.coerceAtLeast(1))
            DisplayMode.NORMAL -> formatNormal(v)
        }
    }

    private fun formatNormal(v: Double): String {
        if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()
        return if (abs(v) >= 1e10 || (abs(v) < 1e-4 && v != 0.0)) {
            formatSci(v, 6)
        } else {
            "%.10f".format(Locale.US, v).trimEnd('0').trimEnd('.')
        }
    }

    private fun formatSci(v: Double, sig: Int): String {
        val s = "%.${sig}e".format(Locale.US, v)
        val eIdx = s.indexOfFirst { it == 'e' || it == 'E' }
        if (eIdx < 0) return s
        val mantissa = s.substring(0, eIdx).trimEnd('0').trimEnd('.')
        val expVal   = s.substring(eIdx + 1).trimStart('+').toInt()
        return "$mantissa×10^$expVal"
    }

    /** Try to express [v] as a fraction p/q (|q| ≤ 1000). */
    fun toFraction(v: Double): Pair<Long, Long>? {
        if (v == floor(v)) return null
        val sign = if (v < 0) -1L else 1L
        val av = abs(v)
        for (q in 1L..1000L) {
            val p = roundToLong(av * q)
            if (abs(av - p.toDouble() / q) < 1e-9) return Pair(sign * p, q)
        }
        return null
    }

    private fun roundToLong(x: Double): Long =
        x.toLong().let { if (x - it >= 0.5) it + 1 else it }
}
