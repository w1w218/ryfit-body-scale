package com.wcgarden.bodyscale.data

/**
 * 指标判定引擎 —— 原样复刻自 base.apk 的 com.chronocloud.bodyscale.util.DataJudge。
 *
 * 每个指标返回 MainItemData(颜色, 等级, 标签索引)：
 *  - 颜色：蓝(偏低)/绿(正常)/黄(偏高)/橙(过高)（原 App main_blue/green/yellow/orange_color，
 *    数值取自同 App 历史柱状图 getColorValue 的 hex 字面量）
 *  - crrentNumber：0=第一档(蓝)，1=第二档，2=第三档，3=第四档（对应色带分段）
 */
object DataJudge {

    const val COLOR_BLUE = 0xFF69B5E9.toInt()
    const val COLOR_GREEN = 0xFF87DB00.toInt()
    const val COLOR_YELLOW = 0xFFFFC600.toInt()
    const val COLOR_ORANGE = 0xFFFF7D0A.toInt()
    const val COLOR_GRAY = 0xFF9C9C9C.toInt()

    data class MainItemData(
        val color: Int,
        val crrentNumber: Int,
        val tag: Int = 0
    )

    // ------------------------------------------------------------------
    // 基础分档（原 App 三个重载）
    // ------------------------------------------------------------------

    /** 2 段：num < num1 → 蓝(0)；num1 <= num → 黄(1) */
    fun getColorByDataJudge(num: Double, num1: Double): MainItemData =
        if (num < num1) MainItemData(COLOR_BLUE, 0)
        else MainItemData(COLOR_YELLOW, 1)

    /** 3 段：< num1 蓝(0)；num1~num2 绿(1)；>= num2 黄(2) */
    fun getColorByDataJudge(num: Double, num1: Double, num2: Double): MainItemData =
        when {
            num < num1 -> MainItemData(COLOR_BLUE, 0)
            num < num2 -> MainItemData(COLOR_GREEN, 1)
            else -> MainItemData(COLOR_YELLOW, 2)
        }

    /** 4 段：< num1 蓝(0)；num1~num2 绿(1)；num2~num3 黄(2)；>= num3 橙(3) */
    fun getColorByDataJudge(num: Double, num1: Double, num2: Double, num3: Double): MainItemData =
        when {
            num < num1 -> MainItemData(COLOR_BLUE, 0)
            num < num2 -> MainItemData(COLOR_GREEN, 1)
            num < num3 -> MainItemData(COLOR_YELLOW, 2)
            else -> MainItemData(COLOR_ORANGE, 3)
        }

    /** 基础代谢：num(标准) < num1(实际) → 绿(达标,1)；实际 <= 标准 → 蓝(未达标,0) */
    fun getMetabolismColorByDataJudge(standard: Double, actual: Double): MainItemData =
        if (standard < actual) MainItemData(COLOR_GREEN, 1)
        else MainItemData(COLOR_BLUE, 0)

    // ------------------------------------------------------------------
    // 各指标判定（原 App 方法，阈值/公式原样）
    // ------------------------------------------------------------------

    /** 骨量（相对体重）：男 [310/体重, 330/体重]；女 [240/体重, 260/体重]，单位 % */
    fun getBoneColorByDataJuge(sex: Int, weight: Double, num: Double): MainItemData =
        if (sex == 1) getColorByDataJudge(num, 310.0 / weight, 330.0 / weight)
        else getColorByDataJudge(num, 240.0 / weight, 260.0 / weight)

    /** 体脂率（%）：男 ≤30 [13,21,25] / >30 [16,24,25]；女 ≤30 [16,25,30] / >30 [19,28,30] */
    fun getFatColorByDataJuge(sex: Int, age: Int, num: Double): MainItemData =
        when {
            sex == 1 && age <= 30 -> getColorByDataJudge(num, 13.0, 21.0, 25.0)
            sex == 1 -> getColorByDataJudge(num, 16.0, 24.0, 25.0)
            age <= 30 -> getColorByDataJudge(num, 16.0, 25.0, 30.0)
            else -> getColorByDataJudge(num, 19.0, 28.0, 30.0)
        }

    /** 基础代谢(亚洲标准)，标准值 = 线性公式（按性别/年龄） */
    fun getMetabolismOColorByDataJuge(sex: Int, age: Int, weight: Double, actual: Double): MainItemData {
        val standard = if (sex == 1) {
            when {
                age < 18 -> 17.5 * weight + 651.0
                age < 30 -> 15.3 * weight + 679.0
                else -> 11.6 * weight + 879.0
            }
        } else {
            when {
                age < 18 -> 12.2 * weight + 746.0
                age < 30 -> 14.7 * weight + 496.0
                else -> 8.7 * weight + 820.0
            }
        }
        return getMetabolismColorByDataJudge(standard, actual)
    }

    /** 基础代谢(欧美标准)，标准值 = 按年龄查表（kcal/天） */
    fun getMetabolismYColorByDataJuge(sex: Int, age: Int, actual: Double): MainItemData {
        val standard: Int = if (sex == 1) {
            when {
                age < 12 -> 1330
                age < 15 -> 1490
                age < 18 -> 1580
                age < 30 -> 1510
                age < 50 -> 1530
                age < 70 -> 1400
                else -> 1280
            }
        } else {
            when {
                age < 12 -> 1200
                age < 15 -> 1360
                age < 18 -> 1280
                age < 30 -> 1120
                age < 50 -> 1150
                age < 70 -> 1110
                else -> 1000
            }
        }
        return getMetabolismColorByDataJudge(standard.toDouble(), actual)
    }

    /** 肌肉比例（%），按性别/身高分档 */
    fun getMuscleColorByDataJuge(sex: Int, height: Double, num: Double): MainItemData =
        if (sex == 1) {
            when {
                height < 160 -> getColorByDataJudge(num, 38.5, 46.5)
                height < 171 -> getColorByDataJudge(num, 44.0, 52.4)
                else -> getColorByDataJudge(num, 49.4, 59.4)
            }
        } else {
            when {
                height < 150 -> getColorByDataJudge(num, 29.1, 34.7)
                height < 161 -> getColorByDataJudge(num, 32.9, 37.5)
                else -> getColorByDataJudge(num, 36.5, 42.5)
            }
        }

    /** 内脏脂肪（等级）：< 9 绿(0)；9~15 黄(1)；>= 15 橙(2) */
    fun getOffalaColorByDataJudge(num: Double, num1: Double, num2: Double): MainItemData =
        when {
            num < num1 -> MainItemData(COLOR_GREEN, 0)
            num < num2 -> MainItemData(COLOR_YELLOW, 1)
            else -> MainItemData(COLOR_ORANGE, 2)
        }

    /** 皮下脂肪（%）：男 [8.6, 16.7]；女 [18.5, 26.7] */
    fun getSkinFatColorByDataJuge(sex: Int, num: Double): MainItemData =
        if (sex == 1) getColorByDataJudge(num, 8.6, 16.7)
        else getColorByDataJudge(num, 18.5, 26.7)

    /** 水分（%）：男 ≤30 [53.6,57] / >30 [53.3,55.6]；女 ≤30 [49.5,52.9] / >30 [48.1,51.5] */
    fun getWaterColorByDataJuge(sex: Int, age: Int, num: Double): MainItemData =
        when {
            sex == 1 && age <= 30 -> getColorByDataJudge(num, 53.6, 57.0)
            sex == 1 -> getColorByDataJudge(num, 53.3, 55.6)
            age <= 30 -> getColorByDataJudge(num, 49.5, 52.9)
            else -> getColorByDataJudge(num, 48.1, 51.5)
        }

    /** 身体年龄：< 实际年龄 → 蓝(0)；>= → 黄(1)（原 App 用 2 段判定） */
    fun getBodyAgeColorByDataJuge(bodyAge: Double, actualAge: Int): MainItemData =
        getColorByDataJudge(bodyAge, actualAge.toDouble())

    /** BMI → 体重阈值换算（原 App getWeightByBmi：bmi/10000 × 身高²） */
    fun getWeightByBmi(bmi: Double, heightCm: Int): Double =
        ((bmi / 10000.0) * (heightCm * heightCm))

    // ------------------------------------------------------------------
    // 阈值数组（与判定方法共用，供界面画色带/刻度）
    // ------------------------------------------------------------------

    fun weightThresholds(heightCm: Int): DoubleArray =
        doubleArrayOf(getWeightByBmi(18.5, heightCm), getWeightByBmi(24.0, heightCm), getWeightByBmi(28.0, heightCm))

    fun bmiThresholds(): DoubleArray = doubleArrayOf(18.5, 24.0, 28.0)

    fun fatThresholds(sex: Int, age: Int): DoubleArray = when {
        sex == 1 && age <= 30 -> doubleArrayOf(13.0, 21.0, 25.0)
        sex == 1 -> doubleArrayOf(16.0, 24.0, 25.0)
        age <= 30 -> doubleArrayOf(16.0, 25.0, 30.0)
        else -> doubleArrayOf(19.0, 28.0, 30.0)
    }

    fun waterThresholds(sex: Int, age: Int): DoubleArray = when {
        sex == 1 && age <= 30 -> doubleArrayOf(53.6, 57.0)
        sex == 1 -> doubleArrayOf(53.3, 55.6)
        age <= 30 -> doubleArrayOf(49.5, 52.9)
        else -> doubleArrayOf(48.1, 51.5)
    }

    fun muscleThresholds(sex: Int, heightCm: Int): DoubleArray = if (sex == 1) {
        when {
            heightCm < 160 -> doubleArrayOf(38.5, 46.5)
            heightCm < 171 -> doubleArrayOf(44.0, 52.4)
            else -> doubleArrayOf(49.4, 59.4)
        }
    } else {
        when {
            heightCm < 150 -> doubleArrayOf(29.1, 34.7)
            heightCm < 161 -> doubleArrayOf(32.9, 37.5)
            else -> doubleArrayOf(36.5, 42.5)
        }
    }

    fun boneThresholds(sex: Int, weightKg: Double): DoubleArray =
        if (sex == 1) doubleArrayOf(310.0 / weightKg, 330.0 / weightKg)
        else doubleArrayOf(240.0 / weightKg, 260.0 / weightKg)

    fun skinfatThresholds(sex: Int): DoubleArray =
        if (sex == 1) doubleArrayOf(8.6, 16.7) else doubleArrayOf(18.5, 26.7)

    fun offalaThresholds(): DoubleArray = doubleArrayOf(9.0, 15.0)

    fun metabolismOThreshold(sex: Int, age: Int, weightKg: Double): Double = if (sex == 1) {
        when {
            age < 18 -> 17.5 * weightKg + 651.0
            age < 30 -> 15.3 * weightKg + 679.0
            else -> 11.6 * weightKg + 879.0
        }
    } else {
        when {
            age < 18 -> 12.2 * weightKg + 746.0
            age < 30 -> 14.7 * weightKg + 496.0
            else -> 8.7 * weightKg + 820.0
        }
    }

    fun metabolismYThreshold(sex: Int, age: Int): Int = if (sex == 1) {
        when {
            age < 12 -> 1330
            age < 15 -> 1490
            age < 18 -> 1580
            age < 30 -> 1510
            age < 50 -> 1530
            age < 70 -> 1400
            else -> 1280
        }
    } else {
        when {
            age < 12 -> 1200
            age < 15 -> 1360
            age < 18 -> 1280
            age < 30 -> 1120
            age < 50 -> 1150
            age < 70 -> 1110
            else -> 1000
        }
    }
}
