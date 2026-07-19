package com.supernote.classwiz

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.random.Random

/**
 * Main activity for the ClassWiz scientific calculator.
 *
 * Layout: 5 columns × 8 rows = 40 buttons, optimised for e-ink display.
 *
 * Modifier keys:
 *  SHIFT  – activates the top (orange on real ClassWiz) secondary functions.
 *  ALPHA  – activates variable-name entry (A–F, M).
 */
class MainActivity : AppCompatActivity() {

    private companion object {
        const val GRID_COLUMNS = 6
    }

    // ── Engine & state ─────────────────────────────────────────────────────────

    private val engine = CalculatorEngine()

    /** The expression string being built (uses Unicode: × ÷ π √ etc.) */
    private val expr = StringBuilder()

    private var isShift        = false
    private var isAlpha        = false
    private var justEvaluated  = false   // true right after pressing '='
    private var showFraction   = false   // toggle S⇔D

    // ── View references ────────────────────────────────────────────────────────

    private lateinit var tvShiftInd : TextView
    private lateinit var tvAlphaInd : TextView
    private lateinit var tvAngleMode: TextView
    private lateinit var tvMemInd   : TextView
    private lateinit var tvExpr     : TextView
    private lateinit var tvResult   : TextView
    private lateinit var btnShiftRef: Button
    private lateinit var btnAlphaRef: Button

    private data class KeyDef(
        val label: String,
        val action: String,
        val shiftLabel: String = "",
        val shiftAction: String = "",
        val alphaLabel: String = "",
        val alphaAction: String = ""
    )

    private val keys = listOf(
        // Row 0: Soft keys
        KeyDef("F1", "setup", shiftLabel = "SETUP"),
        KeyDef("F2", "hyp", shiftLabel = "HYP"),
        KeyDef("F3", "rcl", shiftLabel = "STO", shiftAction = "sto"),
        KeyDef("F4", "mode", shiftLabel = "MODE"),
        KeyDef("F5", "eng", shiftLabel = "<-ENG", shiftAction = "engL"),
        KeyDef("F6", "random", shiftLabel = "RND"),

        // Row 1: control/navigation
        KeyDef("SHIFT", "shift"),
        KeyDef("OPTN", "optn"),
        KeyDef("VARS", "vars"),
        KeyDef("MENU", "menu"),
        KeyDef("<-", "left"),
        KeyDef("UP", "up"),

        // Row 2: secondary controls
        KeyDef("ALPHA", "alpha"),
        KeyDef("x^2", "ins:^2", alphaLabel = "A", alphaAction = "ins:A"),
        KeyDef("^", "ins:^", alphaLabel = "B", alphaAction = "ins:B"),
        KeyDef("EXIT", "exit"),
        KeyDef("DOWN", "down"),
        KeyDef("->", "right"),

        // Row 3: trig / logs
        KeyDef("X,T", "ins:X"),
        KeyDef("log", "ins:log(", shiftLabel = "10^x", shiftAction = "ins:10^("),
        KeyDef("ln", "ins:ln(", shiftLabel = "e^x", shiftAction = "ins:e^("),
        KeyDef("sin", "ins:sin(", shiftLabel = "sin^-1", shiftAction = "ins:asin(", alphaLabel = "D", alphaAction = "ins:D"),
        KeyDef("cos", "ins:cos(", shiftLabel = "cos^-1", shiftAction = "ins:acos(", alphaLabel = "E", alphaAction = "ins:E"),
        KeyDef("tan", "ins:tan(", shiftLabel = "tan^-1", shiftAction = "ins:atan(", alphaLabel = "F", alphaAction = "ins:F"),

        // Row 4: parenthesis and edits
        KeyDef("ab/c", "sd"),
        KeyDef("F<>D", "sd"),
        KeyDef("(", "ins:("),
        KeyDef(")", "ins:)"),
        KeyDef(",", "ins:,"),
        KeyDef("DEL", "del"),

        // Row 5: upper numeric block
        KeyDef("7", "ins:7"),
        KeyDef("8", "ins:8"),
        KeyDef("9", "ins:9"),
        KeyDef("AC", "ac"),
        KeyDef("M+", "mem+", shiftLabel = "M-", shiftAction = "mem-", alphaLabel = "M", alphaAction = "ins:M"),
        KeyDef("Ans", "ins:Ans", shiftLabel = "%", shiftAction = "percent"),

        // Row 6
        KeyDef("4", "ins:4"),
        KeyDef("5", "ins:5"),
        KeyDef("6", "ins:6"),
        KeyDef("x", "ins:×"),
        KeyDef("/", "ins:÷"),
        KeyDef("sqrt", "ins:sqrt(", shiftLabel = "cbrt", shiftAction = "ins:cbrt("),

        // Row 7
        KeyDef("1", "ins:1"),
        KeyDef("2", "ins:2"),
        KeyDef("3", "ins:3"),
        KeyDef("+", "ins:+"),
        KeyDef("-", "ins:-"),
        KeyDef("EXE", "eval"),

        // Row 8
        KeyDef("0", "ins:0"),
        KeyDef(".", "ins:."),
        KeyDef("EXP", "exp10"),
        KeyDef("(-)", "ins:-"),
        KeyDef("π", "ins:pi"),
        KeyDef("e", "ins:e")
    )

    // ── onCreate ───────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Disable window animations globally (better for e-ink)
        window.setWindowAnimations(0)
        setContentView(R.layout.activity_main)

        tvShiftInd  = findViewById(R.id.tvShiftIndicator)
        tvAlphaInd  = findViewById(R.id.tvAlphaIndicator)
        tvAngleMode = findViewById(R.id.tvAngleMode)
        tvMemInd    = findViewById(R.id.tvMemIndicator)
        tvExpr      = findViewById(R.id.tvExpr)
        tvResult    = findViewById(R.id.tvResult)

        buildButtonGrid()
        updateDisplay()
    }

    // ── Build button grid ──────────────────────────────────────────────────────

    private fun buildButtonGrid() {
        val grid = findViewById<GridLayout>(R.id.buttonGrid)
        grid.removeAllViews()

        keys.forEachIndexed { idx, key ->
            val label = key.label

            val btn = Button(this)
            btn.text        = label
            btn.isAllCaps   = false
            btn.textSize    = when {
                label.length > 4 -> 11f
                label.length > 3 -> 13f
                else             -> 15f
            }
            btn.typeface = Typeface.MONOSPACE
            btn.setPadding(0, 0, 0, 0)

            // Colour scheme
            styleButton(btn, label)
            btn.text = renderKeyLabel(key)

            // Show SHIFT secondary label in smaller text above
            val legendDescription = buildString {
                append(label)
                if (key.shiftLabel.isNotEmpty()) append(" / SHIFT: ${key.shiftLabel}")
                if (key.alphaLabel.isNotEmpty()) append(" / ALPHA: ${key.alphaLabel}")
            }
            btn.contentDescription = legendDescription

            // Layout params: equal weight in both dimensions
            val rowSpec = GridLayout.spec(idx / GRID_COLUMNS, 1f)
            val colSpec = GridLayout.spec(idx % GRID_COLUMNS, 1f)
            val lp = GridLayout.LayoutParams(rowSpec, colSpec).apply {
                width   = 0
                height  = 0
                setMargins(1, 1, 1, 1)
            }
            btn.layoutParams = lp

            btn.setOnClickListener {
                onButtonPressed(key)
            }

            grid.addView(btn)

            // Keep references to SHIFT / ALPHA buttons for indicator updates
            if (label == "SHIFT") btnShiftRef = btn
            if (label == "ALPHA") btnAlphaRef = btn
        }
    }

    private fun styleButton(btn: Button, label: String) {
        val bgColor: Int
        val textColor: Int
        val bold: Boolean
        when (label) {
            "EXE"   -> { bgColor = Color.parseColor("#3968B6"); textColor = Color.WHITE; bold = true }
            "SHIFT" -> { bgColor = Color.parseColor("#EFA245"); textColor = Color.parseColor("#201A13"); bold = true }
            "ALPHA" -> { bgColor = Color.parseColor("#B54A54"); textColor = Color.parseColor("#FFF5F5"); bold = true }
            "AC", "DEL", "EXIT" -> { bgColor = Color.parseColor("#5A6472"); textColor = Color.WHITE; bold = true }
            "F1", "F2", "F3", "F4", "F5", "F6" -> { bgColor = Color.parseColor("#7AA070"); textColor = Color.parseColor("#F9FFF5"); bold = true }
            "OPTN", "VARS", "MENU", "sin", "cos", "tan", "log", "ln", "sqrt", "x^2", "^", "X,T", "ab/c", "F<>D", "<-", "->", "UP", "DOWN", "M+", "Ans", "EXP", "(-)", "pi", "e" -> {
                bgColor = Color.parseColor("#404C59"); textColor = Color.parseColor("#F2F5FA"); bold = false
            }
            "x", "/", "+", "-" -> { bgColor = Color.parseColor("#6A7381"); textColor = Color.WHITE; bold = true }
            else -> { bgColor = Color.parseColor("#D7DEE6"); textColor = Color.parseColor("#1E2630"); bold = true }
        }
        btn.setTextColor(textColor)
        btn.minHeight = (btn.resources.displayMetrics.density * 54).toInt()
        btn.minWidth = 0
        if (bold) btn.setTypeface(btn.typeface, Typeface.BOLD)
        // GradientDrawable gives us a solid fill + 1 px black border
        btn.background = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = btn.resources.displayMetrics.density * 18
            setStroke((btn.resources.displayMetrics.density * 1.25f).toInt(), Color.parseColor("#1A1A1A"))
        }
    }

    private fun renderKeyLabel(key: KeyDef): CharSequence {
        val builder = SpannableStringBuilder()
        builder.append(key.label)

        if (key.shiftLabel.isNotEmpty()) {
            builder.append("\n")
            val start = builder.length
            builder.append(key.shiftLabel)
            builder.setSpan(RelativeSizeSpan(0.72f), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        if (key.alphaLabel.isNotEmpty()) {
            builder.append("\n")
            val start = builder.length
            builder.append(key.alphaLabel)
            builder.setSpan(RelativeSizeSpan(0.72f), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return builder
    }

    // ── Button press handling ──────────────────────────────────────────────────

    private fun onButtonPressed(key: KeyDef) {
        // Resolve which action fires
        val resolvedAction: String = when {
            isAlpha && key.alphaAction.isNotEmpty() -> {
                clearModifiers()
                key.alphaAction
            }
            isShift && key.shiftAction.isNotEmpty() -> {
                clearModifiers()
                key.shiftAction
            }
            isShift || isAlpha -> {
                // Modifier was set but this key has no alternate — fall through to normal
                clearModifiers()
                key.action
            }
            else -> key.action
        }

        dispatchAction(resolvedAction, key.label)
    }

    /** Execute a single action code. */
    private fun dispatchAction(code: String, label: String) {
        when {
            // ── Modifier toggles ────────────────────────────────────────────
            code == "shift" -> { isShift = !isShift; isAlpha = false; updateStatus(); return }
            code == "alpha" -> { isAlpha = !isAlpha; isShift = false; updateStatus(); return }

            // ── CFX control rows ───────────────────────────────────────────
            code == "optn" -> showOptnDialog()
            code == "vars" -> showRclDialog()
            code == "menu" -> showSetupDialog()
            code == "exit" -> clearModifiers()
            code == "left" -> {
                if (justEvaluated) { expr.clear(); justEvaluated = false }
                else if (expr.isNotEmpty()) expr.deleteCharAt(expr.length - 1)
            }
            code == "right" || code == "up" || code == "down" -> {
                // Cursor navigation is not yet modeled; keep behavior deterministic.
                updateStatus()
                return
            }

            // ── Insert text into expression ─────────────────────────────────
            code.startsWith("ins:") -> {
                val text = code.removePrefix("ins:")
                if (justEvaluated) {
                    // ClassWiz behaviour after '=':
                    //  binary operator  → prepend Ans (e.g.  Ans+3)
                    //  digit / decimal  → start fresh number
                    //  anything else    → start fresh (e.g. sin( after result)
                    if (text in listOf("+", "-", "×", "÷", "^", "%")) {
                        expr.clear(); expr.append("Ans")
                    } else {
                        expr.clear()
                    }
                    justEvaluated = false
                }
                expr.append(text)
            }

            // ── Delete / clear ───────────────────────────────────────────────
            code == "del" -> {
                if (justEvaluated) { expr.clear(); justEvaluated = false }
                else if (expr.isNotEmpty()) expr.deleteCharAt(expr.length - 1)
            }
            code == "ins"  -> { /* insert-mode: no-op for now */ }
            code == "ac"   -> { expr.clear(); justEvaluated = false }

            // ── Evaluate ─────────────────────────────────────────────────────
            code == "eval" -> doEvaluate()

            // ── Angle mode ───────────────────────────────────────────────────
            code == "mode" -> cycleAngleMode()
            code == "setup" -> showSetupDialog()

            // ── Memory ───────────────────────────────────────────────────────
            code == "mem+" -> {
                val v = tryEval() ?: return
                engine.variables["M"] = (engine.variables["M"] ?: 0.0) + v
                updateMemIndicator()
            }
            code == "mem-" -> {
                val v = tryEval() ?: return
                engine.variables["M"] = (engine.variables["M"] ?: 0.0) - v
                updateMemIndicator()
            }
            code == "rcl"  -> showRclDialog()
            code == "sto"  -> showStoDialog()

            // ── S⇔D: toggle standard/decimal display ────────────────────────
            code == "sd"   -> {
                showFraction = !showFraction
                updateDisplay()
                return
            }

            // ── Hyperbolic wrapper ────────────────────────────────────────────
            code == "hyp"  -> showHypDialog()

            // ── Engineering notation ─────────────────────────────────────────
            code == "eng"  -> shiftEngineering(+1)
            code == "engL" -> shiftEngineering(-1)

            // ── ×10ˣ ─────────────────────────────────────────────────────────
            code == "exp10" -> {
                if (justEvaluated) { expr.clear(); justEvaluated = false }
                expr.append("×10^(")
            }

            // ── Log base-a dialog ─────────────────────────────────────────────
            code == "logb" -> {
                if (justEvaluated) { expr.clear(); justEvaluated = false }
                expr.append("log(") // user manually divides: log(x)/log(b)
            }

            // ── Random number ─────────────────────────────────────────────────
            code == "random" -> {
                val r = Random.nextDouble()
                setResultAndClear(engine.formatResult(r))
                return
            }

            // ── Percent ──────────────────────────────────────────────────────
            code == "percent" -> {
                if (expr.isNotEmpty()) expr.append("÷100")
            }

            else -> return   // unhandled — ignore
        }
        updateDisplay()
    }

    // ── Evaluate ───────────────────────────────────────────────────────────────

    private fun doEvaluate() {
        val raw = expr.toString()
        if (raw.isBlank()) return
        try {
            val v = engine.evaluate(raw)
            val formatted = engine.formatResult(v)
            tvResult.text = if (showFraction) {
                val frac = engine.toFraction(v)
                if (frac != null) "${frac.first}/${frac.second}" else formatted
            } else formatted
            justEvaluated = true
        } catch (ex: ArithmeticException) {
            tvResult.text = ex.message ?: "Math ERROR"
            justEvaluated = true
        } catch (ex: IllegalArgumentException) {
            tvResult.text = "Syntax ERROR"
            justEvaluated = true
        }
        updateDisplay()
    }

    /** Evaluate silently, return null on error (for M+/M- operations). */
    private fun tryEval(): Double? {
        val raw = expr.toString()
        return try {
            engine.evaluate(raw)
        } catch (_: Exception) {
            null
        }
    }

    // ── Display helpers ─────────────────────────────────────────────────────────

    private fun updateDisplay() {
        tvExpr.text = expr.toString()
        if (!justEvaluated && expr.isNotEmpty()) {
            // Live preview
            try {
                val v = engine.evaluate(expr.toString())
                tvResult.text = engine.formatResult(v)
            } catch (_: Exception) {
                // Don't show errors during live typing, keep last result
            }
        } else if (expr.isEmpty()) {
            tvResult.text = "0"
        }
        updateStatus()
    }

    private fun updateStatus() {
        tvShiftInd.visibility  = if (isShift) View.VISIBLE else View.INVISIBLE
        tvAlphaInd.visibility  = if (isAlpha) View.VISIBLE else View.INVISIBLE
        tvAngleMode.text = when (engine.angleMode) {
            CalculatorEngine.AngleMode.DEGREE  -> "DEG"
            CalculatorEngine.AngleMode.RADIAN  -> "RAD"
            CalculatorEngine.AngleMode.GRADIAN -> "GRD"
        }
        updateMemIndicator()

        // Visually invert SHIFT / ALPHA buttons when active, keep 1px border
        if (::btnShiftRef.isInitialized) {
            val (bg, fg) = if (isShift) Color.WHITE to Color.BLACK
                           else Color.parseColor("#EFA245") to Color.parseColor("#201A13")
            btnShiftRef.setTextColor(fg)
            btnShiftRef.background = GradientDrawable().apply { setColor(bg); cornerRadius = btnShiftRef.resources.displayMetrics.density * 18; setStroke(1, Color.BLACK) }
        }
        if (::btnAlphaRef.isInitialized) {
            val (bg, fg) = if (isAlpha) Color.WHITE to Color.BLACK
                           else Color.parseColor("#B54A54") to Color.parseColor("#FFF5F5")
            btnAlphaRef.setTextColor(fg)
            btnAlphaRef.background = GradientDrawable().apply { setColor(bg); cornerRadius = btnAlphaRef.resources.displayMetrics.density * 18; setStroke(1, Color.BLACK) }
        }
    }

    private fun updateMemIndicator() {
        val m = engine.variables["M"] ?: 0.0
        tvMemInd.visibility = if (m != 0.0) View.VISIBLE else View.INVISIBLE
    }

    private fun setResultAndClear(text: String) {
        tvResult.text = text
        expr.clear()
        justEvaluated = true
        updateStatus()
    }

    private fun clearModifiers() {
        isShift = false
        isAlpha = false
        updateStatus()
    }

    // ── Angle mode ─────────────────────────────────────────────────────────────

    private fun cycleAngleMode() {
        engine.angleMode = when (engine.angleMode) {
            CalculatorEngine.AngleMode.DEGREE  -> CalculatorEngine.AngleMode.RADIAN
            CalculatorEngine.AngleMode.RADIAN  -> CalculatorEngine.AngleMode.GRADIAN
            CalculatorEngine.AngleMode.GRADIAN -> CalculatorEngine.AngleMode.DEGREE
        }
        updateStatus()
    }

    // ── Engineering notation shift ─────────────────────────────────────────────

    private fun shiftEngineering(direction: Int) {
        val raw = tvResult.text.toString()
        if (raw.contains("ERROR") || raw.contains("Inf")) return
        try {
            val v = raw.replace("×10^", "e").replace(",","").toDoubleOrNull()
                ?: engine.evaluate(expr.toString())
            // Round to nearest engineering exponent (multiple of 3)
            val exp = if (v == 0.0) 0 else floor(log10(abs(v))).toInt()
            val engExp = (exp / 3 + direction) * 3
            val mantissa = v / 10.0.pow(engExp)
            tvResult.text = "${engine.formatResult(mantissa)}×10^$engExp"
        } catch (_: Exception) { }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────────

    private fun showSetupDialog() {
        val items = arrayOf("DEG – Degrees", "RAD – Radians", "GRD – Gradians", "Clear all memory")
        AlertDialog.Builder(this)
            .setTitle("Setup / Mode")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> engine.angleMode = CalculatorEngine.AngleMode.DEGREE
                    1 -> engine.angleMode = CalculatorEngine.AngleMode.RADIAN
                    2 -> engine.angleMode = CalculatorEngine.AngleMode.GRADIAN
                    3 -> { engine.variables.keys.forEach { engine.variables[it] = 0.0 }; engine.lastAnswer = 0.0 }
                }
                updateStatus()
            }
            .show()
    }

    private fun showRclDialog() {
        val varNames = listOf("A", "B", "C", "D", "E", "F", "M", "Ans")
        val items = varNames.map { k ->
            val v = if (k == "Ans") engine.lastAnswer else engine.variables[k] ?: 0.0
            "$k = ${engine.formatResult(v)}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("RCL – Recall variable")
            .setItems(items) { _, which ->
                val name = varNames[which]
                if (justEvaluated) { expr.clear(); justEvaluated = false }
                expr.append(name)
                updateDisplay()
            }
            .show()
    }

    private fun showStoDialog() {
        val v = tryEval() ?: run {
            tvResult.text = "Syntax ERROR"; return
        }
        val varNames = arrayOf("A", "B", "C", "D", "E", "F", "M")
        AlertDialog.Builder(this)
            .setTitle("STO – Store  ${engine.formatResult(v)}")
            .setItems(varNames) { _, which ->
                engine.variables[varNames[which]] = v
                tvResult.text = "${varNames[which]} = ${engine.formatResult(v)}"
                updateMemIndicator()
            }
            .show()
    }

    private fun showHypDialog() {
        val fns = arrayOf("sinh(", "cosh(", "tanh(", "asinh(", "acosh(", "atanh(")
        AlertDialog.Builder(this)
            .setTitle("Hyperbolic functions")
            .setItems(fns) { _, which ->
                if (justEvaluated) { expr.clear(); justEvaluated = false }
                expr.append(fns[which])
                updateDisplay()
            }
            .show()
    }

    private fun showOptnDialog() {
        val labels = arrayOf(
            "abs(",
            "sqrt(",
            "cbrt(",
            "log(",
            "ln(",
            "sin(",
            "cos(",
            "tan(",
            "!",
            "pi",
            "e",
            "Ans"
        )
        val inserts = arrayOf(
            "abs(",
            "sqrt(",
            "cbrt(",
            "log(",
            "ln(",
            "sin(",
            "cos(",
            "tan(",
            "!",
            "π",
            "e",
            "Ans"
        )
        AlertDialog.Builder(this)
            .setTitle("OPTN")
            .setItems(labels) { _, which ->
                if (justEvaluated) { expr.clear(); justEvaluated = false }
                expr.append(inserts[which])
                updateDisplay()
            }
            .show()
    }
}
