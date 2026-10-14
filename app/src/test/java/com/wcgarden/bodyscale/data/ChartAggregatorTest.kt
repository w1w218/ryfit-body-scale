package com.wcgarden.bodyscale.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartAggregatorTest {

    private fun rec(
        checkdate: String,
        weight: Double = 60.0, fat: Double = 20.0, bmi: Double = 21.0,
        water: Double = 55.0, muscle: Double = 25.0, bone: Double = 3.0,
        metabolism: Int = 1400, skinfat: Double = 12.0, offalfat: Int = 8,
        bodyage: Int = 30, ryfit: Double = 80.0
    ) = MeasurementRecord(
        id = 0, timestamp = 0, checkdate = checkdate,
        weight = weight, bmi = bmi, fat = fat, skinfat = skinfat,
        offalfat = offalfat, muscle = muscle, metabolism = metabolism,
        water = water, bone = bone, bodyage = bodyage, ryfit = ryfit, devcode = ""
    )

    // ------------------------------------------------------------------
    // 提取
    // ------------------------------------------------------------------

    @Test
    fun `extract dimension values`() {
        val r = rec("2026-10-01 08:00:00", weight = 60.0, fat = 20.0, bmi = 21.0,
            water = 55.0, muscle = 25.0, bone = 3.0, metabolism = 1400,
            skinfat = 12.0, offalfat = 8, bodyage = 30, ryfit = 80.0)
        assertEquals(60.0, ChartAggregator.extract(r, 1), 1e-9)
        assertEquals(20.0, ChartAggregator.extract(r, 2), 1e-9)
        assertEquals(8.0, ChartAggregator.extract(r, 3), 1e-9)
        assertEquals(12.0, ChartAggregator.extract(r, 4), 1e-9)
        assertEquals(55.0, ChartAggregator.extract(r, 5), 1e-9)
        assertEquals(21.0, ChartAggregator.extract(r, 6), 1e-9)
        assertEquals(3.0, ChartAggregator.extract(r, 7), 1e-9)
        // type 8 = 基础代谢重算（原 getO 四舍五入）：370+21.6*(1-20/100)*60 = 370+1036.8 → 1407
        assertEquals(1407.0, ChartAggregator.extract(r, 8), 0.01)
        assertEquals(1400.0, ChartAggregator.extract(r, 9), 1e-9)
        assertEquals(25.0, ChartAggregator.extract(r, 10), 1e-9)
        assertEquals(30.0, ChartAggregator.extract(r, 11), 1e-9)
        assertEquals(80.0, ChartAggregator.extract(r, 99), 1e-9)
    }

    // ------------------------------------------------------------------
    // 周聚合：同一天多条取均值
    // ------------------------------------------------------------------

    @Test
    fun `day aggregates hourly average`() {
        val cal = java.util.Calendar.getInstance()
        val today = String.format(
            java.util.Locale.US, "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
        // 同一天：8 点两条 → 均值 60.5；20 点一条 → 70.0
        val r1 = rec("$today 08:10:00", weight = 60.0)
        val r2 = rec("$today 08:40:00", weight = 61.0)
        val r3 = rec("$today 20:00:00", weight = 70.0)
        val points = ChartAggregator.aggregate(
            listOf(r1, r2, r3), 1, ChartAggregator.Period.DAY, 0, 1, 30, 170
        )
        assertEquals(24, points.size)
        assertEquals(60.5, points[8].value!!, 1e-9)
        assertEquals(70.0, points[20].value!!, 1e-9)
        assertEquals(null, points[3].value)
    }

    @Test
    fun `week aggregates average per day`() {
        val cal = java.util.Calendar.getInstance()
        val today = String.format(
            java.util.Locale.US, "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
        val points = ChartAggregator.aggregate(
            listOf(rec("$today 08:00:00", weight = 60.0), rec("$today 20:00:00", weight = 61.0)),
            1, ChartAggregator.Period.WEEK, 0, 1, 30, 170
        )
        // 今天在周内的下标（周一=0，Calendar: 周日=1）
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val idx = if (dow == java.util.Calendar.SUNDAY) 6 else dow - 2
        assertEquals(7, points.size)
        assertEquals(60.5, points[idx].value!!, 1e-9)
    }

    // ------------------------------------------------------------------
    // 无数据 → null + 参考值
    // ------------------------------------------------------------------

    @Test
    fun `empty day yields null value and default reference`() {
        val points = ChartAggregator.aggregate(
            emptyList(), 1, ChartAggregator.Period.DAY, 0, 1, 30, 170
        )
        assertEquals(24, points.size)
        for (p in points) {
            assertNull(p.value)
            assertEquals(false, p.measured)
        }
        // 体重参考值：男 170cm = (170-80)*0.7 = 63
        assertEquals(63.0, ChartAggregator.getDefaultValue(1, 1, 170, 30), 1e-9)
        // 女 160cm = (160-70)*0.6 = 54
        assertEquals(54.0, ChartAggregator.getDefaultValue(1, 0, 160, 30), 1e-9)
        // 体脂：男 ≤30 = 18
        assertEquals(18.0, ChartAggregator.getDefaultValue(2, 1, 170, 25), 1e-9)
        // 内脏脂肪 = 4
        assertEquals(4.0, ChartAggregator.getDefaultValue(3, 1, 170, 30), 1e-9)
        // 评分 = 85
        assertEquals(85.0, ChartAggregator.getDefaultValue(99, 1, 170, 30), 1e-9)
    }

    // ------------------------------------------------------------------
    // 周期点数
    // ------------------------------------------------------------------

    @Test
    fun `period point counts`() {
        val empty = emptyList<MeasurementRecord>()
        assertEquals(24, ChartAggregator.aggregate(empty, 1, ChartAggregator.Period.DAY, 0, 1, 30, 170).size)
        assertEquals(7, ChartAggregator.aggregate(empty, 1, ChartAggregator.Period.WEEK, 0, 1, 30, 170).size)
        assertEquals(12, ChartAggregator.aggregate(empty, 1, ChartAggregator.Period.YEAR, 0, 1, 30, 170).size)
        val month = ChartAggregator.aggregate(empty, 1, ChartAggregator.Period.MONTH, 0, 1, 30, 170)
        assertTrue(month.size in 28..31)
    }

    // ------------------------------------------------------------------
    // 维度清单
    // ------------------------------------------------------------------

    @Test
    fun `dimensions match original order`() {
        val names = ChartAggregator.DIMENSIONS.map { it.name }
        assertEquals(11, names.size)
        assertEquals("健康评分", names[0])
        assertEquals("体重", names[1])
        assertEquals("体脂率", names[2])
        assertEquals("水分", names[10])
    }

    @Test
    fun `year aggregation uses monthly average`() {
        val cal = java.util.Calendar.getInstance()
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val today = String.format(
            java.util.Locale.US, "%04d-%02d-15 08:00:00",
            cal.get(java.util.Calendar.YEAR), month
        )
        val r = rec(today, weight = 74.0)
        val points = ChartAggregator.aggregate(listOf(r), 1, ChartAggregator.Period.YEAR, 0, 1, 30, 170)
        println("YEAR points: " + points.joinToString { it.label + "=" + it.value + "/future=" + it.future })
        assertEquals(12, points.size)
        val thisMonth = points[cal.get(java.util.Calendar.MONTH)]
        assertEquals(74.0, thisMonth.value!!, 1e-9)
    }
}
