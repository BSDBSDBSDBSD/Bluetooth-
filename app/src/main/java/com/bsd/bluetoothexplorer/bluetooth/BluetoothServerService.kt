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
import kotlinx.coroutines.*
import java.io.*
import java.util.UUID

class BluetoothServerService : Service() {

    companion object {
        const val TAG = "BTServerService"
        val BT_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        const val CHANNEL_ID = "bt_explorer_channel"
        const val NOTIF_ID = 1001
        const val ACTION_START_SERVER = "START_SERVER"
        const val ACTION_STOP_SERVER = "STOP_SERVER"
        var isRunning = false
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: BluetoothServerSocket? = null
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
                startForeground(NOTIF_ID, buildNotification("ממתין לחיבור..."))
                startServer()
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

    private fun startServer() {
        scope.launch {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("BTExplorer", BT_UUID)
                Log.d(TAG, "Server listening on UUID: $BT_UUID")

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    Log.d(TAG, "Client connected: ${socket.remoteDevice.name}")
                    updateNotification("מחובר ל: ${socket.remoteDevice.name}")
                    launch { handleClient(socket) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            }
        }
    }

    private suspend fun handleClient(socket: BluetoothSocket) {
        val input = DataInputStream(BufferedInputStream(socket.inputStream))
        val output = DataOutputStream(BufferedOutputStream(socket.outputStream))

        try {
            while (socket.isConnected) {
                // קריאת פקודה - פרוטוקול: [4 bytes length][json bytes]
                val len = input.readInt()
                val jsonBytes = ByteArray(len)
                input.readFully(jsonBytes)
                val command = BtCommand.fromJson(String(jsonBytes))

                Log.d(TAG, "Received command: ${command.cmd} path=${command.path}")

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
            socket.close()
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

        // שולח תחילה JSON עם הגודל
        sendJson(output, BtResponse(success = true, fileSize = fileSize).toJson())

        // ואז שולח את תוכן הקובץ
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
        serverSocket?.close()
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
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}
