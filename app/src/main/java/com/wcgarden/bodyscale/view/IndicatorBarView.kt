package com.wcgarden.bodyscale.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.wcgarden.bodyscale.data.BarGeometry
import com.wcgarden.bodyscale.data.DataJudge
import java.util.Locale

/**
 * 指标行视图 —— 对齐原 App 主页身体指标列表（main_body_item*）的两列布局：
 *
 *   [ 66      ]   (水滴)  74
 *   [ 身体年龄 ]   ────绿──橙────          ← 色带：阈值数字在带上沿
 *                 标准  偏高   过高          ← 分级文字在带下沿
 *
 * 左列：当前值（大字，按判定状态着色）+ 指标名；右区：分段色带 + 水滴定位 + 阈值/分级文字。
 */
class IndicatorBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var label: String = ""
    private var valueText: String = "--"
    private var valueColor: Int = DataJudge.COLOR_GRAY
    private var unit: String? = null
    private var thresholds: DoubleArray = DoubleArray(0)
    private var labels: Array<String> = emptyArray()
    private var segmentColors: IntArray = intArrayOf()
    private var currentValue: Double = 0.0
    private var hasValue: Boolean = false
    private var noteText: String? = null // 如"实际年龄 30"（身体年龄行）

    init {
        namePaint.textSize = sp(13.5f)
        namePaint.color = 0xFF9E9E9E.toInt()
        valuePaint.textSize = sp(22f)
        valuePaint.isFakeBoldText = true
        tickPaint.textSize = sp(10.5f)
        tickPaint.color = 0xFF9E9E9E.toInt()
        levelPaint.textSize = sp(12f)
        levelPaint.color = 0xFF757575.toInt()
    }

    fun setData(
        label: String,
        value: Double,
        valueColor: Int,
        unit: String?,
        thresholds: DoubleArray,
        labels: Array<String>,
        segmentColors: IntArray? = null,
        poorText: String? = null
    ) {
        this.label = label
        this.currentValue = value
        this.hasValue = true
        this.valueColor = valueColor
        this.unit = unit
        this.thresholds = thresholds
        this.labels = labels
        this.segmentColors = segmentColors ?: defaultColors(thresholds.size + 1)
        this.noteText = poorText
        valueText = if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.US, "%.1f", value)
        invalidate()
    }

    /** 空态：显示指标名 + 灰色空带 + "--" */
    fun setEmpty(label: String, unit: String?) {
        this.label = label
        this.unit = unit
        this.hasValue = false
        this.valueText = "--"
        this.valueColor = DataJudge.COLOR_GRAY
        this.thresholds = DoubleArray(0)
        this.labels = emptyArray()
        this.segmentColors = intArrayOf()
        this.noteText = null
        invalidate()
    }

    private fun defaultColors(segments: Int): IntArray = when (segments) {
        4 -> intArrayOf(DataJudge.COLOR_BLUE, DataJudge.COLOR_GREEN, DataJudge.COLOR_YELLOW, DataJudge.COLOR_ORANGE)
        3 -> intArrayOf(DataJudge.COLOR_BLUE, DataJudge.COLOR_GREEN, DataJudge.COLOR_YELLOW)
        2 -> intArrayOf(DataJudge.COLOR_BLUE, DataJudge.COLOR_GREEN)
        else -> intArrayOf(DataJudge.COLOR_GRAY)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(70))
    }

    override fun onDraw(canvas: Canvas) {
        val leftW = dp(100).toFloat() // 左列宽
        val padLeft = dp(12).toFloat()
        val bandLeft = leftW
        val bandRight = width - dp(12).toFloat()
        val bandWidth = bandRight - bandLeft
        val bandTop = dp(26).toFloat()
        val bandHeight = dp(14).toFloat()

        // ---- 左列：值（上）+ 名（下） ----
        val valueY = dp(28).toFloat()
        canvas.drawText(valueText, padLeft, valueY, valuePaint.apply { color = valueColor })
        if (unit != null) {
            tickPaint.color = 0xFF9E9E9E.toInt()
            tickPaint.textSize = sp(11.5f)
            canvas.drawText(unit!!, padLeft + valuePaint.measureText(valueText) + dp(3), valueY, tickPaint)
            tickPaint.textSize = sp(10.5f)
        }
        canvas.drawText(label, padLeft, dp(48).toFloat(), namePaint)

        // ---- 空态：灰色空带（展示结构） ----
        if (!hasValue) {
            paint.color = 0xFFEDF1F5.toInt()
            canvas.drawRoundRect(RectF(bandLeft, bandTop, bandRight, bandTop + bandHeight), dp(7).toFloat(), dp(7).toFloat(), paint)
            if (labels.isEmpty()) {
                tickPaint.color = 0xFFCFD8DC.toInt()
                tickPaint.textSize = sp(10f)
                canvas.drawText("未测量", bandLeft + dp(6), bandTop + bandHeight + dp(15).toFloat(), tickPaint)
            }
            return
        }

        // ---- 身体年龄行：无带，右侧对比说明 ----
        if (thresholds.isEmpty()) {
            if (noteText != null) {
                namePaint.textSize = sp(14f)
                canvas.drawText(noteText!!, bandRight - namePaint.measureText(noteText!!), dp(39).toFloat(), namePaint)
                namePaint.textSize = sp(13.5f)
            }
            return
        }

        val segmentCount = thresholds.size + 1

        // 阈值数字（带上沿上方）：单位跟随维度（% 维度带 %，其余不带）
        for (i in thresholds.indices) {
            val x = bandLeft + bandWidth * (i + 1) / segmentCount
            val t = thresholds[i]
            val num = if (t % 1.0 == 0.0) t.toInt().toString()
            else String.format(Locale.US, "%.1f", t)
            val tLabel = if (unit == "%") "$num%" else num
            tickPaint.color = 0xFFBDBDBD.toInt()
            tickPaint.strokeWidth = dp(1).toFloat()
            canvas.drawLine(x, bandTop - dp(4).toFloat(), x, bandTop - dp(1).toFloat(), tickPaint)
            tickPaint.color = 0xFF9E9E9E.toInt()
            val tw = tickPaint.measureText(tLabel)
            canvas.drawText(tLabel, x - tw / 2, bandTop - dp(6).toFloat(), tickPaint)
        }

        // 分段色带（圆角）
        if (segmentColors.size == segmentCount) {
            val rect = RectF(bandLeft, bandTop, bandRight, bandTop + bandHeight)
            canvas.save()
            canvas.clipRect(rect)
            for (i in 0 until segmentCount) {
                val s0 = bandLeft + bandWidth * i / segmentCount
                val s1 = bandLeft + bandWidth * (i + 1) / segmentCount
                paint.color = segmentColors[i]
                canvas.drawRect(s0, bandTop, s1, bandTop + bandHeight, paint)
            }
            canvas.restore()
            // 分段竖线（细白）
            paint.color = 0x66FFFFFF
            paint.strokeWidth = dp(1).toFloat()
            for (i in 1 until segmentCount) {
                val x = bandLeft + bandWidth * i / segmentCount
                canvas.drawLine(x, bandTop, x, bandTop + bandHeight, paint)
            }
        }

        // 水滴定位（有值时）：复刻原 App setPao 算法 —— 每段等宽，段内按该段值域线性插值
        if (hasValue) {
            val ratio = computePinRatio(currentValue)
            val r = dp(7).toFloat()
            val pinX = (bandLeft + bandWidth * ratio).coerceIn(bandLeft + r, bandRight - r)
            drawPin(canvas, pinX, bandTop + bandHeight / 2)
        }

        // 分级文字（带下沿）
        if (labels.size == segmentCount) {
            for (i in 0 until segmentCount) {
                val center = bandLeft + bandWidth * (i + 0.5f) / segmentCount
                val tw = levelPaint.measureText(labels[i])
                canvas.drawText(labels[i], center - tw / 2, bandTop + bandHeight + dp(15).toFloat(), levelPaint)
            }
        }
        // 差值说明（如"高于目标 2.0kg"）
        if (noteText != null && labels.isEmpty()) {
            levelPaint.color = 0xFF9E9E9E.toInt()
            canvas.drawText(noteText!!, bandLeft, bandTop + bandHeight + dp(15).toFloat(), levelPaint)
        }
    }

    /**
     * 水滴在色带上的比例位置（0~1）—— 委托给纯逻辑工具 [BarGeometry]（可单测）。
     */
    private fun computePinRatio(value: Double): Float = BarGeometry.pinRatio(value, thresholds)

    /** 水滴：白色圆 + 彩色描边 + 下方小三角 */
    private fun drawPin(canvas: Canvas, x: Float, y: Float) {
        val r = dp(7).toFloat()
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x, y - r * 0.35f, r, paint)
        val tri = Path().apply {
            moveTo(x - r * 0.65f, y + r * 0.1f)
            lineTo(x + r * 0.65f, y + r * 0.1f)
            lineTo(x, y + r * 0.95f)
            close()
        }
        canvas.drawPath(tri, paint)
        paint.style = Paint.Style.STROKE
        paint.color = valueColor
        paint.strokeWidth = dp(2).toFloat()
        canvas.drawCircle(x, y - r * 0.35f, r, paint)
        paint.style = Paint.Style.FILL
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
