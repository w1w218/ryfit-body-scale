package com.wcgarden.bodyscale.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.wcgarden.bodyscale.data.ChartAggregator
import com.wcgarden.bodyscale.data.ChartColors
import java.util.Locale

/**
 * 历史图表 —— 柱状 + 折线叠加（对应原 App BarView + LineView）。
 *  - Y 轴自适应数据区间，让波动可见（不强制从 0 起）
 *  - 柱按健康分级着色；无数据不画；未来浅灰
 *  - 折线连接实测点（趋势线）
 */
class ScaleChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF69B5E9.toInt()
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEDF1F5.toInt()
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        color = 0xFF9E9E9E.toInt()
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        color = 0xFF616161.toInt()
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }
    private val targetTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        color = 0xFFFF9800.toInt()
    }

    private var points: List<ChartAggregator.ChartPoint> = emptyList()
    private var type: Int = 1
    private var actualAge: Int = 30
    private var targetValue: Double? = null

    fun setData(points: List<ChartAggregator.ChartPoint>, type: Int, age: Int, target: Double? = null) {
        this.points = points
        this.type = type
        this.actualAge = age
        this.targetValue = target
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(210))
    }

    override fun onDraw(canvas: Canvas) {
        if (points.isEmpty()) return

        val axisW = dp(38).toFloat()
        val topPad = dp(18).toFloat()
        val bottomAxis = height - dp(26).toFloat()
        val chartLeft = axisW
        val chartRight = width - dp(10).toFloat()
        val chartWidth = chartRight - chartLeft
        val chartHeight = bottomAxis - topPad

        // ---- 数据范围（自适应，让波动可见） ----
        val values = points.filter { !it.future && (it.value ?: 0.0) > 0 }.map { it.value!! }
        if (values.isEmpty()) {
            textPaint.textSize = sp(13f)
            textPaint.color = 0xFFB0BEC5.toInt()
            val msg = "暂无测量数据"
            val tw = textPaint.measureText(msg)
            canvas.drawText(msg, (width - tw) / 2f, height / 2f + dp(5), textPaint)
            return
        }
        val minV = values.min()
        val maxV = values.max()
        var span = maxV - minV
        if (span < maxV * 0.03) span = maxV * 0.03 // 波动很小时给最小可视跨度
        val yMin = (minV - span * 0.6).coerceAtLeast(0.0)
        val yMax = maxV + span * 0.6
        val range = (yMax - yMin).coerceAtLeast(1e-6)

        // Y 轴刻度（3 条）+ 网格
        for (i in 0..2) {
            val y = bottomAxis - chartHeight * i / 2
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            val v = yMin + range * i / 2
            val label = fmt(v)
            val tw = textPaint.measureText(label)
            canvas.drawText(label, chartLeft - dp(6) - tw, y + dp(3), textPaint)
        }
        gridPaint.color = 0xFFDCE3EA.toInt()
        canvas.drawLine(chartLeft, bottomAxis, chartRight, bottomAxis, gridPaint)
        gridPaint.color = 0xFFEDF1F5.toInt()

        val count = points.size
        val slot = chartWidth / count
        val barWidth = (slot * 0.55f).coerceAtMost(dp(24).toFloat())
        val showValue = slot >= dp(30).toFloat()
        // X 轴标签间隔（拥挤时抽稀，保证间距 ≥ 36dp）
        val labelStep = Math.ceil(dp(36).toDouble() / slot).toInt().coerceAtLeast(1)

        fun valueY(v: Double): Float =
            (bottomAxis - chartHeight * ((v - yMin) / range)).toFloat()

        // ---- 目标参考线（在可见范围内时画虚线 + 标注） ----
        targetValue?.let { t ->
            if (t > 0 && t in yMin..yMax) {
                val ty = valueY(t)
                canvas.drawLine(chartLeft, ty, chartRight, ty, targetPaint)
                val label = "目标 " + fmt(t)
                canvas.drawText(label, chartRight - targetTextPaint.measureText(label), ty - dp(4), targetTextPaint)
            }
        }

        // ---- 柱 ----
        for (i in 0 until count) {
            val p = points[i]
            val centerX = chartLeft + slot * i + slot / 2
            val v = p.value
            if (p.future || v == null || v <= 0.0) {
                if (i % labelStep == 0 || i == count - 1) {
                    drawXLabel(canvas, centerX, p.label, if (p.future) 0xFFCFD8DC.toInt() else 0xFFB0BEC5.toInt(), bottomAxis)
                }
                continue
            }
            val y = valueY(v)
            barPaint.color = ChartColors.getColorValue(type, v, actualAge)
            val rect = RectF(centerX - barWidth / 2, y, centerX + barWidth / 2, bottomAxis)
            canvas.drawRoundRect(rect, dp(4).toFloat(), dp(4).toFloat(), barPaint)
            if (showValue) {
                val label = fmt(v)
                val tw = valuePaint.measureText(label)
                canvas.drawText(label, centerX - tw / 2, y - dp(4), valuePaint)
            }
            if (i % labelStep == 0 || i == count - 1) {
                drawXLabel(canvas, centerX, p.label, 0xFF9E9E9E.toInt(), bottomAxis)
            }
        }

        // ---- 折线（趋势线；无数据点跳过但要保持连续，用直线跨过缺口） ----
        val path = Path()
        var started = false
        for (i in 0 until count) {
            val p = points[i]
            val v = p.value
            if (p.future || v == null || v <= 0.0) continue
            val centerX = chartLeft + slot * i + slot / 2
            val y = valueY(v)
            if (!started) {
                path.moveTo(centerX, y)
                started = true
            } else {
                path.lineTo(centerX, y)
            }
            // 数据点小圆（折线节点）
            dotPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(centerX, y, dp(3).toFloat(), dotPaint)
            dotPaint.color = linePaint.color
            canvas.drawCircle(centerX, y, dp(2).toFloat(), dotPaint)
        }
        canvas.drawPath(path, linePaint)
    }

    private fun drawXLabel(canvas: Canvas, centerX: Float, label: String, color: Int, bottomAxis: Float) {
        textPaint.color = color
        val tw = textPaint.measureText(label)
        canvas.drawText(label, centerX - tw / 2, bottomAxis + dp(14), textPaint)
    }

    private fun fmt(v: Double): String =
        if (Math.abs(v - Math.round(v)) < 0.05) Math.round(v).toString()
        else String.format(Locale.US, "%.1f", v)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
