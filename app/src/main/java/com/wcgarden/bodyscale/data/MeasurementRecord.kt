package com.wcgarden.bodyscale.data

/** 一条称重记录（对应原 App query_measurement_data 表字段） */
data class MeasurementRecord(
    val id: Long = 0,
    val timestamp: Long,
    val checkdate: String,
    val weight: Double,      // kg
    val bmi: Double,
    val fat: Double,         // %
    val skinfat: Double,     // %
    val offalfat: Int,       // 等级
    val muscle: Double,      // kg
    val metabolism: Int,     // kcal
    val water: Double,       // %
    val bone: Double,        // kg
    val bodyage: Int,        // 岁
    val ryfit: Double,       // 评分
    val devcode: String      // 设备号（hex，可为空）
)
