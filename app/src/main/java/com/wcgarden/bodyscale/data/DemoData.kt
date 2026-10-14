package com.wcgarden.bodyscale.data

import android.content.Context
import com.wcgarden.bodyscale.ble.ScaleProtocol
import java.util.Calendar
import java.util.Locale
import java.util.Random

/**
 * 调试用模拟数据 —— 过去 90 天的测量记录，便于在模拟器/真机上预览主页指标列表
 * 与历史图表（日/周/月/年各档都有数据）。
 *
 * 注意：**不再由页面自动灌入**。只在设置页"填充演示数据"（仅 debug 包可见）
 * 手动触发，避免真机上混入模拟数据。
 */
object DemoData {

    /** 演示数据的 devcode 标记，用于与真机实测记录（devcode 为空）区分 */
    const val DEV_CODE = "DEMO"

    fun seedIfEmpty(
        context: Context,
        db: MeasurementDb,
        heightCm: Int = 170,
        force: Boolean = false
    ) {
        if (db.queryAll().isNotEmpty()) return
        val flag = context.getSharedPreferences("demo", Context.MODE_PRIVATE)
        if (!force && flag.getBoolean("seeded", false)) return

        val rand = Random(20261014L)
        val cal = Calendar.getInstance()
        val list = mutableListOf<MeasurementRecord>()

        for (dayBack in 90 downTo 0) {
            // 今天必生成（便于预览 24 小时视图）；其他天约六成概率，有记录则 1~3 次
            val isToday = dayBack == 0
            if (!isToday && rand.nextInt(100) >= 62) continue
            val dayBase = 74.0 - dayBack * 0.012
            val times = if (isToday) 3 else 1 + rand.nextInt(3)
            val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            for (k in 0 until times) {
                val c = cal.clone() as Calendar
                c.add(Calendar.DAY_OF_YEAR, -dayBack)
                // 今天的数据不超过当前小时，且均匀分布（避免都挤同一小时）
                val hour = if (isToday) {
                    val nowH = nowHour.coerceAtLeast(6)
                    if (times <= 1) nowH else 6 + (nowH - 6) * k / (times - 1)
                } else {
                    6 + k * 6 + rand.nextInt(3)
                }
                c.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                c.set(Calendar.MINUTE, rand.nextInt(60))
                c.set(Calendar.SECOND, rand.nextInt(60))
                val date = String.format(
                    Locale.US, "%04d-%02d-%02d %02d:%02d:%02d",
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)
                )

                // 同日多次测量围绕当天基准小幅波动（体现“每天取均值”的效果）
                val weight = dayBase + (rand.nextDouble() - 0.5) * 0.5
                val fat = 26.0 - dayBack * 0.008 + (rand.nextDouble() - 0.5) * 0.7
                val water = 50.5 + dayBack * 0.004 + (rand.nextDouble() - 0.5) * 0.6
                val muscle = 42.5 + dayBack * 0.003 + (rand.nextDouble() - 0.5) * 0.4
                val bone = 3.35 + (rand.nextDouble() - 0.5) * 0.08
                val metabolism = 1430 + (rand.nextInt(70) - 35)
                val skinfat = 21.5 - dayBack * 0.006 + (rand.nextDouble() - 0.5) * 0.6
                val offalfat = 10 + (rand.nextInt(3) - 1)
                val bodyage = 47 + (rand.nextInt(5) - 2)
                val bmi = weight / (heightCm / 100.0) / (heightCm / 100.0)
                val ryfit = ScaleProtocol.getRyfitIndex(
                    offalfat.toDouble(), bmi, bodyage.toDouble(), 42
                )

                list += MeasurementRecord(
                    timestamp = c.timeInMillis / 1000,
                    checkdate = date,
                    weight = round1(weight),
                    bmi = round1(bmi),
                    fat = round1(fat),
                    skinfat = round1(skinfat),
                    offalfat = offalfat,
                    muscle = round1(muscle),
                    metabolism = metabolism,
                    water = round1(water),
                    bone = round1(bone),
                    bodyage = bodyage,
                    ryfit = round1(ryfit),
                    devcode = DEV_CODE
                )
            }
        }

        for (r in list) db.insert(r)
        flag.edit().putBoolean("seeded", true).apply()
    }

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
}
