package com.wcgarden.bodyscale.data

import java.util.Calendar
import java.util.Locale

/**
 * 历史图表数据聚合 —— 复刻原 App QueryMeasurementDataService 的
 * getWeekData/getMonthData/getYearData 规则：
 *  - 每个时间区间取该区间内所有记录的维度算术平均
 *  - 无数据区间用参考值（getData 查表，按性别/年龄/身高）画灰色柱
 *  - 未来区间标记 future（灰色）
 *
 * 维度 type 编码沿用原 App：1=体重 2=体脂 3=内脏脂肪 4=皮下脂肪 5=水分
 * 6=BMI 7=骨量 8=基础代谢(亚,重算) 9=基础代谢 10=肌肉 11=身体年龄 99=健康评分
 */
object ChartAggregator {

    enum class Period { DAY, WEEK, MONTH, YEAR }

    data class ChartPoint(
        val label: String,
        val value: Double?,   // null = 无数据（画参考值）
        val measured: Boolean,
        val future: Boolean
    )

    // ------------------------------------------------------------------
    // 聚合
    // ------------------------------------------------------------------

    fun aggregate(
        records: List<MeasurementRecord>,
        type: Int,
        period: Period,
        offset: Int,
        sex: Int,
        age: Int,
        height: Int
    ): List<ChartPoint> {
        val cal = Calendar.getInstance()
        val today = Calendar.getInstance()
        val points = mutableListOf<ChartPoint>()

        when (period) {
            Period.DAY -> {
                // 当天按小时分组（0~23 时）
                val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
                val datePrefix = String.format(
                    Locale.US, "%04d-%02d-%02d",
                    day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)
                )
                val isToday = day.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                        day.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                for (h in 0 until 24) {
                    val hourPrefix = String.format(Locale.US, "%s %02d", datePrefix, h)
                    val inHour = records.filter { it.checkdate.startsWith(hourPrefix) }
                    val avg = if (inHour.isEmpty()) null else average(inHour, type)
                    // 未来小时且无数据才算未来；有病数据（时间戳异常）时正常展示
                    val future = isToday && h > today.get(Calendar.HOUR_OF_DAY) && inHour.isEmpty()
                    points += ChartPoint(h.toString(), avg, measured = avg != null && avg != 0.0, future = future)
                }
            }
            Period.WEEK -> {
                // 本周一（原 App：周日=0）；offset>0 表示往前翻
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.add(Calendar.DAY_OF_YEAR, -offset * 7)
                for (i in 0 until 7) {
                    val day = cal.clone() as Calendar
                    day.add(Calendar.DAY_OF_YEAR, i)
                    points += pointForDate(records, type, day, today, sex, age, height,
                        dayPrefix(day), String.format(Locale.US, "%02d-%02d", day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)))
                }
            }
            Period.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, -offset)
                val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val monthPrefix = String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
                for (i in 1..days) {
                    val day = cal.clone() as Calendar
                    day.set(Calendar.DAY_OF_MONTH, i)
                    points += pointForDate(records, type, day, today, sex, age, height,
                        monthPrefix + String.format(Locale.US, "-%02d", i), i.toString())
                }
            }
            Period.YEAR -> {
                val year = Calendar.getInstance().apply { add(Calendar.YEAR, -offset) }.get(Calendar.YEAR)
                for (i in 0 until 12) {
                    val month = Calendar.getInstance().apply {
                        clear()
                        set(year, i, 1)
                    }
                    points += pointForMonth(records, type, month, today, sex, age, height,
                        String.format(Locale.US, "%04d-%02d", year, i + 1),
                        (i + 1).toString() + "月")
                }
            }
        }
        return points
    }

    /** 单日区间聚合（周/月/日共用） */
    private fun pointForDate(
        records: List<MeasurementRecord>,
        type: Int,
        day: Calendar,
        today: Calendar,
        sex: Int, age: Int, height: Int,
        prefix: String,
        label: String
    ): ChartPoint {
        val future = day.after(today)
        val inDay = records.filter { it.checkdate.startsWith(prefix) }
        val avg = if (inDay.isEmpty()) null else average(inDay, type)
        return ChartPoint(label, avg, measured = avg != null && avg != 0.0, future = future)
    }

    /** 单月区间聚合（年图） */
    private fun pointForMonth(
        records: List<MeasurementRecord>,
        type: Int,
        month: Calendar,
        today: Calendar,
        sex: Int, age: Int, height: Int,
        prefix: String,
        label: String
    ): ChartPoint {
        // 该月最后一天
        val last = month.clone() as Calendar
        last.set(Calendar.DAY_OF_MONTH, last.getActualMaximum(Calendar.DAY_OF_MONTH))
        val future = last.after(today) && !(month.get(Calendar.YEAR) == today.get(Calendar.YEAR) && month.get(Calendar.MONTH) == today.get(Calendar.MONTH))
        val inMonth = records.filter { it.checkdate.startsWith(prefix) }
        val avg = if (inMonth.isEmpty()) null else average(inMonth, type)
        return ChartPoint(label, avg, measured = avg != null && avg != 0.0, future = future)
    }

    private fun average(records: List<MeasurementRecord>, type: Int): Double {
        val sum = records.sumOf { extract(it, type) }
        return Math.round(sum / records.size * 10.0) / 10.0
    }

    /** 维度值提取（原 getWeekData switch；type 8 基础代谢用体重+体脂重算） */
    fun extract(r: MeasurementRecord, type: Int): Double = when (type) {
        1 -> r.weight
        2 -> r.fat
        3 -> r.offalfat.toDouble()
        4 -> r.skinfat
        5 -> r.water
        6 -> r.bmi
        7 -> r.bone
        8 -> Math.round(370.0 + 21.6 * (1.0 - r.fat / 100.0) * r.weight).toDouble()  // 原 App getO：四舍五入
        9 -> r.metabolism.toDouble()
        10 -> r.muscle
        11 -> r.bodyage.toDouble()
        99 -> r.ryfit
        else -> 0.0
    }

    // ------------------------------------------------------------------
    // 参考值（原 QMDS.getData：无数据时画灰色柱，×10 整数 → /10）
    // ------------------------------------------------------------------

    fun getDefaultValue(type: Int, sex: Int, height: Int, age: Int): Double = when (type) {
        1 -> if (sex == 1) (height - 80) * 0.7 else (height - 70) * 0.6
        2 -> when {
            sex == 1 && age <= 30 -> 18.0
            sex == 1 -> 20.0
            age <= 30 -> 20.0
            else -> 24.0
        }
        3 -> 4.0
        4 -> if (sex == 1) 14.0 else 22.0
        5 -> if (sex == 1) 55.0 else 52.0
        6 -> 20.0
        7 -> 0.0
        8 -> 0.0
        9 -> 0.0
        10 -> when {
            sex == 1 && height < 160 -> 42.0
            sex == 1 && height < 170 -> 47.0
            sex == 1 -> 51.0
            height < 150 -> 31.0
            height < 160 -> 34.0
            else -> 38.0
        }
        11 -> 0.0
        99 -> 85.0
        else -> 0.0
    }

    // ------------------------------------------------------------------
    // 维度显示信息（原 App initData 的 imageInfo 顺序）
    // ------------------------------------------------------------------

    data class DimInfo(val type: Int, val name: String)

    val DIMENSIONS: List<DimInfo> = listOf(
        DimInfo(99, "健康评分"),
        DimInfo(1, "体重"),
        DimInfo(2, "体脂率"),
        DimInfo(10, "肌肉"),
        DimInfo(11, "身体年龄"),
        DimInfo(3, "内脏脂肪"),
        DimInfo(6, "BMI"),
        DimInfo(7, "骨量"),
        DimInfo(4, "皮下脂肪"),
        DimInfo(9, "基础代谢"),
        DimInfo(5, "水分")
    )

    /** 维度单位 */
    fun unitOf(type: Int): String = when (type) {
        1, 7, 10 -> "kg"
        2, 4, 5 -> "%"
        8, 9 -> "kcal"
        11 -> "岁"
        99 -> "分"
        else -> ""   // 3 内脏脂肪（等级）、6 BMI：无单位
    }

    /** 区间变化量描述：首个实测值 → 最后一个实测值 */
    fun changeOf(points: List<ChartPoint>, type: Int): String {
        val measured = points.filter { !it.future && (it.value ?: 0.0) > 0 }
        if (measured.size < 2) return ""
        val first = measured.first().value!!
        val last = measured.last().value!!
        val diff = last - first
        if (Math.abs(diff) < 0.05) return "区间内持平"
        val arrow = if (diff < 0) "↓" else "↑"
        return String.format(Locale.US, "区间内 %s %.1f%s", arrow, Math.abs(diff), unitOf(type))
    }

    private fun dayPrefix(c: Calendar): String =
        String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}
