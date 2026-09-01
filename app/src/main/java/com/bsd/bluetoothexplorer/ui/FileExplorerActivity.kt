package com.bsd.bluetoothexplorer.ui

import android.app.AlertDialog
import android.os.*
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bsd.bluetoothexplorer.R
import com.bsd.bluetoothexplorer.model.FileItem
import kotlinx.coroutines.launch
import java.io.File

class FileExplorerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvPath: TextView
    private lateinit var tvConnected: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var fileAdapter: FileAdapter

    private val client get() = ClientHolder.client
    private val pathStack = ArrayDeque<String>()
    private var useRoot = false
    private var currentPath = "/storage/emulated/0"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_explorer)

        recyclerView = findViewById(R.id.recyclerFiles)
        tvPath       = findViewById(R.id.tvCurrentPath)
        tvConnected  = findViewById(R.id.tvConnected)
        progressBar  = findViewById(R.id.progressBar)

        tvConnected.text = "📱 ${ClientHolder.remoteDeviceName}"

        fileAdapter = FileAdapter(
            onItemClick = { item -> onFileItemClick(item) },
            onItemLongClick = { item -> showContextMenu(item) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = fileAdapter

        // בדיקת root בשרת
        lifecycleScope.launch {
            val remoteRoot = client?.getRootStatus() ?: false
            runOnUiThread {
                if (remoteRoot) {
                    Toast.makeText(this@FileExplorerActivity, "✅ שרת עם Root", Toast.LENGTH_SHORT).show()
                }
            }
        }

        loadDir(currentPath)
    }

    private fun loadDir(path: String) {
        showLoading(true)
        tvPath.text = path
        lifecycleScope.launch {
            val files = client?.listDir(path, useRoot) ?: emptyList()
            runOnUiThread {
                showLoading(false)
                if (files.isEmpty() && !useRoot) {
                    // נסה root אוטומטית אם הרגיל ריק
                }
                fileAdapter.setFiles(files)
                currentPath = path
            }
        }
    }

    private fun onFileItemClick(item: FileItem) {
        if (item.isDirectory) {
            pathStack.addLast(currentPath)
            loadDir(item.path)
        } else {
            showDownloadDialog(item)
        }
    }

    private fun showDownloadDialog(item: FileItem) {
        val sizeStr = formatSize(item.size)
        AlertDialog.Builder(this)
            .setTitle("📄 ${item.name}")
            .setMessage("גודל: $sizeStr\nמיקום: ${item.path}\n\nהורד למכשיר זה?")
            .setPositiveButton("הורד") { _, _ -> downloadFile(item) }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun downloadFile(item: FileItem) {
        val localFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            item.name
        )
        showLoading(true)
        lifecycleScope.launch {
            val success = client?.getFile(
                remotePath = item.path,
                localFile = localFile,
                useRoot = useRoot,
                onProgress = { received, total ->
                    runOnUiThread {
                        val pct = if (total > 0) (received * 100 / total).toInt() else 0
                        tvPath.text = "מוריד... $pct% (${formatSize(received)}/${formatSize(total)})"
                    }
                }
            ) ?: false

            runOnUiThread {
                showLoading(false)
                tvPath.text = currentPath
                if (success) {
                    Toast.makeText(this@FileExplorerActivity,
                        "✅ הורד ל: ${localFile.absolutePath}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@FileExplorerActivity, "❌ ההורדה נכשלה", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showContextMenu(item: FileItem) {
        val options = if (item.isDirectory) {
            arrayOf("מחק תיקייה", "שנה שם")
        } else {
            arrayOf("הורד", "מחק", "שנה שם")
        }
        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "הורד" -> downloadFile(item)
                    "מחק", "מחק תיקייה" -> confirmDelete(item)
                    "שנה שם" -> showRenameDialog(item)
                }
            }.show()
    }

    private fun confirmDelete(item: FileItem) {
        AlertDialog.Builder(this)
            .setTitle("מחיקה")
            .setMessage("למחוק את '${item.name}'? פעולה זו אינה הפיכה.")
            .setPositiveButton("מחק") { _, _ ->
                lifecycleScope.launch {
                    val ok = client?.delete(item.path, useRoot) ?: false
                    runOnUiThread {
                        if (ok) loadDir(currentPath)
                        else Toast.makeText(this@FileExplorerActivity, "מחיקה נכשלה", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("ביטול", null).show()
    }

    private fun showRenameDialog(item: FileItem) {
        val input = EditText(this).apply { setText(item.name) }
        AlertDialog.Builder(this)
            .setTitle("שנה שם")
            .setView(input)
            .setPositiveButton("אשר") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank()) return@setPositiveButton
                val newPath = item.path.substringBeforeLast("/") + "/$newName"
                lifecycleScope.launch {
                    val ok = client?.rename(item.path, newPath, useRoot) ?: false
                    runOnUiThread {
                        if (ok) loadDir(currentPath)
                        else Toast.makeText(this@FileExplorerActivity, "שינוי שם נכשל", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("ביטול", null).show()
    }

    private fun showNewFolderDialog() {
        val input = EditText(this).apply { hint = "שם התיקייה" }
        AlertDialog.Builder(this)
            .setTitle("תיקייה חדשה")
            .setView(input)
            .setPositiveButton("צור") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) return@setPositiveButton
                lifecycleScope.launch {
                    val ok = client?.mkdir("$currentPath/$name", useRoot) ?: false
                    runOnUiThread {
                        if (ok) loadDir(currentPath)
                        else Toast.makeText(this@FileExplorerActivity, "יצירה נכשלה", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("ביטול", null).show()
    }

    override fun onBackPressed() {
        if (pathStack.isNotEmpty()) {
            loadDir(pathStack.removeLast())
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explorer_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menuRoot -> {
                useRoot = !useRoot
                item.title = if (useRoot) "Root: ON" else "Root: OFF"
                Toast.makeText(this, if (useRoot) "מצב Root פעיל" else "מצב Root כבוי", Toast.LENGTH_SHORT).show()
                loadDir(currentPath)
                true
            }
            R.id.menuHome -> {
                pathStack.clear()
                loadDir("/storage/emulated/0")
                true
            }
            R.id.menuRootDir -> {
                pathStack.clear()
                loadDir("/")
                true
            }
            R.id.menuNewFolder -> {
                showNewFolderDialog()
                true
            }
            R.id.menuRefresh -> {
                loadDir(currentPath)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }
}
