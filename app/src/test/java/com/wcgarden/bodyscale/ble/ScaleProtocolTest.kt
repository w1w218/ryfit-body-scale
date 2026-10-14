package com.wcgarden.bodyscale.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleProtocolTest {

    private fun hexBytes(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

    // ------------------------------------------------------------------
    // 命令编码（原 App hex2Byte 行为）
    // ------------------------------------------------------------------

    @Test
    fun `encodeCommand_single_two_char`() {
        assertTrue(ScaleProtocol.encodeCommand("A5").contentEquals(hexBytes(0xA5)))
    }

    @Test
    fun `encodeCommand_single_four_char_expands to two bytes`() {
        // "A15A" → A1 5A（每参数按字符对展开）
        assertTrue(ScaleProtocol.encodeCommand("A15A").contentEquals(hexBytes(0xA1, 0x5A)))
    }

    @Test
    fun `encodeCommand_time_sync full frame`() {
        // FAF8 + 逆序时间：这里用已知 4 字节 00 01 02 03 验证拼接与逆序
        val cmd = "FAF8" + "00010203"
        assertTrue(
            ScaleProtocol.encodeCommand(cmd).contentEquals(
                hexBytes(0xFA, 0xF8, 0x00, 0x01, 0x02, 0x03)
            )
        )
    }

    @Test
    fun `encodeCommand_c0_command decimal to hex on params 2 and 3`() {
        // C0 09 25 30 01 → C0 09 19 1E 01（25→0x19，30→0x1E）
        assertTrue(
            ScaleProtocol.encodeCommand("C0", "09", "25", "30", "01")
                .contentEquals(hexBytes(0xC0, 0x09, 0x19, 0x1E, 0x01))
        )
    }

    @Test
    fun `encodeCommand_single_char padded`() {
        assertTrue(ScaleProtocol.encodeCommand("5").contentEquals(hexBytes(0x05)))
    }

    // ------------------------------------------------------------------
    // 时间同步
    // ------------------------------------------------------------------

    @Test
    fun `timeSyncCommand reverses bytes and offsets epoch`() {
        // nowEpochSec = 946656000 + 0x01020304 = 2000-01-01T00:00:00Z + 16909060 s
        val now = 946656000L + 0x01020304L
        // diff = 0x01020304，逆序 → 04030201
        assertEquals("FAF8" + "04030201", ScaleProtocol.timeSyncCommand(now))
    }

    @Test
    fun `timeSyncCommand pads to 8 hex digits`() {
        // diff = 0x0000007B（123 秒），hex "7B" → 补零 "0000007B"，逆序 "7B000000"
        val now = 946656000L + 0x7B
        assertEquals("FAF8" + "7B000000", ScaleProtocol.timeSyncCommand(now))
    }

    // ------------------------------------------------------------------
    // D2 实时重量帧
    // ------------------------------------------------------------------

    @Test
    fun `parseD2 weight from das2 das3`() {
        // D2 01 02 BC → weight = 0x02BC / 10 = 70.0
        val das = arrayOf("D2", "01", "02", "BC")
        val (weight, finished) = ScaleProtocol.parseD2(das)
        assertEquals(70.0, weight, 1e-9)
        assertEquals(false, finished)
    }

    @Test
    fun `parseD2 das1 zero means finished`() {
        val das = arrayOf("D2", "00", "00", "00")
        val (weight, finished) = ScaleProtocol.parseD2(das)
        assertEquals(0.0, weight, 1e-9)
        assertEquals(true, finished)
    }

    // ------------------------------------------------------------------
    // D0/D1 完整测量两帧
    // ------------------------------------------------------------------

    @Test
    fun `parseFirstFrame extracts timestamp weight fat water bmi`() {
        // 时间戳：das[7..4] 逆序 + 946656000
        // 设 das[7]=01 das[6]=02 das[5]=03 das[4]=04 → 0x01020304 = 16909060
        // weight = das[8]+das[9] = 02BC → 700 → 70.0 kg
        // fat = das[10]+das[11] = 0190 → 400 → 40.0%
        // water = das[12]+das[13] = 03E8 → 1000 → 100.0%
        val das = arrayOf(
            "D0", "01", "01", "00", "04", "03", "02", "01",
            "02", "BC", "01", "90", "03", "E8"
        )
        val first = ScaleProtocol.parseFirstFrame(das, heightCm = 170, age = 30)
        assertEquals(946656000L + 0x01020304L, first.timestamp)
        assertEquals(70.0, first.weight, 1e-9)
        assertEquals(40.0, first.fat, 1e-9)
        assertEquals(100.0, first.water, 1e-9)
        // BMI = 70 / (1.7*1.7) = 24.2
        assertEquals(24.2, first.bmi, 0.01)
        // 基础代谢（Katch-McArdle 变体）：fat=40% weight=70 → 370+21.6*0.6*70 = 1277
        assertEquals(1277, first.metabolism)
    }

    @Test
    fun `parseFirstFrame metabolism formula`() {
        assertEquals(1277, ScaleProtocol.getEuropeanStandard(40.0, 70.0))
        assertEquals(0, ScaleProtocol.getEuropeanStandard(0.0, 70.0))
    }

    @Test
    fun `mergeMeasurement second frame completes record`() {
        val das = arrayOf(
            "D0", "01", "00", "00", "04", "03", "02", "01",
            "02", "BC", "01", "90", "03", "E8"
        )
        val first = ScaleProtocol.parseFirstFrame(das, heightCm = 170, age = 30)
        // 第二帧：muscle das4+das5，bone das6+das7，metabolism das8+das9，skinfat das10+das11，offalfat das12，bodyage das13
        val second = arrayOf(
            "D0", "01", "00", "00",
            "03", "E8",          // muscle = 0x03E8 = 1000 → 100.0 kg
            "00", "C8",          // bone = 0x00C8 = 200 → 20.0 kg
            "04", "B0",          // metabolism = 0x04B0 = 1200
            "01", "2C",          // skinfat = 0x012C = 300 → 30.0%
            "05",                // offalfat = 5
            "2A"                 // bodyage = 42
        )
        val m = ScaleProtocol.mergeMeasurement(first, second, age = 30)
        assertEquals(100.0, m.muscle, 1e-9)
        assertEquals(20.0, m.bone, 1e-9)
        assertEquals(1200, m.metabolism)
        assertEquals(30.0, m.skinfat, 1e-9)
        assertEquals(5, m.offalfat)
        assertEquals(42, m.bodyage)
        assertEquals(first.timestamp, m.timestamp)
    }

    @Test
    fun `frame classification`() {
        assertTrue(ScaleProtocol.isD0FirstFrame(arrayOf("D0", "01", "01")))
        assertTrue(!ScaleProtocol.isD0FirstFrame(arrayOf("D0", "01", "00")))
        assertTrue(ScaleProtocol.isD1FirstFrame(arrayOf("D1", "01", "03")))  // 奇数
        assertTrue(!ScaleProtocol.isD1FirstFrame(arrayOf("D1", "01", "02"))) // 偶数
        assertTrue(ScaleProtocol.isMeasurementFrame(arrayOf("D0", "01", "01")))
        assertTrue(ScaleProtocol.isMeasurementFrame(arrayOf("D1", "01", "01")))
        assertTrue(!ScaleProtocol.isMeasurementFrame(arrayOf("D2", "01", "02", "BC")))
    }

    // ------------------------------------------------------------------
    // 健康评分（原 App GlobalMethod.getRyfitIndex）
    // ------------------------------------------------------------------

    @Test
    fun `ryfitIndex formula`() {
        // 全 0 → 0
        assertEquals(0.0, ScaleProtocol.getRyfitIndex(0.0, 0.0, 0.0, 30), 1e-9)
        // 内脏脂肪 5、BMI 22、身体年龄 30、实际年龄 30：
        //   soffalafat2 = 100 - 20 = 80
        //   bmip = 2 → (0,4] → bmi2 = 100 - 6 = 94
        //   ryfit = 0.2*80 + 0.3*94 + 0.5*(85 - 0) = 16 + 28.2 + 42.5 = 86.7
        val r = ScaleProtocol.getRyfitIndex(5.0, 22.0, 30.0, 30)
        assertEquals(86.7, r, 1e-9)
    }

    // ------------------------------------------------------------------
    // hex 转换
    // ------------------------------------------------------------------

    @Test
    fun `bytesToHexString matches broadcast format`() {
        val bytes = hexBytes(0xFB, 0xF8)
        assertEquals("FB F8", ScaleProtocol.bytesToHexString(bytes))
    }

    @Test
    fun `hexStringToBytes round trip`() {
        val hex = "FA F8 00 01 02 03"
        assertTrue(ScaleProtocol.hexStringToBytes(hex).contentEquals(hexBytes(0xFA, 0xF8, 0x00, 0x01, 0x02, 0x03)))
    }
}
