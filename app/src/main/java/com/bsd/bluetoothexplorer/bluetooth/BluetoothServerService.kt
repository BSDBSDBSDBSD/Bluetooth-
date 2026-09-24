package com.bsd.bluetoothexplorer.bluetooth

import android.app.*
import android.bluetooth.*
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bsd.bluetoothexplorer.model.BtCommand
import com.bsd.bluetoothexplorer.model.BtResponse
import com.bsd.bluetoothexplorer.root.RootManager
import com.bsd.bluetoothexplorer.ui.MainActivity
import com.bsd.bluetoothexplorer.wifi.WifiDirectManager
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.util.UUID

class BluetoothServerService : Service() {

    companion object {
        const val TAG = "BTServerService"
        val BT_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        const val CHANNEL_ID = "bt_explorer_channel"
        const val NOTIF_ID = 1001
        const val ACTION_START_SERVER = "START_SERVER"
        const val ACTION_STOP_SERVER = "STOP_SERVER"
        const val WIFI_TCP_PORT = WifiDirectManager.SERVER_PORT
        var isRunning = false
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: BluetoothServerSocket? = null
    private var tcpServerSocket: ServerSocket? = null
    private var wifiDirect: WifiDirectManager? = null
    private var useRoot = false

    override fun onCreate() {
        super.onCreate()
        RootManager.init()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVER -> {
                useRoot = intent.getBooleanExtra("use_root", false)
                // Start foreground with its declared type; never let this crash the app.
                try {
                    androidx.core.app.ServiceCompat.startForeground(
                        this, NOTIF_ID, buildNotification("ממתין לחיבור..."),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "startForeground failed", e)
                    try { startForeground(NOTIF_ID, buildNotification("ממתין לחיבור...")) } catch (e2: Exception) {
                        Log.e(TAG, "plain startForeground failed too", e2)
                        stopSelf(); isRunning = false; return START_NOT_STICKY
                    }
                }
                startServer()          // Bluetooth
                startWifiTcpServer()   // WiFi (TCP)
                startWifiDirectHost()  // direct Wi-Fi group, no router
                isRunning = true
            }
            ACTION_STOP_SERVER -> {
                stopServer()
                stopSelf()
                isRunning = false
            }
        }
        return START_STICKY
    }

    // ---- Bluetooth server ----
    private fun startServer() {
        scope.launch {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@launch
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("BTExplorer", BT_UUID)
                Log.d(TAG, "BT server listening on UUID: $BT_UUID")
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    val name = try { socket.remoteDevice.name ?: socket.remoteDevice.address } catch (e: Exception) { "מכשיר" }
                    Log.d(TAG, "BT client connected: $name")
                    updateNotification("מחובר (Bluetooth): $name")
                    launch { handleStreams(socket.inputStream, socket.outputStream) { socket.close() } }
                }
            } catch (e: Exception) {
                Log.e(TAG, "BT server error: ${e.message}")
            }
        }
    }

    // ---- WiFi TCP server ----
    private fun startWifiTcpServer() {
        scope.launch {
            try {
                tcpServerSocket = ServerSocket(WIFI_TCP_PORT)
                Log.d(TAG, "TCP server listening on port $WIFI_TCP_PORT")
                while (isActive) {
                    val socket = try { tcpServerSocket?.accept() } catch (e: Exception) { break } ?: break
                    val addr = socket.inetAddress.hostAddress ?: "unknown"
                    Log.d(TAG, "WiFi client connected: $addr")
                    updateNotification("מחובר (WiFi): $addr")
                    launch { handleStreams(socket.getInputStream(), socket.getOutputStream()) { socket.close() } }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TCP server error: ${e.message}")
            }
        }
    }

    // ---- Wi-Fi Direct host (direct connection, no router) ----
    private fun startWifiDirectHost() {
        try {
            val wd = WifiDirectManager(this)
            wifiDirect = wd
            wd.register()
            wd.createGroup { ok, err ->
                if (ok) updateNotification("שרת WiFi ישיר פעיל — התחבר מהמכשיר השני")
                else Log.w(TAG, "Wi-Fi Direct host: $err")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wi-Fi Direct host failed: ${e.message}")
        }
    }

    // ---- Unified command loop (Bluetooth or TCP) ----
    private fun handleStreams(rawIn: InputStream, rawOut: OutputStream, onDone: () -> Unit) {
        val input = DataInputStream(BufferedInputStream(rawIn))
        val output = DataOutputStream(BufferedOutputStream(rawOut))
        try {
            while (true) {
                val len = input.readInt()
                if (len <= 0 || len > 4 * 1024 * 1024) break
                val jsonBytes = ByteArray(len)
                input.readFully(jsonBytes)
                val command = try { BtCommand.fromJson(String(jsonBytes)) } catch (e: Exception) { continue }
                Log.d(TAG, "cmd: ${command.cmd} path=${command.path}")
                when (command.cmd) {
                    "LIST_DIR" -> handleListDir(command, output)
                    "GET_FILE" -> handleGetFile(command, output)
                    "DELETE"   -> handleDelete(command, output)
                    "RENAME"   -> handleRename(command, output)
                    "MKDIR"    -> handleMkdir(command, output)
                    "ROOT_STATUS" -> handleRootStatus(output)
                    else -> sendJson(output, BtResponse(false, "Unknown command").toJson())
                }
            }
        } catch (e: EOFException) {
            Log.d(TAG, "Client disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Client error: ${e.message}")
        } finally {
            try { onDone() } catch (_: Exception) {}
            updateNotification("ממתין לחיבור...")
        }
    }

    private fun handleListDir(cmd: BtCommand, output: DataOutputStream) {
        val files = RootManager.listDir(cmd.path, cmd.useRoot && useRoot)
        val response = BtResponse(
            success = true,
            files = files,
            isRoot = RootManager.isRootAvailable
        )
        sendJson(output, response.toJson())
    }

    private fun handleGetFile(cmd: BtCommand, output: DataOutputStream) {
        val fileSize = RootManager.getFileSize(cmd.path, cmd.useRoot && useRoot)
        if (fileSize == 0L) {
            sendJson(output, BtResponse(false, "File not found or empty").toJson())
            return
        }
        sendJson(output, BtResponse(success = true, fileSize = fileSize).toJson())
        val stream = RootManager.openFile(cmd.path, cmd.useRoot && useRoot)
        stream?.use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            output.flush()
        }
    }

    private fun handleDelete(cmd: BtCommand, output: DataOutputStream) {
        val success = RootManager.delete(cmd.path, cmd.useRoot && useRoot)
        sendJson(output, BtResponse(success).toJson())
    }

    private fun handleRename(cmd: BtCommand, output: DataOutputStream) {
        val success = RootManager.rename(cmd.path, cmd.newPath, cmd.useRoot && useRoot)
        sendJson(output, BtResponse(success).toJson())
    }

    private fun handleMkdir(cmd: BtCommand, output: DataOutputStream) {
        val success = RootManager.mkdir(cmd.path, cmd.useRoot && useRoot)
        sendJson(output, BtResponse(success).toJson())
    }

    private fun handleRootStatus(output: DataOutputStream) {
        sendJson(output, BtResponse(success = true, isRoot = RootManager.isRootAvailable).toJson())
    }

    private fun sendJson(output: DataOutputStream, json: String) {
        val bytes = json.toByteArray()
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    private fun stopServer() {
        scope.cancel()
        try { wifiDirect?.disconnect() } catch (_: Exception) {}
        try { wifiDirect?.unregister() } catch (_: Exception) {}
        wifiDirect = null
        try { serverSocket?.close() } catch (_: Exception) {}
        try { tcpServerSocket?.close() } catch (_: Exception) {}
        isRunning = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "BT Explorer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BT Explorer - שרת פעיל")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification(text))
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}
