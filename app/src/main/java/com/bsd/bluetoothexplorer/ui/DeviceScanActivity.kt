package com.bsd.bluetoothexplorer.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bsd.bluetoothexplorer.R
import com.bsd.bluetoothexplorer.bluetooth.BluetoothClient
import kotlinx.coroutines.launch

// Singleton לשמירת החיבור
object ClientHolder {
    var client: BluetoothClient? = null
    var remoteDeviceName: String = ""
}

class DeviceScanActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var btnScan: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHint: TextView
    private val client = BluetoothClient()
    private val pairedDevices = mutableListOf<BluetoothDevice>()
    private val scannedDevices = mutableListOf<BluetoothDevice>()
    private val allDevices = mutableListOf<BluetoothDevice>()
    private lateinit var listAdapter: DeviceListAdapter
    private var isScanning = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val alreadyInList = allDevices.any { d -> d.address == it.address }
                        if (!alreadyInList) {
                            scannedDevices.add(it)
                            allDevices.add(it)
                            listAdapter.notifyDataSetChanged()
                            updateHint()
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isScanning = false
                    progressBar.visibility = View.GONE
                    btnScan.text = "🔍 חפש מכשירים חדשים"
                    btnScan.isEnabled = true
                    updateHint()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_scan)

        supportActionBar?.title = "בחר מכשיר Bluetooth"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listView    = findViewById(R.id.listDevices)
        btnScan     = findViewById(R.id.btnScan)
        progressBar = findViewById(R.id.progressBar)
        tvHint      = findViewById(R.id.tvHint)

        listAdapter = DeviceListAdapter(this, allDevices, pairedDevices)
        listView.adapter = listAdapter
        listView.divider = null

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        try { registerReceiver(receiver, filter) } catch (e: Exception) {}

        loadPairedDevices()

        btnScan.setOnClickListener { startDiscovery() }

        listView.setOnItemClickListener { _, _, position, _ ->
            if (position < allDevices.size) connectToDevice(allDevices[position])
        }
    }

    private fun loadPairedDevices() {
        val paired = try {
            BluetoothAdapter.getDefaultAdapter()?.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            Toast.makeText(this, "נדרשת הרשאת Bluetooth", Toast.LENGTH_LONG).show()
            emptySet()
        }
        pairedDevices.clear()
        pairedDevices.addAll(paired)
        allDevices.clear()
        allDevices.addAll(paired)
        listAdapter.notifyDataSetChanged()
        updateHint()
    }

    private fun startDiscovery() {
        if (isScanning) return
        val bt = BluetoothAdapter.getDefaultAdapter() ?: return
        try {
            if (bt.isDiscovering) bt.cancelDiscovery()
            bt.startDiscovery()
            isScanning = true
            progressBar.visibility = View.VISIBLE
            btnScan.text = "⏳ מחפש..."
            btnScan.isEnabled = false
            tvHint.text = "מחפש מכשירים חדשים..."
        } catch (e: SecurityException) {
            Toast.makeText(this, "נדרשת הרשאת Bluetooth Scan", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateHint() {
        val paired = pairedDevices.size
        val new = scannedDevices.size
        tvHint.text = when {
            allDevices.isEmpty() -> "לא נמצאו מכשירים\nלחץ 'חפש מכשירים' לסריקה"
            else -> "מזווגים: $paired | חדשים: $new\nבחר מכשיר להתחברות:"
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        // עצור סריקה אם פעילה
        try { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() } catch (_: Exception) {}

        progressBar.visibility = View.VISIBLE
        val name = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
        tvHint.text = "מתחבר אל $name..."
        listView.isEnabled = false
        btnScan.isEnabled = false

        lifecycleScope.launch {
            val success = try { client.connect(device) } catch (e: Exception) { false }
            runOnUiThread {
                progressBar.visibility = View.GONE
                listView.isEnabled = true
                btnScan.isEnabled = true
                if (success) {
                    ClientHolder.client = client
                    ClientHolder.remoteDeviceName = name
                    startActivity(Intent(this@DeviceScanActivity, FileExplorerActivity::class.java))
                } else {
                    tvHint.text = "❌ חיבור נכשל אל $name\nוודא שהאפליקציה פועלת במצב שרת"
                }
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        try { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

// Adapter עם כתב לבן — מזווגים ירוק, חדשים אפור
class DeviceListAdapter(
    private val ctx: Context,
    private val devices: List<BluetoothDevice>,
    private val pairedDevices: List<BluetoothDevice>
) : BaseAdapter() {
    override fun getCount() = devices.size
    override fun getItem(pos: Int) = devices[pos]
    override fun getItemId(pos: Int) = pos.toLong()

    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(ctx)
            .inflate(R.layout.item_device, parent, false)
        val device = devices[pos]
        val isPaired = pairedDevices.any { it.address == device.address }

        view.findViewById<TextView>(R.id.tvDeviceName).apply {
            text = try { device.name ?: "מכשיר לא ידוע" } catch (e: SecurityException) { "מכשיר" }
            setTextColor(0xFFFFFFFF.toInt())
        }
        view.findViewById<TextView>(R.id.tvDeviceAddr).apply {
            text = "${device.address} • ${if (isPaired) "מזווג" else "חדש"}"
            setTextColor(if (isPaired) 0xFF4CAF50.toInt() else 0xFF888888.toInt())
        }
        return view
    }
}
