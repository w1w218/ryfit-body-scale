package com.wcgarden.bodyscale

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.wcgarden.bodyscale.ble.ScaleManager
import com.wcgarden.bodyscale.ble.ScaleProtocol
import com.wcgarden.bodyscale.data.DataJudge
import com.wcgarden.bodyscale.data.DataMaintenance
import com.wcgarden.bodyscale.data.MeasurementDb
import com.wcgarden.bodyscale.data.MeasurementRecord
import com.wcgarden.bodyscale.data.UserPrefs
import com.wcgarden.bodyscale.view.IndicatorBarView
import com.wcgarden.bodyscale.view.MetricListHelper
import java.util.Locale

class MainActivity : AppCompatActivity(), ScaleManager.Listener {

    private lateinit var scaleManager: ScaleManager
    private lateinit var db: MeasurementDb
    private lateinit var prefs: UserPrefs

    private lateinit var tvState: TextView
    private lateinit var tvDevice: TextView
    private lateinit var tvWeight: TextView
    private lateinit var tvPrompt: TextView
    private lateinit var metricList: LinearLayout

    private var metricRows: List<IndicatorBarView> = emptyList()
    private var measuring = false
    // 记录落地后锁定 D2 实时帧：避免秤端的尾帧/回声帧把已刷新的结果显示回跳
    private var measurementDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scaleManager = ScaleManager(this, this)
        db = MeasurementDb(this)
        prefs = UserPrefs(this)

        tvState = findViewById(R.id.tvState)
        tvDevice = findViewById(R.id.tvDevice)
        tvWeight = findViewById(R.id.tvWeight)
        tvPrompt = findViewById(R.id.tvPrompt)
        metricList = findViewById(R.id.metricList)

        findViewById<TextView>(R.id.btnScan).setOnClickListener {
            ensurePermissions { scaleManager.startScan() }
        }
        findViewById<TextView>(R.id.btnDisconnect).setOnClickListener {
            scaleManager.disconnect()
        }
        findViewById<TextView>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (prefs.deviceMac.isNotEmpty()) {
            tvDevice.text = "上次设备：${prefs.deviceMac}"
        }
        // 升级后一次性清理早期版本遗留的演示数据（真机实测记录不受影响）
        DataMaintenance.purgeLegacyDemoData(this, db)
        metricRows = MetricListHelper.build(this, metricList, prefs)
        refreshFromDb()
    }

    /** 用库中最新一条记录渲染主页指标；无记录时回到空态 */
    private fun refreshFromDb() {
        val latest = db.queryAll().firstOrNull()
        MetricListHelper.bind(
            metricRows,
            latest?.let {
                ScaleProtocol.Measurement(
                    timestamp = it.timestamp, checkdate = it.checkdate,
                    weight = it.weight, bmi = it.bmi, fat = it.fat, water = it.water,
                    muscle = it.muscle, bone = it.bone, metabolism = it.metabolism,
                    skinfat = it.skinfat, offalfat = it.offalfat,
                    bodyage = it.bodyage, ryfit = it.ryfit
                )
            },
            prefs
        )
        if (latest != null) {
            tvWeight.text = String.format(Locale.US, "%.1f", latest.weight)
            tvPrompt.text = "最近记录 ${latest.checkdate.take(16)}"
        } else {
            tvWeight.text = "--"
            tvPrompt.text = getString(R.string.prompt_press_scan)
        }
    }

    override fun onResume() {
        super.onResume()
        // 从设置页清空数据后返回要立即反映；测量进行中不打断实时读数
        if (!measuring) refreshFromDb()
    }

    // ------------------------------------------------------------------
    // ScaleManager.Listener
    // ------------------------------------------------------------------

    override fun onScanResult(deviceName: String, address: String) {
        tvDevice.text = "设备：$deviceName（$address）"
        prefs.deviceMac = address
    }

    override fun onStateChanged(state: ScaleManager.State) {
        val (text, color) = when (state) {
            ScaleManager.State.IDLE -> getString(R.string.status_idle) to R.color.text_secondary
            ScaleManager.State.SCANNING -> getString(R.string.status_scanning) to R.color.color_accent
            ScaleManager.State.CONNECTING -> getString(R.string.status_connecting) to R.color.color_accent
            ScaleManager.State.CONNECTED -> getString(R.string.status_connected) to R.color.color_primary
        }
        tvState.text = text
        tvState.setTextColor(ContextCompat.getColor(this, color))
        when (state) {
            ScaleManager.State.SCANNING -> {
                measurementDone = false // 新一轮测量开始，解锁实时显示
                tvPrompt.text = getString(R.string.prompt_press_scan)
            }
            ScaleManager.State.IDLE -> tvPrompt.text = getString(R.string.prompt_press_scan)
            ScaleManager.State.CONNECTED -> tvPrompt.text = getString(R.string.prompt_waiting)
            else -> {}
        }
    }

    override fun onLiveWeight(weightKg: Double, finished: Boolean) {
        // 完整记录已落库：忽略后续 D2 实时帧，主页保持显示落库的准确值
        if (measurementDone) return
        measuring = !finished && weightKg > 0
        tvWeight.text = String.format(Locale.US, "%.1f", weightKg)
        if (finished) {
            tvPrompt.text = "测量完成"
        } else if (weightKg > 0) {
            tvPrompt.text = "测量中…"
        }
    }

    override fun onMeasurement(m: ScaleProtocol.Measurement) {
        measurementDone = true
        measuring = false
        tvWeight.text = String.format(Locale.US, "%.1f", m.weight)
        tvPrompt.text = "已保存 ${m.checkdate}"
        MetricListHelper.bind(metricRows, m, prefs)

        db.insert(
            MeasurementRecord(
                timestamp = m.timestamp,
                checkdate = m.checkdate,
                weight = m.weight,
                bmi = m.bmi,
                fat = m.fat,
                skinfat = m.skinfat,
                offalfat = m.offalfat,
                muscle = m.muscle,
                metabolism = m.metabolism,
                water = m.water,
                bone = m.bone,
                bodyage = m.bodyage,
                ryfit = m.ryfit,
                devcode = ""
            )
        )
    }

    override fun onLog(message: String) {
        if (tvPrompt.text != getString(R.string.prompt_press_scan)) {
            tvPrompt.text = message
        }
    }

    // ------------------------------------------------------------------
    // 权限
    // ------------------------------------------------------------------

    private fun ensurePermissions(onGranted: () -> Unit) {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.BLUETOOTH_SCAN
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (needed.isEmpty()) {
            onGranted()
        } else {
            pendingPermissionAction = onGranted
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    private var pendingPermissionAction: (() -> Unit)? = null

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                pendingPermissionAction?.invoke()
            } else {
                Toast.makeText(this, "需要蓝牙权限才能连接体脂秤", Toast.LENGTH_SHORT).show()
            }
            pendingPermissionAction = null
        }
    }

    override fun onDestroy() {
        scaleManager.disconnect()
        super.onDestroy()
    }

    companion object {
        private const val REQ_PERMISSIONS = 100
    }
}
