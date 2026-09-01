package com.bsd.bluetoothexplorer.root

import com.bsd.bluetoothexplorer.model.FileItem
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.InputStream

object RootManager {

    var isRootAvailable: Boolean = false
        private set

    fun init() {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
        isRootAvailable = try {
            Shell.getShell().isRoot
        } catch (e: Exception) {
            false
        }
    }

    // ----------- LIST DIR -----------
    fun listDir(path: String, useRoot: Boolean): List<FileItem> {
        return if (useRoot && isRootAvailable) {
            listDirRoot(path)
        } else {
            listDirNormal(path)
        }
    }

    private fun listDirRoot(path: String): List<FileItem> {
        val result = Shell.cmd("ls -la '$path' 2>/dev/null").exec()
        if (!result.isSuccess) return emptyList()

        return result.out.mapNotNull { line ->
            parseLsLine(line, path)
        }
    }

    private fun parseLsLine(line: String, parentPath: String): FileItem? {
        if (line.startsWith("total") || line.isBlank()) return null
        val parts = line.trim().split(Regex("\\s+"), limit = 9)
        if (parts.size < 9) return null
        val permissions = parts[0]
        val size = parts[4].toLongOrNull() ?: 0L
        val name = parts[8]
        if (name == "." || name == "..") return null
        val isDir = permissions.startsWith("d")
        return FileItem(
            name = name,
            path = "$parentPath/$name",
            isDirectory = isDir,
            size = size,
            permissions = permissions
        )
    }

    private fun listDirNormal(path: String): List<FileItem> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()?.map { f ->
            FileItem(
                name = f.name,
                path = f.absolutePath,
                isDirectory = f.isDirectory,
                size = if (f.isFile) f.length() else 0L,
                lastModified = f.lastModified()
            )
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
    }

    // ----------- READ FILE (InputStream) -----------
    fun openFile(path: String, useRoot: Boolean): InputStream? {
        return if (useRoot && isRootAvailable) {
            openFileRoot(path)
        } else {
            openFileNormal(path)
        }
    }

    private fun openFileRoot(path: String): InputStream? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat '$path'"))
            process.inputStream
        } catch (e: Exception) {
            null
        }
    }

    private fun openFileNormal(path: String): InputStream? {
        return try {
            File(path).inputStream()
        } catch (e: Exception) {
            null
        }
    }

    // ----------- DELETE -----------
    fun delete(path: String, useRoot: Boolean): Boolean {
        return if (useRoot && isRootAvailable) {
            Shell.cmd("rm -rf '$path'").exec().isSuccess
        } else {
            File(path).deleteRecursively()
        }
    }

    // ----------- RENAME -----------
    fun rename(oldPath: String, newPath: String, useRoot: Boolean): Boolean {
        return if (useRoot && isRootAvailable) {
            Shell.cmd("mv '$oldPath' '$newPath'").exec().isSuccess
        } else {
            File(oldPath).renameTo(File(newPath))
        }
    }

    // ----------- MKDIR -----------
    fun mkdir(path: String, useRoot: Boolean): Boolean {
        return if (useRoot && isRootAvailable) {
            Shell.cmd("mkdir -p '$path'").exec().isSuccess
        } else {
            File(path).mkdirs()
        }
    }

    // ----------- FILE SIZE -----------
    fun getFileSize(path: String, useRoot: Boolean): Long {
        return if (useRoot && isRootAvailable) {
            val result = Shell.cmd("stat -c%s '$path' 2>/dev/null").exec()
            result.out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
        } else {
            File(path).length()
        }
    }
}
