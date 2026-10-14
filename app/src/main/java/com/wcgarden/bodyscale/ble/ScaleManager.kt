package com.wcgarden.bodyscale.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.wcgarden.bodyscale.ble.ScaleProtocol.GUEST_SLOT
import com.wcgarden.bodyscale.ble.ScaleProtocol.NOTIFY_UUID
import com.wcgarden.bodyscale.ble.ScaleProtocol.SERVICE_UUID
import com.wcgarden.bodyscale.ble.ScaleProtocol.bytesToHexString
import com.wcgarden.bodyscale.ble.ScaleProtocol.encodeCommand
import com.wcgarden.bodyscale.ble.ScaleProtocol.isD0FirstFrame
import com.wcgarden.bodyscale.ble.ScaleProtocol.isD1FirstFrame
import com.wcgarden.bodyscale.ble.ScaleProtocol.isMeasurementFrame
import com.wcgarden.bodyscale.ble.ScaleProtocol.mergeMeasurement
import com.wcgarden.bodyscale.ble.ScaleProtocol.parseD2
import com.wcgarden.bodyscale.ble.ScaleProtocol.parseFirstFrame
import com.wcgarden.bodyscale.ble.ScaleProtocol.timeSyncCommand
import com.wcgarden.bodyscale.ble.ScaleProtocol.writeUserCommandParts

/**
 * BLE 连接管理器：扫描（按服务 UUID 0xFFE0 过滤）、连接、订阅 0xFFE1、收发协议帧。
 *
 * 交互状态机（逆向自原 App）：
 *  - 连接成功 → 设备发 F8（时间同步）→ 回 FAF8+时间
 *  - 设备发 FB F8 → 连接握手完成 → 发送 C0（写用户信息，游客槽位 09）
 *  - 测量中设备发 D2（实时重量）/ D0 两帧（完整数据）→ 解析回调
 */
@SuppressLint("MissingPermission")
class ScaleManager(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onScanResult(deviceName: String, address: String)
        fun onStateChanged(state: State)
        fun onLiveWeight(weightKg: Double, finished: Boolean)
        fun onMeasurement(m: ScaleProtocol.Measurement)
        fun onLog(message: String)
    }

    enum class State { IDLE, SCANNING, CONNECTING, CONNECTED }

    private val handler = Handler(Looper.getMainLooper())
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var gatt: BluetoothGatt? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var connectedAddress: String? = null
    private var scanRetries = 0

    // D0/D1 两帧状态：第一帧暂存
    private var pendingFirst: ScaleProtocol.FirstFrame? = null

    val isConnected: Boolean get() = gatt?.device?.address == connectedAddress

    // ------------------------------------------------------------------
    // 扫描
    // ------------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            listener.onScanResult(device.name ?: "(未知设备)", device.address)
            stopScan()
            connect(device)
        }
    }

    fun startScan() {
        val adapter = bluetoothAdapter ?: return
        val scanner = adapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCallback)
        listener.onStateChanged(State.SCANNING)
        listener.onLog("开始扫描体脂秤（服务 UUID ${SERVICE_UUID}）…")
        // 超时重扫
        handler.postDelayed({
            if (gatt == null) {
                stopScan()
                if (scanRetries < 5) {
                    scanRetries++
                    listener.onLog("未找到设备，重试扫描…")
                    startScan()
                } else {
                    listener.onStateChanged(State.IDLE)
                    listener.onLog("扫描超时，请确认体脂秤已开机且在附近")
                }
            }
        }, 10000)
    }

    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    fun disconnect() {
        stopScan()
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        notifyCharacteristic = null
        connectedAddress = null
        pendingFirst = null
        listener.onStateChanged(State.IDLE)
    }

    // ------------------------------------------------------------------
    // 连接
    // ------------------------------------------------------------------

    private fun connect(device: BluetoothDevice) {
        listener.onStateChanged(State.CONNECTING)
        listener.onLog("正在连接 ${device.name ?: device.address} …")
        connectedAddress = device.address
        gatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener.onStateChanged(State.CONNECTED)
                listener.onLog("已连接，发现服务…")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                this@ScaleManager.gatt = null
                listener.onStateChanged(State.IDLE)
                listener.onLog("连接断开")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onLog("服务发现失败（$status）")
                return
            }
            val service: BluetoothGattService? = gatt.getService(SERVICE_UUID)
            if (service == null) {
                listener.onLog("未找到 0xFFE0 服务")
                return
            }
            val characteristic = service.getCharacteristic(NOTIFY_UUID)
            if (characteristic == null) {
                listener.onLog("未找到 0xFFE1 特征")
                return
            }
            notifyCharacteristic = characteristic
            val enabled = gatt.setCharacteristicNotification(characteristic, true)
            // 等待设备主动发 F8 / FB F8
            listener.onLog("等待设备握手…")
            handler.postDelayed({
                // 设备可能不主动发 F8，主动请求时间同步
                writeCommand(encodeCommand(timeSyncCommand()))
            }, 500)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value ?: return
            handleFrame(bytesToHexString(data).split(" ").toTypedArray())
        }
    }

    // ------------------------------------------------------------------
    // 协议处理
    // ------------------------------------------------------------------

    fun writeCommand(bytes: ByteArray) {
        val c = notifyCharacteristic ?: return
        if (!c.setValue(bytes)) return
        gatt?.writeCharacteristic(c)
    }

    private fun handleFrame(das: Array<String>) {
        val first = das[0]
        when {
            // F8：时间同步请求 → 回 FAF8
            first == "F8" -> {
                writeCommand(encodeCommand(timeSyncCommand()))
                listener.onLog("时间同步")
            }
            // FB F8：连接握手完成 → 写用户信息
            first == "FB" && das.getOrNull(1) == "F8" -> {
                listener.onLog("握手成功")
                handler.postDelayed({ sendUserInfo() }, 300)
            }
            // D2：实时重量
            first == "D2" -> {
                val (weight, finished) = parseD2(das)
                listener.onLiveWeight(weight, finished)
            }
            // D0/D1：完整测量两帧
            isMeasurementFrame(das) -> handleMeasurementFrame(das)
            // 其他帧记录日志
            else -> listener.onLog("帧: ${das.joinToString(" ")}")
        }
    }

    private fun handleMeasurementFrame(das: Array<String>) {
        val firstByte = das[0]
        val isFirst = if (firstByte == "D0") isD0FirstFrame(das) else isD1FirstFrame(das)
        if (isFirst) {
            val prefs = context.getSharedPreferences("user", Context.MODE_PRIVATE)
            val height = prefs.getInt("height", 170)
            val age = prefs.getInt("age", 30)
            pendingFirst = parseFirstFrame(das, height, age)
            writeCommand(encodeCommand(ScaleProtocol.ack(firstByte, das)))
            listener.onLog("测量数据（第一帧）")
        } else {
            val firstFrame = pendingFirst
            if (firstFrame == null) {
                listener.onLog("收到第二帧但没有第一帧，忽略")
                return
            }
            val age = context.getSharedPreferences("user", Context.MODE_PRIVATE).getInt("age", 30)
            val m = mergeMeasurement(firstFrame, das, age)
            pendingFirst = null
            writeCommand(encodeCommand(ScaleProtocol.ack(firstByte, das)))
            listener.onMeasurement(m)
        }
    }

    /** 单用户：发送 C0（游客槽位 09）写用户信息 */
    fun sendUserInfo() {
        val prefs = context.getSharedPreferences("user", Context.MODE_PRIVATE)
        val height = prefs.getInt("height", 170)
        val age = prefs.getInt("age", 30)
        val sex = prefs.getInt("sex", 1) // 1=男 0=女
        val parts = writeUserCommandParts(height, age, sex)
        writeCommand(encodeCommand(*parts))
        listener.onLog("已发送用户信息（C0 $GUEST_SLOT 身高${height} 年龄${age} 性别$sex）")
    }
}
