package com.wcgarden.bodyscale

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.wcgarden.bodyscale.data.DemoData
import com.wcgarden.bodyscale.data.MeasurementDb
import com.wcgarden.bodyscale.data.UserPrefs

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        val prefs = UserPrefs(this)
        val radioSex = findViewById<RadioGroup>(R.id.radioSex)
        val editAge = findViewById<EditText>(R.id.editAge)
        val editHeight = findViewById<EditText>(R.id.editHeight)
        val editTarget = findViewById<EditText>(R.id.editTarget)

        radioSex.check(
            if (prefs.sex == 1) R.id.radioMale else R.id.radioFemale
        )
        editAge.setText(prefs.age.toString())
        editHeight.setText(prefs.height.toString())
        editTarget.setText(String.format("%.1f", prefs.targetWeight))

        findViewById<TextView>(R.id.btnSave).setOnClickListener {
            val age = editAge.text.toString().toIntOrNull()
            val height = editHeight.text.toString().toIntOrNull()
            val target = editTarget.text.toString().toDoubleOrNull()
            if (age == null || age < 1 || age > 120) {
                Toast.makeText(this, "请输入有效年龄", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (height == null || height < 80 || height > 250) {
                Toast.makeText(this, "请输入有效身高（80-250cm）", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (target == null || target < 10 || target > 300) {
                Toast.makeText(this, "请输入有效目标体重", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.sex = if (radioSex.checkedRadioButtonId == R.id.radioMale) 1 else 0
            prefs.age = age
            prefs.height = height
            prefs.targetWeight = target
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }

        // ---- 数据管理 ----
        val db = MeasurementDb(this)
        findViewById<TextView>(R.id.btnClearData).setOnClickListener { confirmClearData(db) }

        val btnDemo = findViewById<TextView>(R.id.btnDemoData)
        if (isDebuggable()) {
            btnDemo.visibility = View.VISIBLE
            btnDemo.setOnClickListener { fillDemoData(db) }
        }
    }

    /** 清除全部测量数据（二次确认）；个人资料保留 */
    private fun confirmClearData(db: MeasurementDb) {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_clear_title)
            .setMessage(R.string.settings_clear_message)
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_clear_confirm) { _, _ ->
                val removed = db.clear()
                UserPrefs(this).deviceMac = ""
                Toast.makeText(this, getString(R.string.settings_cleared, removed), Toast.LENGTH_SHORT)
                    .show()
            }
            .show()
    }

    /** 填充演示数据（仅 debug 包）：空库时灌入过去 90 天的模拟记录 */
    private fun fillDemoData(db: MeasurementDb) {
        if (db.queryAll().isNotEmpty()) {
            Toast.makeText(this, R.string.settings_demo_skipped, Toast.LENGTH_SHORT).show()
            return
        }
        DemoData.seedIfEmpty(this, db, UserPrefs(this).height, force = true)
        Toast.makeText(this, getString(R.string.settings_demo_filled, db.queryAll().size), Toast.LENGTH_SHORT)
            .show()
    }

    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
