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

class DeviceScanActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHint: TextView
    private lateinit var tvTitle: TextView
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
        listView.setOnItemClickListener { _, _, position, _ -> connectToDevice(devices[position]) }
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
            "לא נמצאו מכשירים מזווגים.\nזווג מכשיר ב-Bluetooth של המכשיר ואז חזור."
        else
            "${devices.size} מכשירים מזווגים — בחר מכשיר להתחברות:"
    }

    private fun connectToDevice(device: BluetoothDevice) {
        progressBar.visibility = View.VISIBLE
        tvHint.text = "מתחבר אל ${try { device.name } catch (e: SecurityException) { device.address }}..."
        listView.isEnabled = false

        lifecycleScope.launch {
            val success = try {
                client.connect(device)
            } catch (e: Exception) {
                false
            }

            runOnUiThread {
                progressBar.visibility = View.GONE
                listView.isEnabled = true
                if (success) {
                    ClientHolder.client = client
                    ClientHolder.remoteDeviceName = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
                    startActivity(Intent(this@DeviceScanActivity, FileExplorerActivity::class.java))
                } else {
                    tvHint.text = "❌ חיבור נכשל\nוודא שהאפליקציה פועלת במצב שרת במכשיר השני"
                    Toast.makeText(this@DeviceScanActivity,
                        "חיבור נכשל — האם השרת פועל?", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

// Adapter מותאם עם עיצוב נכון
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
        val tvName = view.findViewById<TextView>(R.id.tvDeviceName)
        val tvAddr = view.findViewById<TextView>(R.id.tvDeviceAddr)
        val ivIcon = view.findViewById<android.widget.ImageView>(R.id.ivDeviceIcon)

        try {
            tvName.text = device.name ?: "מכשיר לא ידוע"
            tvName.setTextColor(0xFFFFFFFF.toInt())
        } catch (e: SecurityException) {
            tvName.text = "מכשיר"
        }
        tvAddr.text = device.address
        tvAddr.setTextColor(0xFF888888.toInt())
        ivIcon.setImageResource(android.R.drawable.stat_sys_data_bluetooth)

        return view
    }
}
