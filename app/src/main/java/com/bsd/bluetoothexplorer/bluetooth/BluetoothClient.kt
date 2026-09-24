package com.bsd.bluetoothexplorer.bluetooth

import android.bluetooth.*
import android.util.Log
import com.bsd.bluetoothexplorer.model.BtCommand
import com.bsd.bluetoothexplorer.model.BtResponse
import com.bsd.bluetoothexplorer.model.FileItem
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    // Serialises every request<->response so overlapping commands can't desync the socket.
    private val io = Mutex()
    var isConnected = false
        private set

    // חיבור Bluetooth רגיל — עם כמה ניסיונות (RFCOMM לפעמים נכשל בניסיון הראשון)
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        repeat(3) { attempt ->
            try {
                val s = device.createRfcommSocketToServiceRecord(BluetoothServerService.BT_UUID)
                s.connect()
                btSocket = s
                input  = DataInputStream(BufferedInputStream(s.inputStream))
                output = DataOutputStream(BufferedOutputStream(s.outputStream))
                isConnected = true
                Log.d(TAG, "BT Connected to ${device.address}")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "BT connect attempt ${attempt + 1} failed: ${e.message}")
                try { Thread.sleep(700) } catch (_: InterruptedException) {}
            }
        }
        isConnected = false
        false
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
    /** Throws on a connection/protocol error so the UI can tell "failed" from "really empty". */
    suspend fun listDir(path: String, useRoot: Boolean = false): List<FileItem> = withContext(Dispatchers.IO) {
        io.withLock {
            val cmd = BtCommand("LIST_DIR", path = path, useRoot = useRoot)
            sendRaw(cmd.toJson())
            val response = receiveJson() ?: throw IOException("אין תגובה מהשרת")
            response.files
        }
    }

    // -------- GET FILE --------
    suspend fun getFile(
        remotePath: String,
        localFile: File,
        useRoot: Boolean = false,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        io.withLock {
            try {
                val cmd = BtCommand("GET_FILE", path = remotePath, useRoot = useRoot)
                sendRaw(cmd.toJson())

                val response = receiveJson() ?: return@withLock false
                if (!response.success) return@withLock false

                val totalSize = response.fileSize
                localFile.parentFile?.mkdirs()
                FileOutputStream(localFile).use { fos ->
                    val buffer = ByteArray(8192)
                    var received = 0L
                    val inp = input ?: return@withLock false
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
    }

    // -------- DELETE --------
    suspend fun delete(path: String, useRoot: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try { command(BtCommand("DELETE", path = path, useRoot = useRoot))?.success ?: false }
        catch (e: Exception) { false }
    }

    // -------- RENAME --------
    suspend fun rename(oldPath: String, newPath: String, useRoot: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try { command(BtCommand("RENAME", path = oldPath, newPath = newPath, useRoot = useRoot))?.success ?: false }
        catch (e: Exception) { false }
    }

    // -------- MKDIR --------
    suspend fun mkdir(path: String, useRoot: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try { command(BtCommand("MKDIR", path = path, useRoot = useRoot))?.success ?: false }
        catch (e: Exception) { false }
    }

    // -------- ROOT STATUS --------
    suspend fun getRootStatus(): Boolean = withContext(Dispatchers.IO) {
        try { command(BtCommand("ROOT_STATUS"))?.isRoot ?: false }
        catch (e: Exception) { false }
    }

    // -------- INTERNAL --------
    private suspend fun command(cmd: BtCommand): BtResponse? = io.withLock {
        sendRaw(cmd.toJson())
        receiveJson()
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
