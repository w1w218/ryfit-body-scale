package com.wcgarden.bodyscale.data

/**
 * 指标色带的几何计算（纯逻辑，可单测）。
 *
 * 复刻原 App setPao1/setPao4 的定位算法：
 *  - 色带按阈值分成若干段，**每段等宽**（1/段数）
 *  - 段 i 的值域：[t(i-1), t(i))，段 0 = [0, t0)，末段 = [t(n-1), ∞)
 *  - 水滴/标记在段内按该段值域**线性插值**
 */
object BarGeometry {

    /** 返回标记在色带上的比例位置（0~1） */
    fun pinRatio(value: Double, thresholds: DoubleArray): Float {
        if (thresholds.isEmpty()) return 0f
        val segCount = thresholds.size + 1
        val segWidth = 1f / segCount

        // 定位所在段
        var idx = 0
        var lower = 0.0
        for (i in thresholds.indices) {
            if (value >= thresholds[i]) {
                idx = i + 1
                lower = thresholds[i]
            } else break
        }

        // 段内插值
        val frac: Double = if (idx < thresholds.size) {
            val upper = thresholds[idx]
            if (upper > lower) ((value - lower) / (upper - lower)).coerceIn(0.0, 1.0) else 0.0
        } else {
            // 末段（超出最后阈值）：以末段值域宽度为参考，限定在段内
            val prev = if (thresholds.size >= 2) thresholds[thresholds.size - 2] else 0.0
            val last = thresholds.last()
            val span = (last - prev).let { if (it > 0) it else last * 0.2 + 1e-6 }
            ((value - last) / span).coerceIn(0.0, 1.0)
        }
        return (idx * segWidth + frac * segWidth).toFloat()
    }

    /** 阈值刻度在色带上的比例位置（段边界，等分） */
    fun tickRatio(index: Int, thresholdCount: Int): Float =
        (index + 1).toFloat() / (thresholdCount + 1)
}
