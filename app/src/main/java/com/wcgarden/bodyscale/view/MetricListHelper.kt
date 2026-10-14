package com.wcgarden.bodyscale.view

import android.content.Context
import android.widget.LinearLayout
import com.wcgarden.bodyscale.ble.ScaleProtocol
import com.wcgarden.bodyscale.data.DataJudge
import com.wcgarden.bodyscale.data.UserPrefs
import java.util.Locale

/**
 * 主页身体指标列表构建器 —— 原 App MainInAdapter 的复刻，供主页与记录详情页复用。
 *
 * 每行一个 IndicatorBarView：指标名 + 当前值（按状态着色）+ 分段色带 + 水滴定位 + 分级标签。
 * 判定逻辑与阈值全部来自逆向的 DataJudge。
 */
object MetricListHelper {

    /** 在容器中构建 10 行指标（每行白卡），返回构建好的 View 列表 */
    fun build(context: Context, container: LinearLayout, prefs: UserPrefs): List<IndicatorBarView> {
        container.removeAllViews()
        val rows = mutableListOf<IndicatorBarView>()
        repeat(10) {
            val bar = IndicatorBarView(context)
            bar.background = androidx.core.content.ContextCompat.getDrawable(context, com.wcgarden.bodyscale.R.drawable.bg_card)
            container.addView(bar, lp(context))
            rows += bar
        }
        bind(rows, null, prefs)
        return rows
    }

    private fun lp(context: Context): LinearLayout.LayoutParams {
        val m = (10 * context.resources.displayMetrics.density).toInt()
        val mb = (8 * context.resources.displayMetrics.density).toInt()
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = m
            marginEnd = m
            bottomMargin = mb
        }
    }

    /** 填充指标数据；m 为 null 时显示空态（--） */
    fun bind(rows: List<IndicatorBarView>, m: ScaleProtocol.Measurement?, prefs: UserPrefs) {
        val sex = prefs.sex
        val age = prefs.age
        val height = prefs.height
        if (m == null) {
            for (r in rows) r.setEmpty("", null)
            return
        }

        // 0 身体年龄
        rows[0].setData("身体年龄", m.bodyage.toDouble(),
            DataJudge.getBodyAgeColorByDataJuge(m.bodyage.toDouble(), age).color, null,
            DoubleArray(0), emptyArray(), poorText = "实际年龄 $age")

        // 1 内脏脂肪
        rows[1].setData("内脏脂肪", m.offalfat.toDouble(),
            DataJudge.getOffalaColorByDataJudge(m.offalfat.toDouble(), 9.0, 15.0).color, null,
            DataJudge.offalaThresholds(), arrayOf("标准", "偏高", "过高"),
            segmentColors = intArrayOf(DataJudge.COLOR_GREEN, DataJudge.COLOR_YELLOW, DataJudge.COLOR_ORANGE))

        // 2 骨量（% = kg/体重×100，阈值相对体重）
        val bonePct = if (m.weight > 0) m.bone / m.weight * 100.0 else 0.0
        rows[2].setData("骨量", bonePct,
            DataJudge.getBoneColorByDataJuge(sex, m.weight, bonePct).color, "%",
            DataJudge.boneThresholds(sex, m.weight), arrayOf("偏低", "标准", "偏高"))

        // 3 体重：阈值由 BMI 18.5/24/28 反推
        val w1 = DataJudge.getWeightByBmi(18.5, height)
        val w2 = DataJudge.getWeightByBmi(24.0, height)
        val w3 = DataJudge.getWeightByBmi(28.0, height)
        val diff = m.weight - prefs.targetWeight
        val poor = if (Math.abs(diff) < 0.05) "目标达成"
        else String.format(Locale.US, if (diff > 0) "高于目标 %.1fkg" else "低于目标 %.1fkg", Math.abs(diff))
        rows[3].setData("体重", m.weight,
            DataJudge.getColorByDataJudge(m.weight, w1, w2, w3).color, "kg",
            DataJudge.weightThresholds(height), arrayOf("偏瘦", "标准", "微胖", "肥胖"), poorText = poor)

        // 4 BMI
        rows[4].setData("BMI", m.bmi,
            DataJudge.getColorByDataJudge(m.bmi, 18.5, 24.0, 28.0).color, null,
            DataJudge.bmiThresholds(), arrayOf("偏瘦", "标准", "微胖", "肥胖"))

        // 5 体脂率
        rows[5].setData("体脂率", m.fat,
            DataJudge.getFatColorByDataJuge(sex, age, m.fat).color, "%",
            DataJudge.fatThresholds(sex, age), arrayOf("偏瘦", "标准", "微胖", "肥胖"))

        // 6 皮下脂肪
        rows[6].setData("皮下脂肪", m.skinfat,
            DataJudge.getSkinFatColorByDataJuge(sex, m.skinfat).color, "%",
            DataJudge.skinfatThresholds(sex), arrayOf("偏低", "标准", "偏高"))

        // 7 肌肉比例（% = kg/体重×100）
        val musclePct = if (m.weight > 0) m.muscle / m.weight * 100.0 else 0.0
        rows[7].setData("肌肉比例", musclePct,
            DataJudge.getMuscleColorByDataJuge(sex, height.toDouble(), musclePct).color, "%",
            DataJudge.muscleThresholds(sex, height), arrayOf("偏低", "标准", "偏高"))

        // 8 基础代谢率(亚)
        val metabO = ScaleProtocol.getEuropeanStandard(m.fat, m.weight).toDouble()
        rows[8].setData("基础代谢率(亚)", metabO,
            DataJudge.getMetabolismOColorByDataJuge(sex, age, m.weight, metabO).color, "kcal",
            doubleArrayOf(DataJudge.metabolismOThreshold(sex, age, m.weight)),
            arrayOf("未达标", "达标"),
            segmentColors = intArrayOf(DataJudge.COLOR_BLUE, DataJudge.COLOR_GREEN))

        // 9 基础代谢率
        val metabY = m.metabolism.toDouble()
        rows[9].setData("基础代谢率", metabY,
            DataJudge.getMetabolismYColorByDataJuge(sex, age, metabY).color, "kcal",
            doubleArrayOf(DataJudge.metabolismYThreshold(sex, age).toDouble()),
            arrayOf("未达标", "达标"),
            segmentColors = intArrayOf(DataJudge.COLOR_BLUE, DataJudge.COLOR_GREEN))
    }
}
