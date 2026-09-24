package com.bsd.bluetoothexplorer.bluetooth

import android.bluetooth.*
import android.util.Log
import com.bsd.bluetoothexplorer.model.BtCommand
import com.bsd.bluetoothexplorer.model.BtResponse
import com.bsd.bluetoothexplorer.model.FileItem
import kotlinx.coroutines.*
import java.io.*
import java.net.Socket

class BluetoothClient {

    companion object {
        const val TAG = "BTClient"
    }

    private var btSocket: BluetoothSocket? = null
    private var tcpSocket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    var isConnected = false
        private set

    // חיבור Bluetooth רגיל
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()
            val s = device.createRfcommSocketToServiceRecord(BluetoothServerService.BT_UUID)
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            s.connect()
            btSocket = s
            input  = DataInputStream(BufferedInputStream(s.inputStream))
            output = DataOutputStream(BufferedOutputStream(s.outputStream))
            isConnected = true
            Log.d(TAG, "BT Connected to ${device.address}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "BT Connect failed: ${e.message}")
            isConnected = false
            false
        }
    }

    // חיבור דרך TCP (WiFi Direct / WiFi רגיל)
    fun connectWithSocket(socket: Socket): Boolean {
        return try {
            disconnect()
            tcpSocket = socket
            input  = DataInputStream(BufferedInputStream(socket.getInputStream()))
            output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            isConnected = true
            Log.d(TAG, "TCP Connected to ${socket.inetAddress}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "TCP Connect failed: ${e.message}")
            isConnected = false
            false
        }
    }

    fun disconnect() {
        isConnected = false
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { btSocket?.close() } catch (_: Exception) {}
        try { tcpSocket?.close() } catch (_: Exception) {}
        btSocket = null
        tcpSocket = null
    }

    // -------- LIST DIR --------
    suspend fun listDir(path: String, useRoot: Boolean = false): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            val cmd = BtCommand("LIST_DIR", path = path, useRoot = useRoot)
            val response = sendCommand(cmd) ?: return@withContext emptyList()
            response.files
        } catch (e: Exception) {
            Log.e(TAG, "listDir error: ${e.message}")
            emptyList()
        }
    }

    // -------- GET FILE --------
    suspend fun getFile(
        remotePath: String,
        localFile: File,
        useRoot: Boolean = false,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = BtCommand("GET_FILE", path = remotePath, useRoot = useRoot)
            sendRaw(cmd.toJson())

            val response = receiveJson() ?: return@withContext false
            if (!response.success) return@withContext false

            val totalSize = response.fileSize
            localFile.parentFile?.mkdirs()
            FileOutputStream(localFile).use { fos ->
                val buffer = ByteArray(8192)
                var received = 0L
                val inp = input ?: return@withContext false
                while (received < totalSize) {
                    val toRead = minOf(buffer.size.toLong(), totalSize - received).toInt()
                    val bytesRead = inp.read(buffer, 0, toRead)
                    if (bytesRead == -1) break
                    fos.write(buffer, 0, bytesRead)
                    received += bytesRead
                    onProgress(received, totalSize)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "getFile error: ${e.message}")
            false
        }
    }

    // -------- DELETE --------
    suspend fun delete(path: String, useRoot: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try { sendCommand(BtCommand("DELETE", path = path, useRoot = useRoot))?.success ?: false }
        catch (e: Exception) { false }
    }

    // -------- RENAME --------
    suspend fun rename(oldPath: String, newPath: String, useRoot: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try { sendCommand(BtCommand("RENAME", path = oldPath, newPath = newPath, useRoot = useRoot))?.success ?: false }
        catch (e: Exception) { false }
    }

    // -------- MKDIR --------
    suspend fun mkdir(path: String, useRoot: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try { sendCommand(BtCommand("MKDIR", path = path, useRoot = useRoot))?.success ?: false }
        catch (e: Exception) { false }
    }

    // -------- ROOT STATUS --------
    suspend fun getRootStatus(): Boolean = withContext(Dispatchers.IO) {
        try { sendCommand(BtCommand("ROOT_STATUS"))?.isRoot ?: false }
        catch (e: Exception) { false }
    }

    // -------- INTERNAL --------
    private fun sendCommand(cmd: BtCommand): BtResponse? {
        sendRaw(cmd.toJson())
        return receiveJson()
    }

    private fun sendRaw(json: String) {
        val out = output ?: throw IOException("לא מחובר")
        val bytes = json.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    private fun receiveJson(): BtResponse? {
        return try {
            val inp = input ?: return null
            val len = inp.readInt()
            if (len <= 0 || len > 10_000_000) return null
            val bytes = ByteArray(len)
            inp.readFully(bytes)
            BtResponse.fromJson(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "receiveJson error: ${e.message}")
            null
        }
    }
}
