package com.wcgarden.bodyscale.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 本地 SQLite：称重记录表（结构沿用原 App query_measurement_data） */
class MeasurementDb(context: Context) :
    SQLiteOpenHelper(context, "bodyscale.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MEASUREMENT (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                memid TEXT,
                weight TEXT,
                bmi TEXT,
                fat TEXT,
                skinfat TEXT,
                offalfat TEXT,
                muscle TEXT,
                metabolism TEXT,
                water TEXT,
                bone TEXT,
                bodyage TEXT,
                devcode TEXT,
                status TEXT,
                checkdate TEXT,
                cratetime TEXT,
                type TEXT,
                ryfit TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MEASUREMENT")
        onCreate(db)
    }

    fun insert(m: MeasurementRecord): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("weight", m.weight.toString())
            put("bmi", m.bmi.toString())
            put("fat", m.fat.toString())
            put("skinfat", m.skinfat.toString())
            put("offalfat", m.offalfat.toString())
            put("muscle", m.muscle.toString())
            put("metabolism", m.metabolism.toString())
            put("water", m.water.toString())
            put("bone", m.bone.toString())
            put("bodyage", m.bodyage.toString())
            put("devcode", m.devcode)
            put("checkdate", m.checkdate)
            put("cratetime", System.currentTimeMillis().toString())
            put("type", "1")
            put("ryfit", m.ryfit.toString())
        }
        return db.insert(TABLE_MEASUREMENT, null, cv)
    }

    /** 全部记录，按时间倒序 */
    fun queryAll(): List<MeasurementRecord> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MEASUREMENT, null, null, null, null, null,
            "checkdate DESC"
        )
        val list = mutableListOf<MeasurementRecord>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
            val weight = cursor.getString(cursor.getColumnIndexOrThrow("weight")).toDoubleOrNull() ?: 0.0
            val bmi = cursor.getString(cursor.getColumnIndexOrThrow("bmi")).toDoubleOrNull() ?: 0.0
            val fat = cursor.getString(cursor.getColumnIndexOrThrow("fat")).toDoubleOrNull() ?: 0.0
            val skinfat = cursor.getString(cursor.getColumnIndexOrThrow("skinfat")).toDoubleOrNull() ?: 0.0
            val offalfat = cursor.getString(cursor.getColumnIndexOrThrow("offalfat")).toIntOrNull() ?: 0
            val muscle = cursor.getString(cursor.getColumnIndexOrThrow("muscle")).toDoubleOrNull() ?: 0.0
            val metabolism = cursor.getString(cursor.getColumnIndexOrThrow("metabolism")).toIntOrNull() ?: 0
            val water = cursor.getString(cursor.getColumnIndexOrThrow("water")).toDoubleOrNull() ?: 0.0
            val bone = cursor.getString(cursor.getColumnIndexOrThrow("bone")).toDoubleOrNull() ?: 0.0
            val bodyage = cursor.getString(cursor.getColumnIndexOrThrow("bodyage")).toIntOrNull() ?: 0
            val ryfit = cursor.getString(cursor.getColumnIndexOrThrow("ryfit")).toDoubleOrNull() ?: 0.0
            val devcode = cursor.getString(cursor.getColumnIndexOrThrow("devcode")).orEmpty()
            val checkdate = cursor.getString(cursor.getColumnIndexOrThrow("checkdate")).orEmpty()
            list += MeasurementRecord(
                id = id,
                timestamp = 0,
                checkdate = checkdate,
                weight = weight,
                bmi = bmi,
                fat = fat,
                skinfat = skinfat,
                offalfat = offalfat,
                muscle = muscle,
                metabolism = metabolism,
                water = water,
                bone = bone,
                bodyage = bodyage,
                ryfit = ryfit,
                devcode = devcode
            )
        }
        cursor.close()
        return list
    }

    /** 清空全部测量记录，返回删除条数 */
    fun clear(): Int = writableDatabase.delete(TABLE_MEASUREMENT, null, null)

    /** 按 devcode 删除（用于清理早期版本遗留的演示数据），返回删除条数 */
    fun deleteByDevCode(devCode: String): Int =
        writableDatabase.delete(TABLE_MEASUREMENT, "devcode=?", arrayOf(devCode))

    fun delete(id: Long) {
        writableDatabase.delete(TABLE_MEASUREMENT, "id=?", arrayOf(id.toString()))
    }

    companion object {
        const val TABLE_MEASUREMENT = "query_measurement_data"
    }
}
