package com.bsd.bluetoothexplorer.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bsd.bluetoothexplorer.R
import com.bsd.bluetoothexplorer.bluetooth.BluetoothServerService
import com.bsd.bluetoothexplorer.root.RootManager

class MainActivity : AppCompatActivity() {

    private lateinit var btnStartServer: Button
    private lateinit var btnConnect: Button
    private lateinit var switchRoot: Switch
    private lateinit var tvStatus: TextView
    private lateinit var tvRootStatus: TextView

    private val PERM_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStartServer = findViewById(R.id.btnStartServer)
        btnConnect     = findViewById(R.id.btnConnect)
        switchRoot     = findViewById(R.id.switchRoot)
        tvStatus       = findViewById(R.id.tvStatus)
        tvRootStatus   = findViewById(R.id.tvRootStatus)

        RootManager.init()
        updateRootStatus()
        requestAllPermissions()

        btnStartServer.setOnClickListener {
            if (BluetoothServerService.isRunning) {
                stopServer()
            } else {
                startServer()
            }
        }

        btnConnect.setOnClickListener {
            startActivity(Intent(this, DeviceScanActivity::class.java))
        }
    }

    private fun updateRootStatus() {
        if (RootManager.isRootAvailable) {
            tvRootStatus.text = "✅ Root זמין"
            tvRootStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            tvRootStatus.text = "⚠️ ללא Root - גישה מוגבלת לאחסון חיצוני"
            tvRootStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
        }
    }

    private fun startServer() {
        if (BluetoothAdapter.getDefaultAdapter()?.isEnabled == false) {
            Toast.makeText(this, "הפעל Bluetooth קודם", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, BluetoothServerService::class.java).apply {
            action = BluetoothServerService.ACTION_START_SERVER
            putExtra("use_root", switchRoot.isChecked)
        }
        startForegroundService(intent)
        btnStartServer.text = "עצור שרת"
        tvStatus.text = "שרת פעיל - ממתין לחיבורים..."
    }

    private fun stopServer() {
        val intent = Intent(this, BluetoothServerService::class.java).apply {
            action = BluetoothServerService.ACTION_STOP_SERVER
        }
        startService(intent)
        btnStartServer.text = "הפעל שרת"
        tvStatus.text = "שרת כבוי"
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val denied = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, denied.toTypedArray(), PERM_REQUEST)
        }

        // MANAGE_EXTERNAL_STORAGE - דרישה נפרדת ב-Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle("הרשאת גישה לקבצים")
                    .setMessage("האפליקציה צריכה הרשאת 'ניהול כל הקבצים' כדי לחשוף אחסון מלא")
                    .setPositiveButton("פתח הגדרות") { _, _ ->
                        startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")))
                    }
                    .setNegativeButton("אחר כך", null)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        btnStartServer.text = if (BluetoothServerService.isRunning) "עצור שרת" else "הפעל שרת"
        tvStatus.text = if (BluetoothServerService.isRunning) "שרת פעיל" else "שרת כבוי"
    }
}
