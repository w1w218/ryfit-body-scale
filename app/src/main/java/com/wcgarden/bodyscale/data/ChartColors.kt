package com.wcgarden.bodyscale.data

/**
 * 历史柱状图着色 —— 复刻原 App QueryMeasurementDataService.getColorValue 阈值表。
 * 与主页指标行（DataJudge 动态阈值）不同，历史图用这套固定阈值。
 * 颜色与 DataJudge 同族：蓝/绿/黄/橙。
 */
object ChartColors {

    const val COLOR_BLUE = 0xFF69B5E9.toInt()
    const val COLOR_GREEN = 0xFF87DB00.toInt()
    const val COLOR_YELLOW = 0xFFFFC600.toInt()
    const val COLOR_ORANGE = 0xFFFF7D0A.toInt()
    const val COLOR_GRAY = 0x889C9C9C.toInt() // 无数据（alpha 55%）

    /** 按维度/数值返回柱颜色；data<=0 → 灰 */
    fun getColorValue(type: Int, data: Double, actualAge: Int = 30): Int {
        if (data <= 0) return COLOR_GRAY
        return when (type) {
            1 -> when {
                data <= 52.2 -> COLOR_BLUE
                data <= 67.7 -> COLOR_GREEN
                data <= 79.0 -> COLOR_YELLOW
                else -> COLOR_ORANGE
            }
            2 -> when {
                data <= 13.0 -> COLOR_BLUE
                data <= 21.0 -> COLOR_GREEN
                data <= 25.0 -> COLOR_YELLOW
                else -> COLOR_ORANGE
            }
            3 -> when {
                data <= 10.0 -> COLOR_BLUE
                data <= 15.0 -> COLOR_GREEN
                else -> COLOR_YELLOW
            }
            4 -> when {
                data <= 8.6 -> COLOR_BLUE
                data <= 16.7 -> COLOR_GREEN
                else -> COLOR_YELLOW
            }
            5 -> when {
                data <= 53.6 -> COLOR_BLUE
                data <= 57.0 -> COLOR_GREEN
                else -> COLOR_YELLOW
            }
            6 -> when {
                data <= 18.5 -> COLOR_BLUE
                data <= 24.0 -> COLOR_GREEN
                data <= 28.0 -> COLOR_YELLOW
                else -> COLOR_ORANGE
            }
            7 -> when {
                data <= 3.1 -> COLOR_BLUE
                data <= 3.3 -> COLOR_GREEN
                else -> COLOR_YELLOW
            }
            8 -> if (data <= 1510.0) COLOR_YELLOW else COLOR_GREEN
            9 -> if (data <= 1431.0) COLOR_YELLOW else COLOR_GREEN
            10 -> when {
                data <= 49.4 -> COLOR_BLUE
                data <= 59.4 -> COLOR_GREEN
                else -> COLOR_YELLOW
            }
            11 -> if (data <= actualAge) COLOR_GREEN else COLOR_YELLOW
            99 -> when {
                data < 60.0 -> COLOR_ORANGE
                data < 85.0 -> COLOR_BLUE
                else -> COLOR_GREEN
            }
            else -> COLOR_GRAY
        }
    }
}
