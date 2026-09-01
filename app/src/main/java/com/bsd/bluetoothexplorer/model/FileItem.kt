package com.bsd.bluetoothexplorer.model

import com.google.gson.Gson

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val permissions: String = ""
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): FileItem = Gson().fromJson(json, FileItem::class.java)
    }
}

data class BtCommand(
    val cmd: String,       // LIST_DIR, GET_FILE, DELETE, RENAME, MKDIR, GET_INFO
    val path: String = "",
    val newPath: String = "",
    val useRoot: Boolean = false
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): BtCommand = Gson().fromJson(json, BtCommand::class.java)
    }
}

data class BtResponse(
    val success: Boolean,
    val error: String = "",
    val files: List<FileItem> = emptyList(),
    val fileSize: Long = 0L,     // לפני GET_FILE - שולח קודם את הגודל
    val isRoot: Boolean = false
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): BtResponse = Gson().fromJson(json, BtResponse::class.java)
    }
}
