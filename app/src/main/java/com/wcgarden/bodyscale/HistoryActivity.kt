package com.wcgarden.bodyscale

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wcgarden.bodyscale.data.ChartAggregator
import com.wcgarden.bodyscale.data.MeasurementDb
import com.wcgarden.bodyscale.data.MeasurementRecord
import com.wcgarden.bodyscale.data.UserPrefs
import com.wcgarden.bodyscale.view.ScaleChartView
import java.util.Calendar
import java.util.Locale

/**
 * 历史页：维度栏（11 维）+ 日/周/月/年切换 + 柱状图 + 记录列表。
 * 图表数据由 ChartAggregator 聚合（复刻原 App getWeekData 等规则）。
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var db: MeasurementDb
    private lateinit var prefs: UserPrefs
    private lateinit var chartView: ScaleChartView
    private lateinit var dimensionBar: GridLayout
    private lateinit var tvRange: TextView

    private var records: List<MeasurementRecord> = emptyList()
    private var dimType: Int = 1           // 当前维度（默认体重）
    private var period: ChartAggregator.Period = ChartAggregator.Period.WEEK
    private var offset: Int = 0            // 翻页（往前推几周/月/年）
    private var selectedDimView: TextView? = null
    private val periodViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        db = MeasurementDb(this)
        prefs = UserPrefs(this)
        chartView = findViewById(R.id.chartView)
        dimensionBar = findViewById(R.id.dimensionBar)
        tvRange = findViewById(R.id.tvRange)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnPrev).setOnClickListener { offset++; refresh() }
        findViewById<TextView>(R.id.btnNext).setOnClickListener { if (offset > 0) { offset--; refresh() } }

        buildDimensionBar()
        buildPeriodTabs()
    }

    override fun onResume() {
        super.onResume()
        // 回到本页（含从详情页返回、设置页清空数据后）重新取数据
        reload()
    }

    private fun reload() {
        records = db.queryAll()
        renderList()
        refresh()
    }

    // ------------------------------------------------------------------
    // 维度栏（原 App initData 的 11 维顺序）
    // ------------------------------------------------------------------

    private fun buildDimensionBar() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        for (dim in ChartAggregator.DIMENSIONS) {
            val tv = TextView(this)
            tv.text = dim.name
            tv.gravity = android.view.Gravity.CENTER
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            tv.textSize = 13.5f
            tv.background = ContextCompat.getDrawable(this, R.drawable.bg_chip)
            tv.isClickable = true
            tv.isFocusable = true
            val lp = GridLayout.LayoutParams()
            lp.width = 0
            lp.height = dp(40)
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            tv.layoutParams = lp
            tv.setOnClickListener {
                dimType = dim.type
                selectDimensionView(tv)
                refresh()
            }
            dimensionBar.addView(tv)
        }
        // 默认选中体重（index=1）
        dimType = ChartAggregator.DIMENSIONS[1].type
        selectDimensionView(dimensionBar.getChildAt(1) as TextView)
    }

    private fun selectDimensionView(tv: TextView) {
        selectedDimView?.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        selectedDimView?.paint?.isFakeBoldText = false
        selectedDimView?.background = ContextCompat.getDrawable(this, R.drawable.bg_chip)
        selectedDimView = tv
        tv.setTextColor(ContextCompat.getColor(this, R.color.color_primary))
        tv.paint.isFakeBoldText = true
        tv.background = ContextCompat.getDrawable(this, R.drawable.bg_chip_active)
    }

    private fun buildPeriodTabs() {
        val defs = listOf(
            ChartAggregator.Period.DAY to R.id.tvDay,
            ChartAggregator.Period.WEEK to R.id.tvWeek,
            ChartAggregator.Period.MONTH to R.id.tvMonth,
            ChartAggregator.Period.YEAR to R.id.tvYear
        )
        for ((p, id) in defs) {
            val tv = findViewById<TextView>(id)
            tv.setOnClickListener {
                period = p
                offset = 0
                updatePeriodTabs()
                refresh()
            }
            periodViews += tv
        }
        updatePeriodTabs()
    }

    private fun updatePeriodTabs() {
        val active = when (period) {
            ChartAggregator.Period.DAY -> R.id.tvDay
            ChartAggregator.Period.WEEK -> R.id.tvWeek
            ChartAggregator.Period.MONTH -> R.id.tvMonth
            ChartAggregator.Period.YEAR -> R.id.tvYear
        }
        for (tv in periodViews) {
            val isActive = tv.id == active
            tv.setTextColor(ContextCompat.getColor(this, if (isActive) R.color.color_primary else R.color.text_secondary))
            tv.textSize = if (isActive) 17f else 15f
            tv.paint.isFakeBoldText = isActive
            tv.background = if (isActive) ContextCompat.getDrawable(this, R.drawable.bg_tab_indicator) else null
        }
    }

    // ------------------------------------------------------------------
    // 图表刷新
    // ------------------------------------------------------------------

    private fun refresh() {
        val points = ChartAggregator.aggregate(
            records, dimType, period, offset,
            prefs.sex, prefs.age, prefs.height
        )
        chartView.setData(points, dimType, prefs.age, if (dimType == 1) prefs.targetWeight else null)
        tvRange.text = rangeText()
        findViewById<TextView>(R.id.tvChange).text = ChartAggregator.changeOf(points, dimType)
    }

    private fun rangeText(): String {
        val cal = Calendar.getInstance()
        return when (period) {
            ChartAggregator.Period.DAY -> {
                // 当天 24 小时
                cal.add(Calendar.DAY_OF_YEAR, -offset)
                String.format(Locale.US, "%04d-%02d-%02d",
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            }
            ChartAggregator.Period.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.add(Calendar.DAY_OF_YEAR, -offset * 7)
                val start = cal.clone() as Calendar
                start.add(Calendar.DAY_OF_YEAR, 6)
                String.format(Locale.US, "%02d-%02d ~ %02d-%02d",
                    cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                    start.get(Calendar.MONTH) + 1, start.get(Calendar.DAY_OF_MONTH))
            }
            ChartAggregator.Period.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, -offset)
                String.format(Locale.US, "%04d 年 %02d 月", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            }
            ChartAggregator.Period.YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.add(Calendar.YEAR, -offset)
                cal.get(Calendar.YEAR).toString() + " 年"
            }
        }
    }

    // ------------------------------------------------------------------
    // 记录列表（点击进详情）
    // ------------------------------------------------------------------

    private fun renderList() {
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        val recycler = findViewById<RecyclerView>(R.id.recyclerHistory)
        val empty = records.isEmpty()
        tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        recycler.visibility = if (empty) View.GONE else View.VISIBLE
        if (recycler.layoutManager == null) {
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = HistoryAdapter(records) { openDetail(it) }
        } else {
            // 复用已有 adapter，避免重建时丢失滚动位置
            (recycler.adapter as HistoryAdapter).submit(records)
        }
    }

    private fun openDetail(record: MeasurementRecord) {
        val intent = Intent(this, RecordDetailActivity::class.java)
        intent.putExtra(RecordDetailActivity.EXTRA_RECORD_ID, record.id)
        startActivity(intent)
    }

    class HistoryAdapter(
        private var items: List<MeasurementRecord>,
        private val onClick: (MeasurementRecord) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

        fun submit(newItems: List<MeasurementRecord>) {
            items = newItems
            notifyDataSetChanged()
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDate: TextView = view.findViewById(R.id.tvDate)
            val tvWeight: TextView = view.findViewById(R.id.tvWeight)
            val tvSummary: TextView = view.findViewById(R.id.tvSummary)
            val tvDelta: TextView = view.findViewById(R.id.tvDelta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history, parent, false)
            return Holder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val record = items[position]
            holder.tvDate.text = record.checkdate
            holder.tvWeight.text = String.format(Locale.US, "%.1f kg", record.weight)
            // 与上一条（更早一次）对比，放在体重左侧，直观看出变化
            val prev = items.getOrNull(position + 1)
            holder.tvDelta.text = if (prev != null) {
                val d = record.weight - prev.weight
                when {
                    Math.abs(d) < 0.05 -> "较上次持平"
                    d > 0 -> String.format(Locale.US, "较上次 ↑%.1f", d)
                    else -> String.format(Locale.US, "较上次 ↓%.1f", -d)
                }
            } else ""
            holder.tvSummary.text = String.format(
                Locale.US,
                "BMI %.1f  体脂 %.1f%%  水分 %.1f%%  肌肉 %.1fkg  内脏 %d",
                record.bmi, record.fat, record.water, record.muscle, record.offalfat
            )
            holder.itemView.setOnClickListener { onClick(record) }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
