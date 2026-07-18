package com.supernote.classwiz

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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

    // ── Button definitions ─────────────────────────────────────────────────────
    //
    // Each entry: Triple(normalLabel, shiftLabel, alphaLabel)
    // The parallel `actions` list holds Triple(normalAction, shiftAction, alphaAction)
    // where each action is a String code handled by dispatchAction().

    private val btnDefs = listOf(
        // ── Row 0 ──────────────────────────────────────────────────────────────
        Triple("SHIFT",   "",       ""),
        Triple("ALPHA",   "",       ""),
        Triple("MODE",    "SETUP",  ""),
        Triple("◄DEL",    "INS",    ""),
        Triple("AC",      "CLR",    ""),
        // ── Row 1 ──────────────────────────────────────────────────────────────
        Triple("sin",     "sin⁻¹", "A"),
        Triple("cos",     "cos⁻¹", "B"),
        Triple("tan",     "tan⁻¹", "C"),
        Triple("ln",      "eˣ",    "D"),
        Triple("log",     "10ˣ",   "E"),
        // ── Row 2 ──────────────────────────────────────────────────────────────
        Triple("x⁻¹",    "x!",    "F"),
        Triple("x²",      "x³",    ""),
        Triple("√x",      "∛x",    ""),
        Triple("^",       "log_a", ""),
        Triple("(",       "|x|",   ""),
        // ── Row 3 ──────────────────────────────────────────────────────────────
        Triple("S⇔D",    "HYP",   ""),
        Triple("M+",      "M-",    "M"),
        Triple("RCL",     "STO",   ""),
        Triple("ENG",     "←ENG",  ""),
        Triple(")",       "Ran#",  ""),
        // ── Row 4 ──────────────────────────────────────────────────────────────
        Triple("7",  "", ""),
        Triple("8",  "", ""),
        Triple("9",  "", ""),
        Triple("÷",  "", ""),
        Triple("×",  "", ""),
        // ── Row 5 ──────────────────────────────────────────────────────────────
        Triple("4",  "", ""),
        Triple("5",  "", ""),
        Triple("6",  "", ""),
        Triple("-",  "", ""),
        Triple("+",  "", ""),
        // ── Row 6 ──────────────────────────────────────────────────────────────
        Triple("1",  "", ""),
        Triple("2",  "", ""),
        Triple("3",  "", ""),
        Triple("π",  "Ran#", ""),
        Triple("e",  "",     ""),
        // ── Row 7 ──────────────────────────────────────────────────────────────
        Triple("0",    "",    ""),
        Triple(".",    "",    ""),
        Triple("×10ˣ","",    ""),
        Triple("Ans",  "%",   ""),
        Triple("=",    "",    ""),
    )

    /** Action codes, parallel to [btnDefs]. */
    private val btnActions = listOf(
        // Row 0
        Triple("shift",       "",          ""),
        Triple("alpha",       "",          ""),
        Triple("mode",        "setup",     ""),
        Triple("del",         "ins",       ""),
        Triple("ac",          "ac",        ""),
        // Row 1
        Triple("ins:sin(",    "ins:asin(", "ins:A"),
        Triple("ins:cos(",    "ins:acos(", "ins:B"),
        Triple("ins:tan(",    "ins:atan(", "ins:C"),
        Triple("ins:ln(",     "ins:e^(",   "ins:D"),
        Triple("ins:log(",    "ins:10^(",  "ins:E"),
        // Row 2
        Triple("ins:^(-1)",   "ins:!",    "ins:F"),
        Triple("ins:^2",      "ins:^3",   ""),
        Triple("ins:sqrt(",   "ins:cbrt(",""),
        Triple("ins:^",       "logb",     ""),
        Triple("ins:(",       "ins:abs(", ""),
        // Row 3
        Triple("sd",          "hyp",      ""),
        Triple("mem+",        "mem-",     "ins:M"),
        Triple("rcl",         "sto",      ""),
        Triple("eng",         "engL",     ""),
        Triple("ins:)",       "random",   ""),
        // Row 4
        Triple("ins:7","",""), Triple("ins:8","",""), Triple("ins:9","",""),
        Triple("ins:÷","",""), Triple("ins:×","",""),
        // Row 5
        Triple("ins:4","",""), Triple("ins:5","",""), Triple("ins:6","",""),
        Triple("ins:-","",""), Triple("ins:+","",""),
        // Row 6
        Triple("ins:1","",""), Triple("ins:2","",""), Triple("ins:3","",""),
        Triple("ins:π","","ins:π"), Triple("ins:e","",""),
        // Row 7
        Triple("ins:0","",""), Triple("ins:.","",""),
        Triple("exp10",       "",         ""),
        Triple("ins:Ans",     "percent",  ""),
        Triple("eval",        "",         ""),
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

        btnDefs.forEachIndexed { idx, def ->
            val (label, shiftLabel, alphaLabel) = def
            val (action, shiftAction, alphaAction) = btnActions[idx]

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

            // Show SHIFT secondary label in smaller text above
            if (shiftLabel.isNotEmpty()) {
                btn.contentDescription = "$label / SHIFT: $shiftLabel"
            }

            // Layout params: equal weight in both dimensions
            val rowSpec = GridLayout.spec(idx / 5, 1f)
            val colSpec = GridLayout.spec(idx % 5, 1f)
            val lp = GridLayout.LayoutParams(rowSpec, colSpec).apply {
                width   = 0
                height  = 0
                setMargins(1, 1, 1, 1)
            }
            btn.layoutParams = lp

            btn.setOnClickListener {
                onButtonPressed(label, shiftLabel, alphaLabel, action, shiftAction, alphaAction, btn)
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
            "="     -> { bgColor = Color.BLACK;                 textColor = Color.WHITE; bold = true  }
            "SHIFT" -> { bgColor = Color.parseColor("#1A1A1A"); textColor = Color.WHITE; bold = false }
            "ALPHA" -> { bgColor = Color.parseColor("#333333"); textColor = Color.WHITE; bold = false }
            "AC"    -> { bgColor = Color.parseColor("#444444"); textColor = Color.WHITE; bold = false }
            "◄DEL"  -> { bgColor = Color.parseColor("#666666"); textColor = Color.WHITE; bold = false }
            "÷", "×", "-", "+" ->
                        { bgColor = Color.WHITE;                textColor = Color.BLACK; bold = true  }
            else    -> { bgColor = Color.WHITE;                 textColor = Color.BLACK; bold = false }
        }
        btn.setTextColor(textColor)
        if (bold) btn.setTypeface(btn.typeface, Typeface.BOLD)
        // GradientDrawable gives us a solid fill + 1 px black border
        btn.background = GradientDrawable().apply {
            setColor(bgColor)
            setStroke(1, Color.BLACK)
        }
    }

    // ── Button press handling ──────────────────────────────────────────────────

    private fun onButtonPressed(
        label: String, shiftLabel: String, alphaLabel: String,
        action: String, shiftAction: String, alphaAction: String,
        btn: Button
    ) {
        // Resolve which action fires
        val resolvedAction: String = when {
            isAlpha && alphaAction.isNotEmpty() -> {
                clearModifiers()
                alphaAction
            }
            isShift && shiftAction.isNotEmpty() -> {
                clearModifiers()
                shiftAction
            }
            isShift || isAlpha -> {
                // Modifier was set but this key has no alternate — fall through to normal
                clearModifiers()
                action
            }
            else -> action
        }

        dispatchAction(resolvedAction, label)
    }

    /** Execute a single action code. */
    private fun dispatchAction(code: String, label: String) {
        when {
            // ── Modifier toggles ────────────────────────────────────────────
            code == "shift" -> { isShift = !isShift; isAlpha = false; updateStatus(); return }
            code == "alpha" -> { isAlpha = !isAlpha; isShift = false; updateStatus(); return }

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
                           else Color.parseColor("#1A1A1A") to Color.WHITE
            btnShiftRef.setTextColor(fg)
            btnShiftRef.background = GradientDrawable().apply { setColor(bg); setStroke(1, Color.BLACK) }
        }
        if (::btnAlphaRef.isInitialized) {
            val (bg, fg) = if (isAlpha) Color.WHITE to Color.BLACK
                           else Color.parseColor("#333333") to Color.WHITE
            btnAlphaRef.setTextColor(fg)
            btnAlphaRef.background = GradientDrawable().apply { setColor(bg); setStroke(1, Color.BLACK) }
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
}
