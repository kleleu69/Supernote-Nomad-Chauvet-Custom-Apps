package com.supernote.cfx9960gt

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10

/**
 * Casio CFX-9960GTe – Main Activity (COMP mode).
 *
 * Keyboard layout: 6 columns × 9 rows  (54 buttons).
 *
 * Row 0  – F1…F6   soft-menu keys
 * Row 1  – SHIFT ALPHA x/θ/T MENU ▲ AC
 * Row 2  – OPTN VARS DEL ◄ ▼ ►
 * Row 3  – x²  x⁻¹  log  ln  (-)  EXIT
 * Row 4  – sin  cos  tan  ×10ˣ  HYP  EXP(unused/blank)
 * Row 5  – 7  8  9  (  )  ÷
 * Row 6  – 4  5  6  ×  √x  π
 * Row 7  – 1  2  3  +  -  Ans
 * Row 8  – 0  .  ,  ENG  EXE  [blank]
 */
class MainActivity : AppCompatActivity() {

    // ── Engine & state ─────────────────────────────────────────────────────────

    private val engine = CfxEngine()
    private val expr   = StringBuilder()

    private var isShift        = false
    private var isAlpha        = false
    private var isHyp          = false
    private var justEvaluated  = false
    private var showFraction   = false

    // history: list of Pair(expression, result)
    private val history = mutableListOf<Pair<String, String>>()

    // ── View references ────────────────────────────────────────────────────────

    private lateinit var tvShiftInd  : TextView
    private lateinit var tvAlphaInd  : TextView
    private lateinit var tvHypInd    : TextView
    private lateinit var tvAngleMode : TextView
    private lateinit var tvModeInd   : TextView
    private lateinit var tvMemInd    : TextView
    private lateinit var tvExpr      : TextView
    private lateinit var tvResult    : TextView
    private lateinit var fKeyBar     : LinearLayout   // F1-F6 labels at display bottom

    private val fKeyLabels = arrayOfNulls<TextView>(6)
    private val btnRefs    = mutableMapOf<String, Button>()

    // ── Key definitions ────────────────────────────────────────────────────────
    //
    // Each entry: label / shiftLabel / alphaLabel / normalAction / shiftAction / alphaAction
    // Empty string = no secondary function.

    data class KeyDef(
        val label: String,
        val shiftLabel: String  = "",
        val alphaLabel: String  = "",
        val action: String      = label,
        val shiftAction: String = "",
        val alphaAction: String = ""
    )

    // 6 columns × 9 rows = 54 keys
    private val keyDefs: List<KeyDef?> = listOf(
        // ── Row 0 : F-keys ────────────────────────────────────────────────────
        KeyDef("F1",    action = "F1"),
        KeyDef("F2",    action = "F2"),
        KeyDef("F3",    action = "F3"),
        KeyDef("F4",    action = "F4"),
        KeyDef("F5",    action = "F5"),
        KeyDef("F6",    action = "F6"),
        // ── Row 1 ─────────────────────────────────────────────────────────────
        KeyDef("SHIFT",  action = "SHIFT"),
        KeyDef("ALPHA",  action = "ALPHA"),
        KeyDef("x,θ,T",  shiftLabel = "CLR",  alphaLabel = "X",
               action = "VAR_X",  shiftAction = "CLR",  alphaAction = "ALPHA_X"),
        KeyDef("MENU",   shiftLabel = "SETUP", action = "MENU", shiftAction = "SETUP"),
        KeyDef("▲",      shiftLabel = "QUIT",  alphaLabel = "Y",
               action = "CURSOR_UP", shiftAction = "QUIT", alphaAction = "ALPHA_Y"),
        KeyDef("AC",     shiftLabel = "OFF",   action = "AC", shiftAction = "AC"),
        // ── Row 2 ─────────────────────────────────────────────────────────────
        KeyDef("OPTN",   action = "OPTN"),
        KeyDef("VARS",   action = "VARS"),
        KeyDef("DEL",    shiftLabel = "INS",   action = "DEL", shiftAction = "INS"),
        KeyDef("◄",      alphaLabel = "Z",
               action = "CURSOR_LEFT", alphaAction = "ALPHA_Z"),
        KeyDef("▼",      shiftLabel = "EXIT",  action = "CURSOR_DOWN", shiftAction = "EXIT_MODE"),
        KeyDef("►",      action = "CURSOR_RIGHT"),
        // ── Row 3 ─────────────────────────────────────────────────────────────
        KeyDef("x²",     shiftLabel = "√",     alphaLabel = "A",
               action = "SQUARE",    shiftAction = "SQRT",     alphaAction = "ALPHA_A"),
        KeyDef("x⁻¹",    shiftLabel = "x!",    alphaLabel = "B",
               action = "RECIP",     shiftAction = "FACT",     alphaAction = "ALPHA_B"),
        KeyDef("log",    shiftLabel = "10ˣ",   alphaLabel = "C",
               action = "LOG",       shiftAction = "TEN_X",    alphaAction = "ALPHA_C"),
        KeyDef("ln",     shiftLabel = "eˣ",    alphaLabel = "D",
               action = "LN",        shiftAction = "EXP_FN",   alphaAction = "ALPHA_D"),
        KeyDef("(-)",    shiftLabel = "INS",   alphaLabel = "E",
               action = "NEG",       shiftAction = "INS",      alphaAction = "ALPHA_E"),
        KeyDef("EXIT",   action = "EXIT_MODE"),
        // ── Row 4 ─────────────────────────────────────────────────────────────
        KeyDef("sin",    shiftLabel = "sin⁻¹", alphaLabel = "F",
               action = "SIN",       shiftAction = "ASIN",     alphaAction = "ALPHA_F"),
        KeyDef("cos",    shiftLabel = "cos⁻¹", alphaLabel = "G",
               action = "COS",       shiftAction = "ACOS",     alphaAction = "ALPHA_G"),
        KeyDef("tan",    shiftLabel = "tan⁻¹", alphaLabel = "H",
               action = "TAN",       shiftAction = "ATAN",     alphaAction = "ALPHA_H"),
        KeyDef("×10ˣ",  shiftLabel = "ENG",   alphaLabel = "I",
               action = "EXP_E",    shiftAction = "ENG",      alphaAction = "ALPHA_I"),
        KeyDef("HYP",    action = "HYP"),
        KeyDef("∛x",     shiftLabel = "∜x",   action = "CBRT", shiftAction = "ROOT4"),
        // ── Row 5 ─────────────────────────────────────────────────────────────
        KeyDef("7",      alphaLabel = "J",  action = "7",  alphaAction = "ALPHA_J"),
        KeyDef("8",      alphaLabel = "K",  action = "8",  alphaAction = "ALPHA_K"),
        KeyDef("9",      alphaLabel = "L",  action = "9",  alphaAction = "ALPHA_L"),
        KeyDef("(",      shiftLabel = "{",  alphaLabel = "M",
               action = "LPAREN",    shiftAction = "LBRACE",  alphaAction = "ALPHA_M"),
        KeyDef(")",      shiftLabel = "}",  alphaLabel = "N",
               action = "RPAREN",    shiftAction = "RBRACE",  alphaAction = "ALPHA_N"),
        KeyDef("÷",      alphaLabel = "O",  action = "÷",  alphaAction = "ALPHA_O"),
        // ── Row 6 ─────────────────────────────────────────────────────────────
        KeyDef("4",      alphaLabel = "P",  action = "4",  alphaAction = "ALPHA_P"),
        KeyDef("5",      alphaLabel = "Q",  action = "5",  alphaAction = "ALPHA_Q"),
        KeyDef("6",      alphaLabel = "R",  action = "6",  alphaAction = "ALPHA_R"),
        KeyDef("×",      alphaLabel = "S",  action = "×",  alphaAction = "ALPHA_S"),
        KeyDef("√x",     shiftLabel = "x²",alphaLabel = "T",
               action = "SQRT",     shiftAction = "SQUARE",  alphaAction = "ALPHA_T"),
        KeyDef("π",      shiftLabel = "e",  action = "PI",  shiftAction = "EULER"),
        // ── Row 7 ─────────────────────────────────────────────────────────────
        KeyDef("1",      alphaLabel = "U",  action = "1",  alphaAction = "ALPHA_U"),
        KeyDef("2",      alphaLabel = "V",  action = "2",  alphaAction = "ALPHA_V"),
        KeyDef("3",      alphaLabel = "W",  action = "3",  alphaAction = "ALPHA_W"),
        KeyDef("+",      alphaLabel = " ",  action = "+"),
        KeyDef("-",      alphaLabel = " ",  action = "-"),
        KeyDef("Ans",    shiftLabel = "RCL", alphaLabel = "→",
               action = "ANS",      shiftAction = "RCL"),
        // ── Row 8 ─────────────────────────────────────────────────────────────
        KeyDef("0",      alphaLabel = "~",  action = "0"),
        KeyDef(".",      shiftLabel = "…",  action = "."),
        KeyDef(",",      alphaLabel = "\"", action = ","),
        KeyDef("M+",     shiftLabel = "M-", action = "M_PLUS", shiftAction = "M_MINUS"),
        KeyDef("EXE",    action = "EXE"),
        null   // blank cell
    )

    // ── Soft F-key labels in COMP normal state ─────────────────────────────────
    private val fLabelsComp = listOf("COMP", "GRAPH", "STAT", "DEG", "RAD", "GRA")
    private val fLabelsSetup = listOf("Deg", "Rad", "Gra", "Fix", "Sci", "Norm")
    private val fLabelsOptn  = listOf("LIST", "MAT", "CPLX", "CALC", "STAT", "▶")
    private var currentFLabels: List<String> = fLabelsComp

    // ── onCreate ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvShiftInd  = findViewById(R.id.tvShiftIndicator)
        tvAlphaInd  = findViewById(R.id.tvAlphaIndicator)
        tvHypInd    = findViewById(R.id.tvHypIndicator)
        tvAngleMode = findViewById(R.id.tvAngleMode)
        tvModeInd   = findViewById(R.id.tvModeIndicator)
        tvMemInd    = findViewById(R.id.tvMemIndicator)
        tvExpr      = findViewById(R.id.tvExpr)
        tvResult    = findViewById(R.id.tvResult)
        fKeyBar     = findViewById(R.id.fKeyBar)

        // Bind F-key label TextViews
        fKeyLabels[0] = findViewById(R.id.tvF1)
        fKeyLabels[1] = findViewById(R.id.tvF2)
        fKeyLabels[2] = findViewById(R.id.tvF3)
        fKeyLabels[3] = findViewById(R.id.tvF4)
        fKeyLabels[4] = findViewById(R.id.tvF5)
        fKeyLabels[5] = findViewById(R.id.tvF6)

        buildGrid()
        updateDisplay()
        updateFKeyBar(fLabelsComp)
    }

    // ── Button grid ────────────────────────────────────────────────────────────

    private fun buildGrid() {
        val grid = findViewById<GridLayout>(R.id.buttonGrid)
        grid.removeAllViews()
        grid.columnCount = 6
        grid.rowCount    = 9

        keyDefs.forEachIndexed { idx, keyDef ->
            val col = idx % 6
            val row = idx / 6

            val btn = Button(this).apply {
                isAllCaps = false
                typeface  = Typeface.MONOSPACE
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, 0, 0, 0)
            }

            val params = GridLayout.LayoutParams().apply {
                columnSpec = GridLayout.spec(col, 1, 1f)
                rowSpec    = GridLayout.spec(row, 1, 1f)
                width  = 0
                height = 0
                setMargins(1, 1, 1, 1)
            }
            btn.layoutParams = params

            if (keyDef == null) {
                btn.visibility = View.INVISIBLE
                grid.addView(btn)
                return@forEachIndexed
            }

            // Style based on key type
            val bgColor: Int
            val textColor: Int
            when {
                keyDef.label == "EXE" -> {
                    bgColor   = Color.parseColor("#000000")
                    textColor = Color.WHITE
                }
                keyDef.label in listOf("SHIFT", "ALPHA", "AC") -> {
                    bgColor   = Color.parseColor("#333333")
                    textColor = Color.WHITE
                }
                keyDef.label.startsWith("F") && keyDef.label.length == 2 -> {
                    bgColor   = Color.parseColor("#1A1A1A")
                    textColor = Color.WHITE
                }
                keyDef.label in listOf("÷", "×", "+", "-") -> {
                    bgColor   = Color.parseColor("#1A1A1A")
                    textColor = Color.WHITE
                }
                keyDef.label[0].isDigit() || keyDef.label == "." || keyDef.label == "," -> {
                    bgColor   = Color.WHITE
                    textColor = Color.BLACK
                }
                else -> {
                    bgColor   = Color.parseColor("#EEEEEE")
                    textColor = Color.BLACK
                }
            }

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(bgColor)
                setStroke(1, Color.BLACK)
                cornerRadius = 3f
            }
            btn.background = bg
            btn.setTextColor(textColor)

            // Build multi-line label: shiftLabel (small orange) / mainLabel / alphaLabel (small gray)
            btn.text = buildButtonText(keyDef)

            if (keyDef.label in listOf("SHIFT", "ALPHA")) {
                btn.tag = keyDef.label
                btnRefs[keyDef.label] = btn
            }

            btn.setOnClickListener { onKeyPressed(keyDef) }
            grid.addView(btn)
        }
    }

    private fun buildButtonText(k: KeyDef): String {
        val sb = StringBuilder()
        if (k.shiftLabel.isNotEmpty()) sb.append(k.shiftLabel).append("\n")
        sb.append(k.label)
        if (k.alphaLabel.isNotEmpty() && k.alphaLabel.trim().isNotEmpty())
            sb.append("\n").append(k.alphaLabel)
        return sb.toString()
    }

    // ── Key press dispatch ────────────────────────────────────────────────────

    private fun onKeyPressed(k: KeyDef) {
        val action = when {
            isAlpha && k.alphaAction.isNotEmpty() -> k.alphaAction
            isShift && k.shiftAction.isNotEmpty() -> k.shiftAction
            else                                   -> k.action
        }
        // Reset shift/alpha/hyp (unless pressing SHIFT, ALPHA, or HYP itself)
        if (action !in listOf("SHIFT", "ALPHA", "HYP")) {
            val wasHyp = isHyp
            isShift = false; isAlpha = false; isHyp = false
            updateModifierIndicators()
            dispatchAction(action, wasHyp)
        } else {
            dispatchAction(action, isHyp)
        }
    }

    private fun dispatchAction(action: String, wasHyp: Boolean) {
        when (action) {
            // ── Modifiers ──────────────────────────────────────────────────────
            "SHIFT" -> { isShift = !isShift; if (isShift) { isAlpha = false; isHyp = false }; updateModifierIndicators(); return }
            "ALPHA" -> { isAlpha = !isAlpha; if (isAlpha) { isShift = false; isHyp = false }; updateModifierIndicators(); return }
            "HYP"   -> { isHyp   = !isHyp;  if (isHyp)   { isShift = false; isAlpha = false }; updateModifierIndicators(); return }

            // ── AC / CLR ───────────────────────────────────────────────────────
            "AC"    -> { expr.clear(); justEvaluated = false; updateDisplay(); return }
            "CLR"   -> { expr.clear(); justEvaluated = false; updateDisplay(); return }

            // ── Delete ─────────────────────────────────────────────────────────
            "DEL"   -> {
                if (expr.isNotEmpty()) {
                    expr.deleteCharAt(expr.length - 1)
                    // Remove dangling "×10^" or "sin(" prefixes
                    while (expr.endsWith("×10^") || expr.endsWith("sin(") ||
                           expr.endsWith("cos(") || expr.endsWith("tan(") ||
                           expr.endsWith("log(") || expr.endsWith("ln(")  ||
                           expr.endsWith("asin(") || expr.endsWith("acos(") ||
                           expr.endsWith("atan(")) {
                        // keep the parenthesis in these cases – do nothing extra
                        break
                    }
                }
                updateDisplay(); return
            }

            // ── EXE / = ────────────────────────────────────────────────────────
            "EXE" -> {
                if (expr.isEmpty()) return
                try {
                    val result = engine.evaluate(expr.toString())
                    val formatted = engine.formatResult(result)
                    history.add(Pair(expr.toString(), formatted))
                    if (history.size > 10) history.removeAt(0)
                    tvResult.text = formatted
                    justEvaluated = true
                } catch (ex: Exception) {
                    tvResult.text = "ERROR"
                }
                updateDisplay(); return
            }

            // ── Numerics ───────────────────────────────────────────────────────
            "0","1","2","3","4","5","6","7","8","9","." -> {
                prepareForInput(); append(action); updateDisplay(); return
            }
            "," -> { append(","); updateDisplay(); return }

            // ── Operators ──────────────────────────────────────────────────────
            "+","-" -> { append(action); updateDisplay(); return }
            "×"     -> { append("×"); updateDisplay(); return }
            "÷"     -> { append("÷"); updateDisplay(); return }

            // ── Functions (with hyperbolic modifier) ──────────────────────────
            "SIN"  -> { prepareForInput(); append(if (wasHyp) "sinh(" else "sin(");  updateDisplay(); return }
            "COS"  -> { prepareForInput(); append(if (wasHyp) "cosh(" else "cos(");  updateDisplay(); return }
            "TAN"  -> { prepareForInput(); append(if (wasHyp) "tanh(" else "tan(");  updateDisplay(); return }
            "ASIN" -> { prepareForInput(); append(if (wasHyp) "asinh(" else "asin("); updateDisplay(); return }
            "ACOS" -> { prepareForInput(); append(if (wasHyp) "acosh(" else "acos("); updateDisplay(); return }
            "ATAN" -> { prepareForInput(); append(if (wasHyp) "atanh(" else "atan("); updateDisplay(); return }

            "LOG"    -> { prepareForInput(); append("log(");   updateDisplay(); return }
            "TEN_X"  -> { prepareForInput(); append("10^(");  updateDisplay(); return }
            "LN"     -> { prepareForInput(); append("ln(");    updateDisplay(); return }
            "EXP_FN" -> { prepareForInput(); append("exp(");   updateDisplay(); return }

            "SQUARE" -> { append("²");    updateDisplay(); return }
            "SQRT"   -> { prepareForInput(); append("√(");  updateDisplay(); return }
            "RECIP"  -> { append("⁻¹");   updateDisplay(); return }
            "FACT"   -> { append("!");    updateDisplay(); return }
            "CBRT"   -> { prepareForInput(); append("∛(");  updateDisplay(); return }
            "ROOT4"  -> { prepareForInput(); append("("); append("^(1÷4)×("); updateDisplay(); return }

            "LPAREN"  -> { append("("); updateDisplay(); return }
            "RPAREN"  -> { append(")"); updateDisplay(); return }
            "LBRACE"  -> { append("{"); updateDisplay(); return }
            "RBRACE"  -> { append("}"); updateDisplay(); return }

            // ── EXP notation ──────────────────────────────────────────────────
            "EXP_E" -> { append("×10^"); updateDisplay(); return }
            "ENG"   -> { convertToEng(); updateDisplay(); return }

            // ── Constants & variables ─────────────────────────────────────────
            "PI"    -> { prepareForInput(); append("π");   updateDisplay(); return }
            "EULER" -> { prepareForInput(); append("e");   updateDisplay(); return }
            "ANS"   -> { prepareForInput(); append("Ans"); updateDisplay(); return }
            "NEG"   -> { append("-"); updateDisplay(); return }

            "VAR_X" -> { prepareForInput(); append("X"); updateDisplay(); return }

            // ── Alpha variable entry ───────────────────────────────────────────
            else -> {
                if (action.startsWith("ALPHA_")) {
                    val v = action.removePrefix("ALPHA_")
                    prepareForInput(); append(v); updateDisplay(); return
                }
                // F-keys
                if (action.startsWith("F") && action.length == 2) {
                    val idx = action[1].digitToIntOrNull()
                    if (idx != null && idx in 1..6) handleFKey(idx - 1)
                    return
                }
                // Mode actions
                when (action) {
                    "MENU"      -> showMenuDialog()
                    "SETUP"     -> showSetupMenu()
                    "OPTN"      -> showOptnMenu()
                    "VARS"      -> showVarsMenu()
                    "EXIT_MODE" -> { /* nothing to exit in COMP */ }
                    "QUIT"      -> finish()
                    "M_PLUS"    -> {
                        try {
                            val v = engine.evaluate(expr.toString())
                            engine.variables["M"] = (engine.variables["M"] ?: 0.0) + v
                            updateMemIndicator()
                        } catch (_: Exception) {}
                    }
                    "M_MINUS"   -> {
                        try {
                            val v = engine.evaluate(expr.toString())
                            engine.variables["M"] = (engine.variables["M"] ?: 0.0) - v
                            updateMemIndicator()
                        } catch (_: Exception) {}
                    }
                    "RCL" -> {
                        prepareForInput(); append("M"); updateDisplay()
                    }
                    else -> {} // unhandled actions silently ignored
                }
            }
        }
    }

    // ── Input helpers ──────────────────────────────────────────────────────────

    private fun prepareForInput() {
        if (justEvaluated) {
            // Start fresh unless continuing with an operator
            justEvaluated = false
        }
    }

    private fun append(s: String) {
        if (justEvaluated && s[0].isDigit()) {
            expr.clear()
            justEvaluated = false
        }
        expr.append(s)
    }

    private fun convertToEng() {
        val v = try { engine.evaluate(expr.toString()) } catch (_: Exception) { return }
        val exp3 = (floor(log10(Math.abs(v)) / 3.0) * 3.0).toInt()
        val mantissa = v / Math.pow(10.0, exp3.toDouble())
        expr.clear()
        expr.append("${engine.formatResult(mantissa)}×10^$exp3")
    }

    // ── F-key actions ──────────────────────────────────────────────────────────

    private fun handleFKey(idx: Int) {
        when (currentFLabels) {
            fLabelsComp -> when (idx) {
                0 -> { /* already in COMP */ }
                1 -> launchGraph()
                2 -> showStatMode()
                3 -> { engine.angleMode = CfxEngine.AngleMode.DEGREE;  updateAngleMode() }
                4 -> { engine.angleMode = CfxEngine.AngleMode.RADIAN;  updateAngleMode() }
                5 -> { engine.angleMode = CfxEngine.AngleMode.GRADIAN; updateAngleMode() }
            }
            fLabelsSetup -> when (idx) {
                0 -> { engine.angleMode = CfxEngine.AngleMode.DEGREE;  updateAngleMode() }
                1 -> { engine.angleMode = CfxEngine.AngleMode.RADIAN;  updateAngleMode() }
                2 -> { engine.angleMode = CfxEngine.AngleMode.GRADIAN; updateAngleMode() }
                3 -> showFixDialog()
                4 -> { engine.displayMode = CfxEngine.DisplayMode.SCI;    updateDisplay() }
                5 -> { engine.displayMode = CfxEngine.DisplayMode.NORMAL; updateDisplay() }
            }
        }
        currentFLabels = fLabelsComp
        updateFKeyBar(fLabelsComp)
    }

    // ── Menu dialogs ───────────────────────────────────────────────────────────

    private fun showMenuDialog() {
        AlertDialog.Builder(this)
            .setTitle("MENU  –  Select Mode")
            .setItems(arrayOf(
                "1: COMP  (Standard Calculator)",
                "2: GRAPH (Function Graphing)",
                "3: STAT  (Statistics)",
                "4: SETUP (Settings)"
            )) { _, which ->
                when (which) {
                    0 -> { /* already in COMP */ }
                    1 -> launchGraph()
                    2 -> showStatMode()
                    3 -> showSetupMenu()
                }
            }
            .show()
    }

    private fun showSetupMenu() {
        currentFLabels = fLabelsSetup
        updateFKeyBar(fLabelsSetup)
        AlertDialog.Builder(this)
            .setTitle("SETUP")
            .setItems(arrayOf(
                "Angle: Degrees",
                "Angle: Radians",
                "Angle: Gradians",
                "Display: Fix n",
                "Display: Sci",
                "Display: Normal"
            )) { _, which ->
                when (which) {
                    0 -> { engine.angleMode = CfxEngine.AngleMode.DEGREE;  updateAngleMode() }
                    1 -> { engine.angleMode = CfxEngine.AngleMode.RADIAN;  updateAngleMode() }
                    2 -> { engine.angleMode = CfxEngine.AngleMode.GRADIAN; updateAngleMode() }
                    3 -> showFixDialog()
                    4 -> { engine.displayMode = CfxEngine.DisplayMode.SCI;    updateDisplay() }
                    5 -> { engine.displayMode = CfxEngine.DisplayMode.NORMAL; updateDisplay() }
                }
                currentFLabels = fLabelsComp
                updateFKeyBar(fLabelsComp)
            }
            .setOnDismissListener { currentFLabels = fLabelsComp; updateFKeyBar(fLabelsComp) }
            .show()
    }

    private fun showFixDialog() {
        AlertDialog.Builder(this)
            .setTitle("Fix: decimal places (0–9)")
            .setItems(Array(10) { it.toString() }) { _, which ->
                engine.displayMode = CfxEngine.DisplayMode.FIX
                engine.fixPlaces   = which
                updateDisplay()
            }.show()
    }

    private fun showOptnMenu() {
        currentFLabels = fLabelsOptn
        updateFKeyBar(fLabelsOptn)
        AlertDialog.Builder(this)
            .setTitle("OPTN")
            .setItems(arrayOf("nPr", "nCr", "Ran#", "logab(b,x)", "Close")) { _, which ->
                when (which) {
                    0 -> { append("npr("); updateDisplay() }
                    1 -> { append("ncr("); updateDisplay() }
                    2 -> { append("rand"); updateDisplay() }
                    3 -> { append("logab("); updateDisplay() }
                }
                currentFLabels = fLabelsComp
                updateFKeyBar(fLabelsComp)
            }
            .setOnDismissListener { currentFLabels = fLabelsComp; updateFKeyBar(fLabelsComp) }
            .show()
    }

    private fun showVarsMenu() {
        val varNames = engine.variables.keys.sorted()
        val items = varNames.map { "$it = ${engine.formatResult(engine.variables[it] ?: 0.0)}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("VARS – Recall Variable")
            .setItems(items) { _, which ->
                prepareForInput(); append(varNames[which]); updateDisplay()
            }
            .show()
    }

    private fun showStatMode() {
        val statView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        val tvInfo = TextView(this).apply {
            text = "Enter data values separated by spaces or newlines"
            textSize = 12f
        }
        val etData = EditText(this).apply {
            hint = "e.g. 1 2 3 4 5"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            if (engine.statData.isNotEmpty())
                setText(engine.statData.joinToString(" "))
        }
        statView.addView(tvInfo)
        statView.addView(etData)

        AlertDialog.Builder(this)
            .setTitle("STAT – 1-Variable")
            .setView(statView)
            .setPositiveButton("Calc") { _, _ ->
                engine.statData.clear()
                etData.text.toString().trim()
                    .split(Regex("[\\s,]+"))
                    .mapNotNull { it.toDoubleOrNull() }
                    .forEach { engine.statData.add(it) }
                try {
                    val s = engine.calcStats()
                    AlertDialog.Builder(this)
                        .setTitle("Statistics Results")
                        .setMessage(
                            "n     = ${s.n}\n" +
                            "Σx    = ${engine.formatResult(s.sum)}\n" +
                            "Σx²   = ${engine.formatResult(s.sumSq)}\n" +
                            "x̄     = ${engine.formatResult(s.mean)}\n" +
                            "σx    = ${engine.formatResult(s.populationStd)}\n" +
                            "sx    = ${engine.formatResult(s.sampleStd)}\n" +
                            "Min   = ${engine.formatResult(s.min)}\n" +
                            "Max   = ${engine.formatResult(s.max)}"
                        )
                        .setPositiveButton("OK", null)
                        .show()
                } catch (ex: Exception) {
                    Toast.makeText(this, ex.message, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ── Graph ──────────────────────────────────────────────────────────────────

    private fun launchGraph() {
        startActivity(Intent(this, GraphActivity::class.java))
    }

    // ── Display update ─────────────────────────────────────────────────────────

    private fun updateDisplay() {
        tvExpr.text   = if (expr.isNotEmpty()) expr.toString() else ""
        if (!justEvaluated) {
            // Live eval preview
            if (expr.isNotEmpty()) {
                try {
                    val v = engine.evaluate(expr.toString())
                    tvResult.text = engine.formatResult(v)
                } catch (_: Exception) {
                    tvResult.text = ""
                }
            } else {
                tvResult.text = engine.formatResult(engine.lastAnswer)
            }
        }
        updateAngleMode()
        updateMemIndicator()
    }

    private fun updateModifierIndicators() {
        tvShiftInd.visibility = if (isShift) View.VISIBLE else View.INVISIBLE
        tvAlphaInd.visibility = if (isAlpha) View.VISIBLE else View.INVISIBLE
        tvHypInd.visibility   = if (isHyp)   View.VISIBLE else View.INVISIBLE

        // Visually highlight SHIFT/ALPHA buttons
        btnRefs["SHIFT"]?.let {
            val bg = it.background as? GradientDrawable ?: return@let
            bg.setColor(if (isShift) Color.BLACK else Color.parseColor("#333333"))
        }
        btnRefs["ALPHA"]?.let {
            val bg = it.background as? GradientDrawable ?: return@let
            bg.setColor(if (isAlpha) Color.BLACK else Color.parseColor("#333333"))
        }
    }

    private fun updateAngleMode() {
        tvAngleMode.text = when (engine.angleMode) {
            CfxEngine.AngleMode.DEGREE  -> "D"
            CfxEngine.AngleMode.RADIAN  -> "R"
            CfxEngine.AngleMode.GRADIAN -> "G"
        }
    }

    private fun updateMemIndicator() {
        val m = engine.variables["M"] ?: 0.0
        tvMemInd.visibility = if (m != 0.0) View.VISIBLE else View.INVISIBLE
    }

    private fun updateFKeyBar(labels: List<String>) {
        for (i in 0..5) {
            fKeyLabels[i]?.text = labels.getOrElse(i) { "" }
        }
    }
}
