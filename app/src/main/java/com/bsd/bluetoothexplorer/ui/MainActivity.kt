package com.bsd.bluetoothexplorer.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.View
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
    private lateinit var btnConnectWifi: Button
    private lateinit var switchRoot: Switch
    private lateinit var tvStatus: TextView
    private lateinit var tvRootStatus: TextView
    private lateinit var statusDot: View

    private val PERM_REQUEST = 100
    private val STORAGE_PERM_REQUEST = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStartServer  = findViewById(R.id.btnStartServer)
        btnConnect      = findViewById(R.id.btnConnect)
        btnConnectWifi  = findViewById(R.id.btnConnectWifi)
        switchRoot      = findViewById(R.id.switchRoot)
        tvStatus        = findViewById(R.id.tvStatus)
        tvRootStatus    = findViewById(R.id.tvRootStatus)
        statusDot       = findViewById(R.id.statusDot)

        // Root בthread נפרד — לא יגרום לקריסה
        Thread {
            try { RootManager.init() } catch (e: Exception) { e.printStackTrace() }
            runOnUiThread { updateRootStatus() }
        }.start()

        btnStartServer.setOnClickListener {
            if (BluetoothServerService.isRunning) stopServer() else startServer()
        }

        btnConnect.setOnClickListener {
            if (!hasBluetoothPermission()) {
                requestBluetoothPermissions()
                return@setOnClickListener
            }
            startActivity(Intent(this, DeviceScanActivity::class.java))
        }

        btnConnectWifi.setOnClickListener {
            startActivity(Intent(this, WifiScanActivity::class.java))
        }

        // הרשאות Bluetooth מייד
        requestBluetoothPermissions()
        // הרשאות Storage עם דיליי — ימנע קריסה
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDestroyed && !isFinishing) {
                requestStoragePermission()
            }
        }, 800)
    }

    private fun updateRootStatus() {
        if (isDestroyed || isFinishing) return
        if (RootManager.isRootAvailable) {
            tvRootStatus.text = "✅ Root זמין — גישה מלאה למערכת קבצים"
            switchRoot.isEnabled = true
        } else {
            tvRootStatus.text = "⚠️ ללא Root — גישה לאחסון חיצוני בלבד"
            switchRoot.isChecked = false
            switchRoot.isEnabled = false
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun requestBluetoothPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val perms = arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
                val denied = perms.filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }
                if (denied.isNotEmpty()) {
                    ActivityCompat.requestPermissions(this, denied.toTypedArray(), PERM_REQUEST)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERM_REQUEST
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestStoragePermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    AlertDialog.Builder(this)
                        .setTitle("הרשאת גישה לקבצים")
                        .setMessage("לגישה מלאה לאחסון, אשר את הרשאת 'ניהול כל הקבצים'.\nניתן גם לדלג — האפליקציה תעבוד עם גישה חלקית.")
                        .setPositiveButton("אשר") { _, _ ->
                            try {
                                startActivity(Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:$packageName")
                                ))
                            } catch (e: Exception) {
                                try {
                                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                } catch (e2: Exception) { }
                            }
                        }
                        .setNegativeButton("דלג", null)
                        .show()
                }
            } else {
                val perms = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                val denied = perms.filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }
                if (denied.isNotEmpty()) {
                    ActivityCompat.requestPermissions(this, denied.toTypedArray(), STORAGE_PERM_REQUEST)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // אל תעשה כלום שיגרום לקריסה
    }

    private fun startServer() {
        val bt = BluetoothAdapter.getDefaultAdapter()
        if (bt == null || !bt.isEnabled) {
            Toast.makeText(this, "הפעל Bluetooth בהגדרות המכשיר", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, BluetoothServerService::class.java).apply {
            action = BluetoothServerService.ACTION_START_SERVER
            putExtra("use_root", switchRoot.isChecked)
        }
        try {
            startForegroundService(intent)
            setServerUI(true)
        } catch (e: Exception) {
            Toast.makeText(this, "שגיאה בהפעלת השרת: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopServer() {
        try {
            startService(Intent(this, BluetoothServerService::class.java).apply {
                action = BluetoothServerService.ACTION_STOP_SERVER
            })
        } catch (e: Exception) {}
        setServerUI(false)
    }

    private fun setServerUI(running: Boolean) {
        btnStartServer.text = if (running) "עצור שרת" else "הפעל שרת"
        btnStartServer.backgroundTintList = getColorStateList(
            if (running) android.R.color.holo_red_light else R.color.green_primary
        )
        tvStatus.text = if (running) "שרת פעיל — ממתין לחיבורים" else "שרת כבוי"
        statusDot.setBackgroundResource(if (running) R.drawable.dot_green else R.drawable.dot_red)
    }

    override fun onResume() {
        super.onResume()
        setServerUI(BluetoothServerService.isRunning)
    }
}
