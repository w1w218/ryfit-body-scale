package com.wcgarden.bodyscale

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.wcgarden.bodyscale.ble.ScaleProtocol
import com.wcgarden.bodyscale.data.MeasurementDb
import com.wcgarden.bodyscale.data.UserPrefs
import com.wcgarden.bodyscale.view.MetricListHelper
import java.util.Locale

/**
 * 单次测量记录详情页 —— 复用主页的身体指标展示（IndicatorBarView 列表）。
 * 从历史列表点击进入，显示该次记录的全部身体数据。
 */
class RecordDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_detail)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)
        val db = MeasurementDb(this)
        val record = db.queryAll().firstOrNull { it.id == recordId }
        val prefs = UserPrefs(this)

        val tvTitle = findViewById<TextView>(R.id.tvDetailTitle)
        val tvWeight = findViewById<TextView>(R.id.tvDetailWeight)
        val metricList = findViewById<LinearLayout>(R.id.metricList)

        if (record == null) {
            tvTitle.text = "记录不存在"
            finish()
            return
        }

        tvTitle.text = record.checkdate
        tvWeight.text = String.format(Locale.US, "%.1f kg", record.weight)

        // 记录 → 协议测量模型（字段与 db 一致）
        val m = ScaleProtocol.Measurement(
            timestamp = 0,
            checkdate = record.checkdate,
            weight = record.weight,
            bmi = record.bmi,
            fat = record.fat,
            water = record.water,
            muscle = record.muscle,
            bone = record.bone,
            metabolism = record.metabolism,
            skinfat = record.skinfat,
            offalfat = record.offalfat,
            bodyage = record.bodyage,
            ryfit = record.ryfit
        )
        val rows = MetricListHelper.build(this, metricList, prefs)
        MetricListHelper.bind(rows, m, prefs)
    }

    companion object {
        const val EXTRA_RECORD_ID = "record_id"
    }
}
