package com.wcgarden.bodyscale.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 色带定位几何单测 —— 锁定原 App setPao 的"每段等宽 + 段内插值"算法。
 * 回归背景：曾用"值÷最大阈值"的错误映射，导致内脏脂肪 11 被画到 15 右侧。
 */
class BarGeometryTest {

    private val eps = 1e-4f

    @Test
    fun `visceral fat 11 in 9-15 should sit at one third of the middle segment`() {
        // 阈值 [9,15] → 3 段，每段 1/3。
        // 11 落在段 1（偏高，9~15），段内 (11-9)/(15-9)=1/3
        // → 比例 = 1/3 + (1/3)(1/3) = 0.4444
        val r = BarGeometry.pinRatio(11.0, doubleArrayOf(9.0, 15.0))
        assertEquals(0.4444f, r, eps)
        // 必须落在中间段内（1/3 ~ 2/3），而不是 15 右侧
        assert(r > 1f / 3 && r < 2f / 3)
    }

    @Test
    fun `visceral fat 16 exceeds last threshold stays inside last segment`() {
        val r = BarGeometry.pinRatio(16.0, doubleArrayOf(9.0, 15.0))
        assert(r >= 2f / 3 && r <= 1f) { "expected in last segment, got $r" }
    }

    @Test
    fun `value on threshold boundary sits at tick`() {
        // 内脏脂肪 15 → 正好在 2/3 处
        assertEquals(2f / 3, BarGeometry.pinRatio(15.0, doubleArrayOf(9.0, 15.0)), eps)
        // 内脏脂肪 9 → 正好在 1/3 处
        assertEquals(1f / 3, BarGeometry.pinRatio(9.0, doubleArrayOf(9.0, 15.0)), eps)
    }

    @Test
    fun `value below first threshold sits in first segment`() {
        // 6 → 段 0（0~9）内 2/3 → 比例 = (2/3)(1/3) = 0.2222
        assertEquals(0.2222f, BarGeometry.pinRatio(6.0, doubleArrayOf(9.0, 15.0)), eps)
    }

    @Test
    fun `weight 74 with bmi thresholds`() {
        // 阈值 [53.5, 69.4, 80.9] → 4 段，每段 1/4。
        // 74 → 段 2（微胖，69.4~80.9），段内 (74-69.4)/11.5 = 0.4
        // → 比例 = 2/4 + 0.4/4 = 0.6
        val r = BarGeometry.pinRatio(74.0, doubleArrayOf(53.5, 69.4, 80.9))
        assertEquals(0.6f, r, eps)
    }

    @Test
    fun `body fat 26 point 2 with thresholds 16 24 25`() {
        // 阈值 [16,24,25] → 4 段。
        // 26.2 → 末段（≥25），参考末段宽度 (25-24)=1 → 比例 = 3/4 + 1/4 = 1.0
        assertEquals(1.0f, BarGeometry.pinRatio(26.2, doubleArrayOf(16.0, 24.0, 25.0)), eps)
        // 24.5 → 段 2（24~25）内 0.5 → 2/4 + 0.5/4 = 0.625
        assertEquals(0.625f, BarGeometry.pinRatio(24.5, doubleArrayOf(16.0, 24.0, 25.0)), eps)
    }

    @Test
    fun `bone 4 point 5 at thresholds`() {
        // 骨量阈值 [4.3, 4.5]：4.5 → 末段起点 = 2/3
        assertEquals(2f / 3, BarGeometry.pinRatio(4.5, doubleArrayOf(4.3, 4.5)), eps)
    }

    @Test
    fun `single threshold segmentation`() {
        // 基础代谢阈值 [1724.6] → 2 段，每段 1/2
        // 1546 → 段 0（0~1724.6）内 1546/1724.6 = 0.8964 → 比例 0.4482
        assertEquals(0.4482f, BarGeometry.pinRatio(1546.0, doubleArrayOf(1724.6)), 1e-3f)
        // 1900 → 末段（≥1724.6），参考跨度 0~1724.6 → frac=(1900-1724.6)/1724.6=0.1017 → 比例 0.5+0.0508=0.5508
        assertEquals(0.5508f, BarGeometry.pinRatio(1900.0, doubleArrayOf(1724.6)), 1e-3f)
    }

    @Test
    fun `tick positions are even`() {
        assertEquals(1f / 3, BarGeometry.tickRatio(0, 2), eps)
        assertEquals(2f / 3, BarGeometry.tickRatio(1, 2), eps)
        assertEquals(0.25f, BarGeometry.tickRatio(0, 3), eps)
        assertEquals(0.75f, BarGeometry.tickRatio(2, 3), eps)
    }

    @Test
    fun `empty thresholds returns zero`() {
        assertEquals(0f, BarGeometry.pinRatio(50.0, DoubleArray(0)), eps)
    }
}
