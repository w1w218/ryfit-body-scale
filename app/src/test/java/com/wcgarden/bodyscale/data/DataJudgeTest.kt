package com.wcgarden.bodyscale.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DataJudge 判定逻辑单测 —— 阈值/分档/颜色对照原 App util.DataJudge。
 */
class DataJudgeTest {

    private fun assertColor(item: DataJudge.MainItemData, expectColor: Int, expectLevel: Int) {
        assertEquals(expectColor, item.color)
        assertEquals(expectLevel, item.crrentNumber)
    }

    // ------------------------------------------------------------------
    // 基础分档
    // ------------------------------------------------------------------

    @Test
    fun `two-segment judgment`() {
        // num < num1 → 蓝(0)；num1 <= num → 黄(1)
        assertColor(DataJudge.getColorByDataJudge(50.0, 60.0), DataJudge.COLOR_BLUE, 0)
        assertColor(DataJudge.getColorByDataJudge(60.0, 60.0), DataJudge.COLOR_YELLOW, 1)
        assertColor(DataJudge.getColorByDataJudge(80.0, 60.0), DataJudge.COLOR_YELLOW, 1)
    }

    @Test
    fun `three-segment judgment`() {
        // <num1 蓝；num1~num2 绿；>=num2 黄
        assertColor(DataJudge.getColorByDataJudge(10.0, 20.0, 30.0), DataJudge.COLOR_BLUE, 0)
        assertColor(DataJudge.getColorByDataJudge(25.0, 20.0, 30.0), DataJudge.COLOR_GREEN, 1)
        assertColor(DataJudge.getColorByDataJudge(35.0, 20.0, 30.0), DataJudge.COLOR_YELLOW, 2)
    }

    @Test
    fun `four-segment judgment`() {
        // <num1 蓝；num1~num2 绿；num2~num3 黄；>=num3 橙
        assertColor(DataJudge.getColorByDataJudge(10.0, 20.0, 30.0, 40.0), DataJudge.COLOR_BLUE, 0)
        assertColor(DataJudge.getColorByDataJudge(25.0, 20.0, 30.0, 40.0), DataJudge.COLOR_GREEN, 1)
        assertColor(DataJudge.getColorByDataJudge(35.0, 20.0, 30.0, 40.0), DataJudge.COLOR_YELLOW, 2)
        assertColor(DataJudge.getColorByDataJudge(45.0, 20.0, 30.0, 40.0), DataJudge.COLOR_ORANGE, 3)
    }

    // ------------------------------------------------------------------
    // 各指标（原 App 阈值）
    // ------------------------------------------------------------------

    @Test
    fun `body fat by sex and age`() {
        // 男 ≤30：[13,21,25]
        assertColor(DataJudge.getFatColorByDataJuge(1, 25, 15.0), DataJudge.COLOR_GREEN, 1)
        assertColor(DataJudge.getFatColorByDataJuge(1, 25, 22.0), DataJudge.COLOR_YELLOW, 2)
        assertColor(DataJudge.getFatColorByDataJuge(1, 25, 28.0), DataJudge.COLOR_ORANGE, 3)
        // 男 >30：[16,24,25]
        assertColor(DataJudge.getFatColorByDataJuge(1, 40, 18.0), DataJudge.COLOR_GREEN, 1)
        // 女 ≤30：[16,25,30]
        assertColor(DataJudge.getFatColorByDataJuge(0, 25, 20.0), DataJudge.COLOR_GREEN, 1)
        // 女 >30：[19,28,30]
        assertColor(DataJudge.getFatColorByDataJuge(0, 40, 29.0), DataJudge.COLOR_YELLOW, 2)
        assertColor(DataJudge.getFatColorByDataJuge(0, 40, 30.0), DataJudge.COLOR_ORANGE, 3)
    }

    @Test
    fun `water by sex and age`() {
        // 男 ≤30：[53.6,57]
        assertColor(DataJudge.getWaterColorByDataJuge(1, 25, 55.0), DataJudge.COLOR_GREEN, 1)
        assertColor(DataJudge.getWaterColorByDataJuge(1, 25, 50.0), DataJudge.COLOR_BLUE, 0)
        // 女 ≤30：[49.5,52.9]
        assertColor(DataJudge.getWaterColorByDataJuge(0, 25, 51.0), DataJudge.COLOR_GREEN, 1)
    }

    @Test
    fun `muscle by sex and height`() {
        // 男 160~170：[44,52.4]
        assertColor(DataJudge.getMuscleColorByDataJuge(1, 168.0, 48.0), DataJudge.COLOR_GREEN, 1)
        assertColor(DataJudge.getMuscleColorByDataJuge(1, 168.0, 42.0), DataJudge.COLOR_BLUE, 0)
        // 男 >=171：[49.4,59.4]
        assertColor(DataJudge.getMuscleColorByDataJuge(1, 180.0, 55.0), DataJudge.COLOR_GREEN, 1)
        // 女 150~160：[32.9,37.5]
        assertColor(DataJudge.getMuscleColorByDataJuge(0, 155.0, 35.0), DataJudge.COLOR_GREEN, 1)
    }

    @Test
    fun `bone relative to weight`() {
        // 男 58.5kg：310/58.5=5.30、330/58.5=5.64
        val sex = 1
        val w = 58.5
        assertColor(DataJudge.getBoneColorByDataJuge(sex, w, 5.0), DataJudge.COLOR_BLUE, 0)
        assertColor(DataJudge.getBoneColorByDataJuge(sex, w, 5.5), DataJudge.COLOR_GREEN, 1)
        assertColor(DataJudge.getBoneColorByDataJuge(sex, w, 6.0), DataJudge.COLOR_YELLOW, 2)
    }

    @Test
    fun `skin fat by sex`() {
        assertColor(DataJudge.getSkinFatColorByDataJuge(1, 12.0), DataJudge.COLOR_GREEN, 1)
        assertColor(DataJudge.getSkinFatColorByDataJuge(1, 20.0), DataJudge.COLOR_YELLOW, 2)
        assertColor(DataJudge.getSkinFatColorByDataJuge(0, 20.0), DataJudge.COLOR_GREEN, 1)
    }

    @Test
    fun `visceral fat`() {
        // <9 绿；9~15 黄；>=15 橙
        assertColor(DataJudge.getOffalaColorByDataJudge(8.0, 9.0, 15.0), DataJudge.COLOR_GREEN, 0)
        assertColor(DataJudge.getOffalaColorByDataJudge(10.0, 9.0, 15.0), DataJudge.COLOR_YELLOW, 1)
        assertColor(DataJudge.getOffalaColorByDataJudge(16.0, 9.0, 15.0), DataJudge.COLOR_ORANGE, 2)
    }

    @Test
    fun `body age vs actual age`() {
        // < 实际年龄 → 蓝(0)；>= → 黄(1)
        assertColor(DataJudge.getBodyAgeColorByDataJuge(30.0, 40), DataJudge.COLOR_BLUE, 0)
        assertColor(DataJudge.getBodyAgeColorByDataJuge(45.0, 40), DataJudge.COLOR_YELLOW, 1)
    }

    // ------------------------------------------------------------------
    // 基础代谢
    // ------------------------------------------------------------------

    @Test
    fun `metabolism asian standard`() {
        // 男 40 岁 58.5kg：标准 = 11.6*58.5+879 = 1557.6；实测 1340 < 标准 → 未达标蓝
        val item = DataJudge.getMetabolismOColorByDataJuge(1, 40, 58.5, 1340.0)
        assertColor(item, DataJudge.COLOR_BLUE, 0)
        // 实测 1700 > 标准 → 达标绿
        val item2 = DataJudge.getMetabolismOColorByDataJuge(1, 40, 58.5, 1700.0)
        assertColor(item2, DataJudge.COLOR_GREEN, 1)
    }

    @Test
    fun `metabolism european table`() {
        // 男 30-49 岁：标准 1530；实测 < 标准 → 蓝未达标
        assertColor(DataJudge.getMetabolismYColorByDataJuge(1, 40, 1400.0), DataJudge.COLOR_BLUE, 0)
        assertColor(DataJudge.getMetabolismYColorByDataJuge(1, 40, 1600.0), DataJudge.COLOR_GREEN, 1)
        // 女 18-29 岁：标准 1120；实测 1100 < 标准 → 蓝
        assertColor(DataJudge.getMetabolismYColorByDataJuge(0, 25, 1100.0), DataJudge.COLOR_BLUE, 0)
    }

    // ------------------------------------------------------------------
    // 阈值数组（与判定共用）
    // ------------------------------------------------------------------

    @Test
    fun `weight thresholds derived from bmi`() {
        // 身高 169：18.5/24/28 × 1.69²
        val t = DataJudge.weightThresholds(169)
        assertEquals(18.5 * 1.69 * 1.69, t[0], 0.1)
        assertEquals(24.0 * 1.69 * 1.69, t[1], 0.1)
        assertEquals(28.0 * 1.69 * 1.69, t[2], 0.1)
    }

    @Test
    fun `threshold helpers match judgment`() {
        val sex = 1
        val age = 40
        val height = 169
        val weight = 60.0
        // 体脂判定与阈值一致：fat=17 → 男>30 [16,24,25] → 绿
        val th = DataJudge.fatThresholds(sex, age)
        assertTrue(th.contentEquals(doubleArrayOf(16.0, 24.0, 25.0)))
        assertColor(DataJudge.getFatColorByDataJuge(sex, age, 17.0), DataJudge.COLOR_GREEN, 1)
        // 骨量阈值相对体重
        val bt = DataJudge.boneThresholds(sex, weight)
        assertTrue(bt.contentEquals(doubleArrayOf(310.0 / weight, 330.0 / weight)))
        // 代谢亚标准
        assertEquals(11.6 * weight + 879.0, DataJudge.metabolismOThreshold(sex, age, weight), 1e-9)
    }
}
