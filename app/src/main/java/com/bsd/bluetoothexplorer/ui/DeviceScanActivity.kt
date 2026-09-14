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
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHint: TextView
    private val client = BluetoothClient()
    private val devices = mutableListOf<BluetoothDevice>()
    private lateinit var listAdapter: DeviceListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_scan)

        supportActionBar?.title = "בחר מכשיר"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listView    = findViewById(R.id.listDevices)
        progressBar = findViewById(R.id.progressBar)
        tvHint      = findViewById(R.id.tvHint)

        listAdapter = DeviceListAdapter(this, devices)
        listView.adapter = listAdapter
        listView.divider = null

        loadPairedDevices()
        listView.setOnItemClickListener { _, _, position, _ ->
            connectToDevice(devices[position])
        }
    }

    private fun loadPairedDevices() {
        val paired = try {
            BluetoothAdapter.getDefaultAdapter()?.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            Toast.makeText(this, "נדרשת הרשאת Bluetooth", Toast.LENGTH_LONG).show()
            emptySet()
        }
        devices.clear()
        devices.addAll(paired)
        listAdapter.notifyDataSetChanged()
        tvHint.text = if (devices.isEmpty())
            "לא נמצאו מכשירים מזווגים.\nזווג מכשיר ב-Bluetooth ואז חזור."
        else
            "${devices.size} מכשירים — בחר מכשיר להתחברות:"
    }

    private fun connectToDevice(device: BluetoothDevice) {
        progressBar.visibility = View.VISIBLE
        val name = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
        tvHint.text = "מתחבר אל $name..."
        listView.isEnabled = false

        lifecycleScope.launch {
            val success = try { client.connect(device) } catch (e: Exception) { false }
            runOnUiThread {
                progressBar.visibility = View.GONE
                listView.isEnabled = true
                if (success) {
                    ClientHolder.client = client
                    ClientHolder.remoteDeviceName = name
                    startActivity(Intent(this@DeviceScanActivity, FileExplorerActivity::class.java))
                } else {
                    tvHint.text = "❌ חיבור נכשל\nוודא שהאפליקציה פועלת במצב שרת במכשיר השני"
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

// Adapter עם כתב לבן על רקע כהה
class DeviceListAdapter(
    private val ctx: Context,
    private val devices: List<BluetoothDevice>
) : BaseAdapter() {
    override fun getCount() = devices.size
    override fun getItem(pos: Int) = devices[pos]
    override fun getItemId(pos: Int) = pos.toLong()

    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(ctx)
            .inflate(R.layout.item_device, parent, false)
        val device = devices[pos]
        view.findViewById<TextView>(R.id.tvDeviceName).apply {
            text = try { device.name ?: "מכשיר לא ידוע" } catch (e: SecurityException) { "מכשיר" }
            setTextColor(0xFFFFFFFF.toInt())
        }
        view.findViewById<TextView>(R.id.tvDeviceAddr).apply {
            text = device.address
            setTextColor(0xFF888888.toInt())
        }
        return view
    }
}
