package com.supernote.cfx9960gt

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Custom canvas view that renders function graphs for the CFX-9960GTe.
 *
 * Features:
 *  - Up to 3 simultaneous equations (Y1, Y2, Y3)
 *  - Configurable view window (Xmin/Xmax/Ymin/Ymax + scale marks)
 *  - Axes drawn with tick marks and labels
 *  - Touch-to-trace: tap/drag shows the cursor position
 *  - Grid lines (optional)
 */
class GraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── View window ────────────────────────────────────────────────────────────

    var xMin: Double = -10.0
    var xMax: Double =  10.0
    var yMin: Double = -10.0
    var yMax: Double =  10.0
    var xScale: Double = 1.0
    var yScale: Double = 1.0
    var showGrid: Boolean = true

    // ── Equations ─────────────────────────────────────────────────────────────

    data class EquationDef(val expression: String, val color: Int, var enabled: Boolean = true)

    private val equations = mutableListOf<EquationDef>()
    private val engine    = CfxEngine()

    fun setEquations(eqs: List<EquationDef>) {
        equations.clear()
        equations.addAll(eqs)
        plotData.clear()
        for (eq in equations) {
            if (eq.enabled && eq.expression.isNotBlank()) {
                plotData.add(computePlot(eq.expression))
            } else {
                plotData.add(emptyList())
            }
        }
        invalidate()
    }

    // ── Plot cache ────────────────────────────────────────────────────────────

    private val plotData = mutableListOf<List<Pair<Double, Double>>>()

    private fun computePlot(exprStr: String): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        val steps  = 400
        val dx     = (xMax - xMin) / steps
        var x      = xMin
        while (x <= xMax + dx * 0.5) {
            engine.variables["X"] = x
            engine.variables["x"] = x
            try {
                val y = engine.evaluate(exprStr)
                if (y.isFinite()) points.add(Pair(x, y))
                else              points.add(Pair(x, Double.NaN)) // break line at discontinuity
            } catch (_: Exception) {
                points.add(Pair(x, Double.NaN))
            }
            x += dx
        }
        return points
    }

    // ── Trace cursor ──────────────────────────────────────────────────────────

    var traceEnabled: Boolean = false
    private var traceCursorX: Float = Float.NaN
    private var traceCursorY: Float = Float.NaN
    var onTraceUpdate: ((x: Double, y: Double) -> Unit)? = null

    // ── Paints ────────────────────────────────────────────────────────────────

    private val bgPaint    = Paint().apply { color = Color.WHITE }
    private val axisPaint  = Paint().apply {
        color = Color.BLACK; strokeWidth = 2f; isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private val gridPaint  = Paint().apply {
        color = Color.parseColor("#CCCCCC"); strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val textPaint  = Paint().apply {
        color = Color.BLACK; textSize = 24f; isAntiAlias = true; typeface = Typeface.MONOSPACE
    }
    private val curvePaints = listOf(
        Paint().apply { color = Color.BLACK;   strokeWidth = 2f; isAntiAlias = true; style = Paint.Style.STROKE },
        Paint().apply { color = Color.parseColor("#555555"); strokeWidth = 2f; isAntiAlias = true; style = Paint.Style.STROKE },
        Paint().apply { color = Color.parseColor("#888888"); strokeWidth = 2f; isAntiAlias = true; style = Paint.Style.STROKE }
    )
    private val cursorPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 2f; isAntiAlias = true; style = Paint.Style.STROKE
    }

    // ── onDraw ────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        drawGrid(canvas, w, h)
        drawAxes(canvas, w, h)
        drawCurves(canvas, w, h)
        if (traceEnabled && !traceCursorX.isNaN()) drawTraceCursor(canvas, w, h)
    }

    // ── Grid ──────────────────────────────────────────────────────────────────

    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        if (!showGrid) return
        // Vertical grid lines
        var x = Math.ceil(xMin / xScale) * xScale
        while (x <= xMax) {
            val sx = toScreenX(x, w)
            canvas.drawLine(sx, 0f, sx, h, gridPaint)
            x += xScale
        }
        // Horizontal grid lines
        var y = Math.ceil(yMin / yScale) * yScale
        while (y <= yMax) {
            val sy = toScreenY(y, h)
            canvas.drawLine(0f, sy, w, sy, gridPaint)
            y += yScale
        }
    }

    // ── Axes ──────────────────────────────────────────────────────────────────

    private fun drawAxes(canvas: Canvas, w: Float, h: Float) {
        // X-axis
        val yAxis = toScreenY(0.0, h).coerceIn(0f, h)
        canvas.drawLine(0f, yAxis, w, yAxis, axisPaint)
        // Y-axis
        val xAxis = toScreenX(0.0, w).coerceIn(0f, w)
        canvas.drawLine(xAxis, 0f, xAxis, h, axisPaint)

        // Tick marks + labels on X-axis
        var x = Math.ceil(xMin / xScale) * xScale
        while (x <= xMax) {
            if (Math.abs(x) > 1e-10) {
                val sx = toScreenX(x, w)
                canvas.drawLine(sx, yAxis - 5f, sx, yAxis + 5f, axisPaint)
                val label = formatAxisLabel(x)
                canvas.drawText(label, sx - textPaint.measureText(label) / 2f, yAxis + 20f, textPaint)
            }
            x += xScale
        }
        // Tick marks + labels on Y-axis
        var y = Math.ceil(yMin / yScale) * yScale
        while (y <= yMax) {
            if (Math.abs(y) > 1e-10) {
                val sy = toScreenY(y, h)
                canvas.drawLine(xAxis - 5f, sy, xAxis + 5f, sy, axisPaint)
                val label = formatAxisLabel(y)
                canvas.drawText(label, xAxis + 8f, sy + textPaint.textSize / 3f, textPaint)
            }
            y += yScale
        }

        // "O" at origin if visible
        if (xMin < 0 && xMax > 0 && yMin < 0 && yMax > 0) {
            canvas.drawText("O", xAxis + 3f, yAxis - 3f, textPaint)
        }

        // Axis arrows
        canvas.drawLine(w - 10f, yAxis - 5f, w, yAxis, axisPaint)
        canvas.drawLine(w - 10f, yAxis + 5f, w, yAxis, axisPaint)
        canvas.drawLine(xAxis - 5f, 10f, xAxis, 0f, axisPaint)
        canvas.drawLine(xAxis + 5f, 10f, xAxis, 0f, axisPaint)
    }

    // ── Curves ────────────────────────────────────────────────────────────────

    private fun drawCurves(canvas: Canvas, w: Float, h: Float) {
        plotData.forEachIndexed { idx, points ->
            if (points.isEmpty()) return@forEachIndexed
            val paint = curvePaints.getOrElse(idx) { curvePaints[0] }
            val path  = Path()
            var penUp = true
            for ((px, py) in points) {
                if (py.isNaN()) { penUp = true; continue }
                val sx = toScreenX(px, w)
                val sy = toScreenY(py, h)
                if (penUp) { path.moveTo(sx, sy); penUp = false }
                else        path.lineTo(sx, sy)
            }
            canvas.drawPath(path, paint)
        }
    }

    // ── Trace cursor ──────────────────────────────────────────────────────────

    private fun drawTraceCursor(canvas: Canvas, w: Float, h: Float) {
        canvas.drawLine(traceCursorX, 0f, traceCursorX, h, cursorPaint)
        canvas.drawLine(0f, traceCursorY, w, traceCursorY, cursorPaint)
        canvas.drawCircle(traceCursorX, traceCursorY, 6f, cursorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!traceEnabled) return false
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            traceCursorX = event.x
            val worldX    = toWorldX(event.x)
            // Find closest Y on first enabled curve
            val worldY    = evalY1(worldX)
            traceCursorY  = if (!worldY.isNaN()) toScreenY(worldY, height.toFloat()) else event.y
            onTraceUpdate?.invoke(worldX, worldY)
            invalidate()
            return true
        }
        return false
    }

    private fun evalY1(x: Double): Double {
        if (equations.isEmpty()) return Double.NaN
        engine.variables["X"] = x
        engine.variables["x"] = x
        return try {
            val v = engine.evaluate(equations[0].expression)
            if (v.isFinite()) v else Double.NaN
        } catch (_: Exception) { Double.NaN }
    }

    // ── Coordinate helpers ────────────────────────────────────────────────────

    private fun toScreenX(wx: Double, w: Float): Float =
        ((wx - xMin) / (xMax - xMin) * w).toFloat()

    private fun toScreenY(wy: Double, h: Float): Float =
        ((yMax - wy) / (yMax - yMin) * h).toFloat()

    private fun toWorldX(sx: Float): Double =
        xMin + sx.toDouble() / width * (xMax - xMin)

    private fun formatAxisLabel(v: Double): String {
        if (v == Math.floor(v) && Math.abs(v) < 1000) return v.toInt().toString()
        return "%.1f".format(v)
    }
}
