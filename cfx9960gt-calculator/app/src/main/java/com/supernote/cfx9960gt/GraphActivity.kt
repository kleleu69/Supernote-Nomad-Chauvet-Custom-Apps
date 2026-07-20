package com.supernote.cfx9960gt

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * Graphing screen for the CFX-9960GTe.
 *
 * Provides:
 *  - Y1, Y2, Y3 equation editors
 *  - VIEW WINDOW dialog (Xmin/Xmax/Ymin/Ymax/Xscl/Yscl)
 *  - DRAW button
 *  - TRACE mode (touch to display X/Y coordinates)
 *  - G↔T  graph ↔ table toggle (basic)
 */
class GraphActivity : AppCompatActivity() {

    private lateinit var etY1: EditText
    private lateinit var etY2: EditText
    private lateinit var etY3: EditText
    private lateinit var tvTrace: TextView
    private lateinit var graphView: GraphView
    private lateinit var btnDraw: Button
    private lateinit var btnTrace: Button
    private lateinit var btnWindow: Button
    private lateinit var btnTable: Button

    // View window defaults
    private var xMin = -10.0; private var xMax = 10.0
    private var yMin = -10.0; private var yMax = 10.0
    private var xScl =  2.0;  private var yScl =  2.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        etY1      = findViewById(R.id.etY1)
        etY2      = findViewById(R.id.etY2)
        etY3      = findViewById(R.id.etY3)
        tvTrace   = findViewById(R.id.tvTrace)
        graphView = findViewById(R.id.graphView)
        btnDraw   = findViewById(R.id.btnDraw)
        btnTrace  = findViewById(R.id.btnTrace)
        btnWindow = findViewById(R.id.btnWindow)
        btnTable  = findViewById(R.id.btnTable)

        findViewById<Button>(R.id.btnBackToComp).setOnClickListener { finish() }

        btnDraw.setOnClickListener { drawGraph() }

        btnTrace.setOnClickListener {
            graphView.traceEnabled = !graphView.traceEnabled
            btnTrace.text = if (graphView.traceEnabled) "TRACE ON" else "TRACE"
            tvTrace.visibility = if (graphView.traceEnabled) View.VISIBLE else View.GONE
        }

        btnWindow.setOnClickListener { showViewWindowDialog() }

        btnTable.setOnClickListener { showTableDialog() }

        graphView.onTraceUpdate = { x, y ->
            tvTrace.text = "X=%.6g  Y=%.6g".format(x, y)
        }

        // Pre-fill with a default equation
        etY1.setText("sin(X)")
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    private fun drawGraph() {
        // Validate window
        if (xMin >= xMax || yMin >= yMax) {
            Toast.makeText(this, "Invalid view window", Toast.LENGTH_SHORT).show()
            return
        }

        graphView.xMin   = xMin;  graphView.xMax   = xMax
        graphView.yMin   = yMin;  graphView.yMax   = yMax
        graphView.xScale = xScl;  graphView.yScale = yScl

        val eqs = listOf(
            GraphView.EquationDef(etY1.text.toString(), Color.BLACK, etY1.text.isNotBlank()),
            GraphView.EquationDef(etY2.text.toString(), Color.parseColor("#555555"), etY2.text.isNotBlank()),
            GraphView.EquationDef(etY3.text.toString(), Color.parseColor("#888888"), etY3.text.isNotBlank())
        )
        graphView.setEquations(eqs)
    }

    // ── VIEW WINDOW dialog ────────────────────────────────────────────────────

    private fun showViewWindowDialog() {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 16, 40, 16)
        }

        fun row(label: String, default: String): EditText {
            val ll = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val tv = TextView(this).apply { text = "$label = "; minWidth = 160 }
            val et = EditText(this).apply {
                setText(default)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                            android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            ll.addView(tv)
            ll.addView(et)
            view.addView(ll)
            return et
        }

        val etXmin = row("Xmin", xMin.toString())
        val etXmax = row("Xmax", xMax.toString())
        val etXscl = row("Xscl", xScl.toString())
        val etYmin = row("Ymin", yMin.toString())
        val etYmax = row("Ymax", yMax.toString())
        val etYscl = row("Yscl", yScl.toString())

        AlertDialog.Builder(this)
            .setTitle("View Window")
            .setView(view)
            .setPositiveButton("Set") { _, _ ->
                xMin = etXmin.text.toString().toDoubleOrNull() ?: xMin
                xMax = etXmax.text.toString().toDoubleOrNull() ?: xMax
                xScl = etXscl.text.toString().toDoubleOrNull() ?: xScl
                yMin = etYmin.text.toString().toDoubleOrNull() ?: yMin
                yMax = etYmax.text.toString().toDoubleOrNull() ?: yMax
                yScl = etYscl.text.toString().toDoubleOrNull() ?: yScl
            }
            .setNeutralButton("Default") { _, _ ->
                xMin = -10.0; xMax = 10.0; xScl = 2.0
                yMin = -10.0; yMax = 10.0; yScl = 2.0
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Table dialog ──────────────────────────────────────────────────────────

    private fun showTableDialog() {
        val exprStr = etY1.text.toString().trim()
        if (exprStr.isBlank()) {
            Toast.makeText(this, "Enter Y1 equation first", Toast.LENGTH_SHORT).show()
            return
        }

        val engine = CfxEngine()
        val sb = StringBuilder("    X       │     Y1\n")
        sb.append("────────────────────\n")
        val step = (xMax - xMin) / 10.0
        var x = xMin
        repeat(11) {
            engine.variables["X"] = x
            engine.variables["x"] = x
            val y = try { engine.evaluate(exprStr) } catch (_: Exception) { Double.NaN }
            sb.append("%10.4f │ %10.4f\n".format(x, y))
            x += step
        }

        AlertDialog.Builder(this)
            .setTitle("Table  Y1 = $exprStr")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show()
    }
}
