package com.wcgarden.bodyscale.data

import android.content.Context

/**
 * 一次性数据维护 —— 版本升级后清理历史遗留数据。
 *
 * 背景：早期调试版本会在空库时自动灌入演示数据（devcode='DEMO'），
 * 装到真机上也会出现模拟记录。现在改为只在设置页手动填充，
 * 因此升级后第一次启动时把这些遗留的演示数据清掉。
 *
 * 只删 devcode='DEMO' 的记录：真机实测记录（devcode 为空）不受影响。
 */
object DataMaintenance {

    private const val PREFS = "maintenance"
    private const val KEY_DEMO_PURGED = "demo_purged_v1"

    /** 清理早期遗留的演示数据；返回删除条数（已清理过则返回 0） */
    fun purgeLegacyDemoData(context: Context, db: MeasurementDb): Int {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (sp.getBoolean(KEY_DEMO_PURGED, false)) return 0
        val removed = db.deleteByDevCode(DemoData.DEV_CODE)
        sp.edit().putBoolean(KEY_DEMO_PURGED, true).apply()
        return removed
    }
}
