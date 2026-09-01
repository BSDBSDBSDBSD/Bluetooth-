package com.bsd.bluetoothexplorer.bluetooth

import android.bluetooth.*
import android.util.Log
import com.bsd.bluetoothexplorer.model.BtCommand
import com.bsd.bluetoothexplorer.model.BtResponse
import com.bsd.bluetoothexplorer.model.FileItem
import kotlinx.coroutines.*
import java.io.*

class BluetoothClient {

    companion object {
        const val TAG = "BTClient"
    }

    private var socket: BluetoothSocket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    var isConnected = false
        private set

    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()
            val s = device.createRfcommSocketToServiceRecord(BluetoothServerService.BT_UUID)
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
            s.connect()
            socket = s
            input = DataInputStream(BufferedInputStream(s.inputStream))
            output = DataOutputStream(BufferedOutputStream(s.outputStream))
            isConnected = true
            Log.d(TAG, "Connected to ${device.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connect failed: ${e.message}")
            isConnected = false
            false
        }
    }

    fun disconnect() {
        try {
            input?.close()
            output?.close()
            socket?.close()
        } catch (_: Exception) {}
        isConnected = false
    }

    // -------- LIST DIR --------
    suspend fun listDir(path: String, useRoot: Boolean = false): List<FileItem> = withContext(Dispatchers.IO) {
        val cmd = BtCommand("LIST_DIR", path = path, useRoot = useRoot)
        val response = sendCommand(cmd) ?: return@withContext emptyList()
        response.files
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

            // קבלת JSON עם גודל הקובץ
            val response = receiveJson() ?: return@withContext false
            if (!response.success) return@withContext false

            val totalSize = response.fileSize

            // קבלת bytes של הקובץ
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
        sendCommand(BtCommand("DELETE", path = path, useRoot = useRoot))?.success ?: false
    }

    // -------- RENAME --------
    suspend fun rename(oldPath: String, newPath: String, useRoot: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        sendCommand(BtCommand("RENAME", path = oldPath, newPath = newPath, useRoot = useRoot))?.success ?: false
    }

    // -------- MKDIR --------
    suspend fun mkdir(path: String, useRoot: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        sendCommand(BtCommand("MKDIR", path = path, useRoot = useRoot))?.success ?: false
    }

    // -------- ROOT STATUS --------
    suspend fun getRootStatus(): Boolean = withContext(Dispatchers.IO) {
        sendCommand(BtCommand("ROOT_STATUS"))?.isRoot ?: false
    }

    // -------- פונקציות פנימיות --------
    private fun sendCommand(cmd: BtCommand): BtResponse? {
        sendRaw(cmd.toJson())
        return receiveJson()
    }

    private fun sendRaw(json: String) {
        val out = output ?: throw IOException("Not connected")
        val bytes = json.toByteArray()
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    private fun receiveJson(): BtResponse? {
        return try {
            val inp = input ?: return null
            val len = inp.readInt()
            val bytes = ByteArray(len)
            inp.readFully(bytes)
            BtResponse.fromJson(String(bytes))
        } catch (e: Exception) {
            Log.e(TAG, "receiveJson error: ${e.message}")
            null
        }
    }
}
