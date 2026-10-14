package com.wcgarden.bodyscale.data

import android.content.Context
import android.content.SharedPreferences

/** 用户本地信息（性别/年龄/身高/目标体重）+ 设备 MAC（原 App 存 SharedPreferences 同思路） */
class UserPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("user", Context.MODE_PRIVATE)

    var sex: Int
        get() = sp.getInt("sex", 1)   // 1=男 0=女
        set(v) = sp.edit().putInt("sex", v).apply()

    var age: Int
        get() = sp.getInt("age", 30)
        set(v) = sp.edit().putInt("age", v).apply()

    var height: Int
        get() = sp.getInt("height", 170)
        set(v) = sp.edit().putInt("height", v).apply()

    var targetWeight: Double
        get() = sp.getFloat("target_weight", 60f).toDouble()
        set(v) = sp.edit().putFloat("target_weight", v.toFloat()).apply()

    var deviceMac: String
        get() = sp.getString("device_mac", "").orEmpty()
        set(v) = sp.edit().putString("device_mac", v).apply()

    companion object {
        const val KEY_DEVICE_MAC = "device_mac"
    }
}
