package com.supernote.ganttproject

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.supernote.ganttproject.models.Task

/**
 * Custom view that renders a Gantt chart for a list of tasks.
 * Optimised for e-ink: no gradients, pure black/white/grey palette.
 */
class GanttChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var tasks: List<Task> = emptyList()
        set(value) {
            field = value
            recalcDimensions()
            requestLayout()
            invalidate()
        }

    private val rowHeight = 44f.dp
    private val headerHeight = 32f.dp
    private val labelWidth = 120f.dp
    private val dayWidth = 24f.dp
    private val barPadV = 8f.dp
    private val textBounds = Rect()

    private val paintHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.header_bg)
    }
    private val paintHeaderText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.header_text)
        textSize = 11f.dp
    }
    private val paintLabelBg = Paint().apply {
        color = ContextCompat.getColor(context, R.color.row_label_bg)
    }
    private val paintAltRowBg = Paint().apply {
        color = ContextCompat.getColor(context, R.color.row_alt_bg)
    }
    private val paintGrid = Paint().apply {
        color = ContextCompat.getColor(context, R.color.grid_line)
        strokeWidth = 1f
    }
    private val paintBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.bar_default)
    }
    private val paintTodayLine = Paint().apply {
        color = ContextCompat.getColor(context, R.color.today_line)
        strokeWidth = 2f
    }
    private val paintLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.black)
        textSize = 13f.dp
    }
    private val paintRowBorder = Paint().apply {
        color = ContextCompat.getColor(context, R.color.grid_line)
        strokeWidth = 1f
    }
    private val paintColumnTick = Paint().apply {
        color = ContextCompat.getColor(context, R.color.header_column_tick)
        strokeWidth = 1f
    }

    private val Float.dp get() = this * context.resources.displayMetrics.density

    private var totalDays = 30

    private fun recalcDimensions() {
        totalDays = if (tasks.isEmpty()) 30 else {
            tasks.maxOf { it.startDay + it.durationDays - 1 + 5 }.coerceAtLeast(30)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (labelWidth + totalDays * dayWidth).toInt()
        val h = (headerHeight + tasks.size * rowHeight).toInt().coerceAtLeast(80)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawHeader(canvas)
        drawRows(canvas)
        drawTodayLine(canvas)
    }

    private fun drawHeader(canvas: Canvas) {
        // label column background
        canvas.drawRect(0f, 0f, labelWidth, headerHeight, paintHeader)
        // draw day numbers
        canvas.drawRect(labelWidth, 0f, width.toFloat(), headerHeight, paintHeader)
        for (day in 1..totalDays) {
            val x = labelWidth + (day - 1) * dayWidth
            // vertical tick every 7 days
            if (day % 7 == 1) {
                canvas.drawLine(x, 0f, x, headerHeight, paintColumnTick)
                val label = "D$day"
                paintHeaderText.getTextBounds(label, 0, label.length, textBounds)
                canvas.drawText(label, x + 2f, headerHeight - 6f.dp, paintHeaderText)
            }
        }
    }

    private fun drawRows(canvas: Canvas) {
        tasks.forEachIndexed { i, task ->
            val top = headerHeight + i * rowHeight
            val bottom = top + rowHeight

            // alternating row background
            if (i % 2 == 1) canvas.drawRect(0f, top, width.toFloat(), bottom, paintAltRowBg)

            // row border
            canvas.drawLine(0f, bottom, width.toFloat(), bottom, paintRowBorder)

            // label column bg
            canvas.drawRect(0f, top, labelWidth, bottom, paintLabelBg)

            // task label
            val labelText = task.name.take(16)
            paintLabel.getTextBounds(labelText, 0, labelText.length, textBounds)
            val textY = top + (rowHeight + textBounds.height()) / 2f
            canvas.drawText(labelText, 4f.dp, textY, paintLabel)

            // vertical separator
            canvas.drawLine(labelWidth, top, labelWidth, bottom, paintRowBorder)

            // bar
            val barLeft = labelWidth + (task.startDay - 1) * dayWidth
            val barRight = barLeft + task.durationDays * dayWidth
            canvas.drawRect(barLeft, top + barPadV, barRight, bottom - barPadV, paintBar)
        }
    }

    private fun drawTodayLine(canvas: Canvas) {
        // today = day 1 marker (leftmost, can be scrolled)
        val x = labelWidth
        canvas.drawLine(x, 0f, x, height.toFloat(), paintTodayLine)
    }
}
