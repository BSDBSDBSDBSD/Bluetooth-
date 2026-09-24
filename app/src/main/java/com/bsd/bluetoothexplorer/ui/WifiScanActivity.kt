package com.bsd.bluetoothexplorer.ui

import android.net.wifi.p2p.WifiP2pDevice
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bsd.bluetoothexplorer.R
import com.bsd.bluetoothexplorer.bluetooth.BluetoothClient
import com.bsd.bluetoothexplorer.wifi.WifiDirectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Socket

class WifiScanActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var btnScan: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHint: TextView
    private lateinit var tvMyIp: TextView

    private val wifiManager by lazy { WifiDirectManager(this) }
    private val peers = mutableListOf<WifiP2pDevice>()
    private lateinit var peerAdapter: WifiPeerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_scan)

        supportActionBar?.title = "חיפוש WiFi Direct"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listView    = findViewById(R.id.listDevices)
        btnScan     = findViewById(R.id.btnScan)
        progressBar = findViewById(R.id.progressBar)
        tvHint      = findViewById(R.id.tvHint)
        tvMyIp      = findViewById(R.id.tvMyIp)

        peerAdapter = WifiPeerAdapter(this, peers)
        listView.adapter = peerAdapter
        listView.divider = null

        wifiManager.register()
        wifiManager.onPeersChanged = { newPeers ->
            runOnUiThread {
                progressBar.visibility = View.GONE
                peers.clear()
                peers.addAll(newPeers)
                peerAdapter.notifyDataSetChanged()
                tvHint.text = if (newPeers.isEmpty())
                    "לא נמצאו מכשירים — ודא שהצד השני גם בחיפוש"
                else
                    "${newPeers.size} מכשירים — בחר מכשיר:"
            }
        }

        wifiManager.onConnectionChanged = { connected, info ->
            runOnUiThread {
                if (connected && info != null) {
                    val serverIp = if (info.isGroupOwner) {
                        "192.168.49.1"
                    } else {
                        info.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
                    }
                    tvHint.text = "✅ מחובר! IP שרת: $serverIp"
                    connectToWifiServer(serverIp)
                }
            }
        }

        tvMyIp.text = "IP שלי: בדיקה..."
        Handler(Looper.getMainLooper()).postDelayed({
            tvMyIp.text = "IP שלי: ${wifiManager.getLocalIp()}"
        }, 500)

        btnScan.setOnClickListener { startDiscovery() }
        startDiscovery()

        listView.setOnItemClickListener { _, _, pos, _ ->
            if (pos < peers.size) connectToPeer(peers[pos])
        }
    }

    private fun startDiscovery() {
        progressBar.visibility = View.VISIBLE
        tvHint.text = "מחפש מכשירים..."
        wifiManager.discoverPeers { success, error ->
            runOnUiThread {
                if (!success) {
                    progressBar.visibility = View.GONE
                    tvHint.text = "שגיאה בחיפוש: $error\nוודא ש-WiFi מופעל"
                }
            }
        }
    }

    private fun connectToPeer(device: WifiP2pDevice) {
        progressBar.visibility = View.VISIBLE
        tvHint.text = "מתחבר אל ${device.deviceName}..."
        wifiManager.connect(device) { success ->
            runOnUiThread {
                if (!success) {
                    progressBar.visibility = View.GONE
                    tvHint.text = "חיבור נכשל — נסה שוב"
                }
                // אם הצליח - onConnectionChanged יתפוס
            }
        }
    }

    private fun connectToWifiServer(serverIp: String) {
        progressBar.visibility = View.VISIBLE
        tvHint.text = "מתחבר לשרת $serverIp..."
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    socket.connect(java.net.InetSocketAddress(serverIp, WifiDirectManager.SERVER_PORT), 5000)
                    val client = BluetoothClient()
                    client.connectWithSocket(socket)
                    ClientHolder.client = client
                    ClientHolder.remoteDeviceName = "WiFi Direct ($serverIp)"
                    true
                } catch (e: Exception) {
                    false
                }
            }
            progressBar.visibility = View.GONE
            if (success) {
                startActivity(android.content.Intent(this@WifiScanActivity, FileExplorerActivity::class.java))
                finish()
            } else {
                tvHint.text = "❌ לא ניתן להתחבר לשרת\nוודא שהאפליקציה פועלת במצב שרת במכשיר השני"
            }
        }
    }

    override fun onDestroy() {
        wifiManager.unregister()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

class WifiPeerAdapter(
    private val ctx: android.content.Context,
    private val peers: List<WifiP2pDevice>
) : BaseAdapter() {
    override fun getCount() = peers.size
    override fun getItem(pos: Int) = peers[pos]
    override fun getItemId(pos: Int) = pos.toLong()
    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(ctx)
            .inflate(R.layout.item_device, parent, false)
        val device = peers[pos]
        view.findViewById<TextView>(R.id.tvDeviceName).apply {
            text = device.deviceName.ifBlank { "WiFi Device" }
            setTextColor(0xFFFFFFFF.toInt())
        }
        view.findViewById<TextView>(R.id.tvDeviceAddr).apply {
            text = "WiFi Direct • ${device.deviceAddress}"
            setTextColor(0xFF4CAF50.toInt())
        }
        view.background = ctx.getDrawable(R.drawable.item_device_bg)
        return view
    }
}
