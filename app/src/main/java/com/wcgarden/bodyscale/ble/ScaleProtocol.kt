package com.wcgarden.bodyscale.ble

import java.util.UUID

/**
 * 蓝牙体脂秤协议层 —— 从 com.chronocloud.bodyscale（base.apk）逆向复刻。
 *
 * 链路：BLE 服务 0xFFE0，特征 0xFFE1（同一特征读写 + notify）。
 * 上行命令：hex 字符串 → 字节（编码规则与原 App 的 hex2Byte 完全一致：
 *   第 3、4 个参数是"十进制整数转 hex"，用于 C0/A1 命令的身高系数与年龄）。
 * 下行数据：notify 原始字节 → "XX XX ..." 大写 hex（空格分隔）→ 帧解析。
 */
object ScaleProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val NOTIFY_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    /** 时间基准：设备协议里所有时间戳 = (Unix 秒 - 946656000)，即 2000-01-01T00:00:00Z */
    const val EPOCH_OFFSET: Long = 946656000L

    /** 游客槽位（原 App 里 testP9 固定使用 09） */
    const val GUEST_SLOT: String = "09"

    // ------------------------------------------------------------------
    // 命令构造（发送给设备）
    // ------------------------------------------------------------------

    /**
     * 编码发送命令。复刻原 App hex2Byte(String...) 的真实行为：
     *  - 参数索引 2、3（C0/A1/A3 命令的身高系数、年龄）是十进制整数 → 转 hex
     *  - 每个参数按 hex 字符对展开为字节（"A15A" → A1 5A；"C0","09","25","30","01" → C0 09 19 1E 01）
     *  - 单字符补 0
     */
    fun encodeCommand(vararg parts: String): ByteArray {
        val norm = parts.mapIndexed { i, p ->
            if (i > 1 && i < 4) Integer.toHexString(p.toInt()).uppercase() else p
        }
        val hexStr = norm.joinToString("") { it.padStart(2, '0') }
        require(hexStr.length % 2 == 0) { "命令长度必须为偶数：$hexStr" }
        return ByteArray(hexStr.length / 2) { i ->
            hexStr.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /**
     * 时间同步命令：FAF8 + (Unix 秒 - 946656000) 的 4 字节 hex 逆序。
     * 收到设备发来的 "F8" 帧后发送。
     */
    fun timeSyncCommand(nowEpochSec: Long = System.currentTimeMillis() / 1000): String {
        val diff = (nowEpochSec - EPOCH_OFFSET).toInt()
        val padded = Integer.toHexString(diff).uppercase().padStart(8, '0')
        return "FAF8" + padded.substring(6) + padded.substring(4, 6) +
                padded.substring(2, 4) + padded.substring(0, 2)
    }

    /** 用户身高 → 写称用的"身高系数"：原 App 使用 (height - 100) / 0.5 */
    fun userHeightCode(heightCm: Int): Int = ((heightCm - 100) / 0.5).toInt()

    /**
     * 写用户信息命令 C0（单用户用游客槽位 09，与原 App testP9 一致）。
     * 返回待编码的命令片段，发送时用 encodeCommand 编码。
     */
    fun writeUserCommandParts(heightCm: Int, age: Int, sex: Int): Array<String> = arrayOf(
        "C0",
        GUEST_SLOT,
        userHeightCode(heightCm).toString(),
        age.toString(),
        "0$sex"
    )

    /** 帧 ACK：FA + 原帧头 3 字节（如 D0 xx yy → FAD0xxyy） */
    fun ack(header: String, das: Array<String>): String = "FA$header${das[1]}${das[2]}"

    // ------------------------------------------------------------------
    // 下行帧解析（设备 → App）
    // ------------------------------------------------------------------

    /** 一条完整测量记录（对应原 App SubmitDataReq / query_measurement_data 表） */
    data class Measurement(
        val timestamp: Long,
        val checkdate: String,
        val weight: Double,   // kg
        val bmi: Double,
        val fat: Double,      // %
        val water: Double,    // %
        val muscle: Double,   // kg
        val bone: Double,     // kg
        val metabolism: Int,  // kcal
        val skinfat: Double,  // %
        val offalfat: Int,    // 等级
        val bodyage: Int,     // 岁
        val ryfit: Double     // 健康评分 0-100
    )

    /** D0/D1 两帧中的第一帧解析结果，等待第二帧合并 */
    data class FirstFrame(
        val timestamp: Long,
        val checkdate: String,
        val weight: Double,
        val bmi: Double,
        val fat: Double,
        val water: Double,
        val metabolism: Int
    )

    /** 合并 D0/D1 两帧产出完整测量记录 */
    fun mergeMeasurement(first: FirstFrame, das2: Array<String>, age: Int): Measurement {
        val muscle = hexPair(das2, 4, 5) / 10.0
        val bone = hexPair(das2, 6, 7) / 10.0
        val metabolism = hexPair(das2, 8, 9)
        val skinfat = hexPair(das2, 10, 11) / 10.0
        val offalfat = hexValInt(das2, 12)
        val bodyage = hexValInt(das2, 13)
        val ryfit = getRyfitIndex(offalfat.toDouble(), first.bmi, bodyage.toDouble(), age)
        return Measurement(
            timestamp = first.timestamp,
            checkdate = first.checkdate,
            weight = first.weight,
            bmi = first.bmi,
            fat = first.fat,
            water = first.water,
            muscle = muscle,
            bone = bone,
            metabolism = metabolism,
            skinfat = skinfat,
            offalfat = offalfat,
            bodyage = bodyage,
            ryfit = ryfit
        )
    }

    /** 解析 D0/D1 第一帧（das[0]=D0 且 das[2]==1，或 D1 且 das[2] 为奇数） */
    fun parseFirstFrame(das: Array<String>, heightCm: Int, age: Int): FirstFrame {
        val time = hexQuadRev(das, 4) + EPOCH_OFFSET
        val weight = hexPair(das, 8, 9) / 10.0
        val fat = hexPair(das, 10, 11) / 10.0
        val water = hexPair(das, 12, 13) / 10.0
        val bmi = if (heightCm > 0) {
            java.math.BigDecimal(weight / (heightCm.toDouble() * heightCm.toDouble()) * 10000.0)
                .setScale(1, java.math.RoundingMode.HALF_UP).toDouble()
        } else 0.0
        val metabolism = getEuropeanStandard(fat, weight)
        val checkdate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(time * 1000))
        return FirstFrame(time, checkdate, weight, bmi, fat, water, metabolism)
    }

    /** 实时重量帧 D2：weight = hex(das[2]+das[3]) / 10，das[1]==0（hex）表示测量结束 */
    fun parseD2(das: Array<String>): Pair<Double, Boolean> {
        val weight = hexPair(das, 2, 3) / 10.0
        val finished = das.getOrNull(1)?.toIntOrNull(16) == 0
        return weight to finished
    }

    /** 判断 D0 帧是否第一帧（das[2] == 1） */
    fun isD0FirstFrame(das: Array<String>): Boolean = das.getOrNull(2)?.toIntOrNull(16) == 1

    /** 判断 D1 帧是否第一帧（das[2] 为奇数） */
    fun isD1FirstFrame(das: Array<String>): Boolean {
        val v = das.getOrNull(2)?.toIntOrNull(16) ?: return true
        return v % 2 != 0
    }

    /** 是否完整测量帧头（D0/D1） */
    fun isMeasurementFrame(das: Array<String>): Boolean =
        das.isNotEmpty() && (das[0] == "D0" || das[0] == "D1")

    // ------------------------------------------------------------------
    // 公式（原 App GlobalMethod）
    // ------------------------------------------------------------------

    /** 基础代谢（Katch-McArdle 变体）：fat<=0 时返回 0 */
    fun getEuropeanStandard(fat: Double, weight: Double): Int {
        if (fat <= 0.0) return 0
        return (370.0 + 21.6 * (1.0 - fat / 100.0) * weight).toInt()
    }

    /** 健康评分 0-100 */
    fun getRyfitIndex(soffalafat: Double, bmi: Double, bodyAge: Double, age: Int): Double {
        if (soffalafat == 0.0 && bmi == 0.0 && bodyAge == 0.0) return 0.0
        val soffalafat2 = 100.0 - 4.0 * soffalafat
        val bmip = bmi - 20.0
        val bmi2: Double = when {
            bmip == 0.0 -> 100.0
            (bmip < 0.0 && bmip >= -2.0) || (bmip > 0.0 && bmip <= 4.0) ->
                100.0 - kotlin.math.abs(bmip) * 3.0
            bmip < -2.0 -> 100.0 - (((18.0 - bmip) - 20.0) * 5.0 + 6.0)
            else -> 100.0 - (((20.0 + bmip) - 24.0) * 5.0 + 12.0)
        }
        return 0.2 * soffalafat2 + 0.3 * bmi2 + 0.5 * (85.0 - (bodyAge - age) * 2.0)
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /** 原始字节 → "XX XX ..." 大写 hex（与原 App broadcastUpdate 一致） */
    fun bytesToHexString(data: ByteArray): String {
        val sb = StringBuilder(data.size * 3)
        for (b in data) {
            sb.append(String.format("%02X ", b))
        }
        return sb.toString().trim()
    }

    /** hex 字符串 → 字节数组（用于把 notify 字节转数组前的反向操作，主要给测试用） */
    fun hexStringToBytes(hex: String): ByteArray {
        val cleaned = hex.replace(" ", "")
        require(cleaned.length % 2 == 0)
        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun hexPair(das: Array<String>, i: Int, j: Int): Int =
        Integer.parseInt(das[i] + das[j], 16)

    /** das[7]+das[6]+das[5]+das[4]（逆序 4 字节 hex）→ 数值 */
    private fun hexQuadRev(das: Array<String>, base: Int): Long =
        Integer.parseInt(das[base + 3] + das[base + 2] + das[base + 1] + das[base], 16).toLong()

    private fun hexValInt(das: Array<String>, i: Int): Int =
        Integer.parseInt(das[i], 16)
}
