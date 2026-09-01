package com.bsd.bluetoothexplorer.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bsd.bluetoothexplorer.R
import com.bsd.bluetoothexplorer.bluetooth.BluetoothClient
import kotlinx.coroutines.launch

class DeviceScanActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHint: TextView
    private val client = BluetoothClient()
    private val devices = mutableListOf<BluetoothDevice>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_scan)

        listView    = findViewById(R.id.listDevices)
        progressBar = findViewById(R.id.progressBar)
        tvHint      = findViewById(R.id.tvHint)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        loadPairedDevices()

        listView.setOnItemClickListener { _, _, position, _ ->
            connectToDevice(devices[position])
        }
    }

    private fun loadPairedDevices() {
        val paired = BluetoothAdapter.getDefaultAdapter()?.bondedDevices ?: emptySet()
        devices.clear()
        devices.addAll(paired)
        adapter.clear()

        if (devices.isEmpty()) {
            tvHint.text = "אין מכשירים מזווגים.\nזווג מכשיר ב-Bluetooth של המערכת ואז חזור."
        } else {
            tvHint.text = "בחר מכשיר להתחברות:"
            devices.forEach { d ->
                adapter.add("${d.name ?: "Unknown"}\n${d.address}")
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        progressBar.visibility = android.view.View.VISIBLE
        tvHint.text = "מתחבר ל-${device.name}..."
        listView.isEnabled = false

        lifecycleScope.launch {
            val success = client.connect(device)
            runOnUiThread {
                progressBar.visibility = android.view.View.GONE
                listView.isEnabled = true
                if (success) {
                    // שמירת הclient לפעילות FileExplorer
                    ClientHolder.client = client
                    ClientHolder.remoteDeviceName = device.name ?: device.address
                    val intent = Intent(this@DeviceScanActivity, FileExplorerActivity::class.java)
                    startActivity(intent)
                } else {
                    tvHint.text = "חיבור נכשל - ודא שהאפליקציה רצה במצב שרת במכשיר השני"
                    Toast.makeText(this@DeviceScanActivity, "חיבור נכשל", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

// Singleton פשוט לשמירת ה-client בין Activities
object ClientHolder {
    var client: BluetoothClient? = null
    var remoteDeviceName: String = ""
}
