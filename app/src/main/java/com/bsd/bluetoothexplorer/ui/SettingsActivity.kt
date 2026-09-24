package com.bsd.bluetoothexplorer.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bsd.bluetoothexplorer.R

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS = "bt_explorer_settings"
        const val KEY_DOWNLOAD_PATH    = "download_path"
        const val KEY_SHOW_HIDDEN      = "show_hidden"
        const val KEY_SORT_ORDER       = "sort_order"  // 0=name, 1=size, 2=date
        const val KEY_CONN_TIMEOUT     = "conn_timeout" // seconds
        const val KEY_OVERWRITE_FILES  = "overwrite_files"
        const val KEY_CONFIRM_DELETE   = "confirm_delete"

        fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun getDownloadPath(ctx: Context): String =
            prefs(ctx).getString(KEY_DOWNLOAD_PATH, null)
            ?: (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: "/sdcard/Download")

        fun getShowHidden(ctx: Context)    = prefs(ctx).getBoolean(KEY_SHOW_HIDDEN, false)
        fun getSortOrder(ctx: Context)     = prefs(ctx).getInt(KEY_SORT_ORDER, 0)
        fun getConnTimeout(ctx: Context)   = prefs(ctx).getInt(KEY_CONN_TIMEOUT, 15)
        fun getOverwriteFiles(ctx: Context)= prefs(ctx).getBoolean(KEY_OVERWRITE_FILES, false)
        fun getConfirmDelete(ctx: Context) = prefs(ctx).getBoolean(KEY_CONFIRM_DELETE, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_bt)

        supportActionBar?.title = "הגדרות"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = prefs(this)

        // Download path display
        val tvDownloadPath = findViewById<TextView>(R.id.tvDownloadPath)
        tvDownloadPath.text = getDownloadPath(this)

        // Sort order
        val spinnerSort = findViewById<Spinner>(R.id.spinnerSort)
        val sortOptions = arrayOf("שם (א-ת)", "גודל", "תאריך")
        spinnerSort.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sortOptions)
        spinnerSort.setSelection(prefs.getInt(KEY_SORT_ORDER, 0))
        spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                prefs.edit().putInt(KEY_SORT_ORDER, pos).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Connection timeout
        val spinnerTimeout = findViewById<Spinner>(R.id.spinnerTimeout)
        val timeoutOptions = arrayOf("5 שניות", "10 שניות", "15 שניות", "30 שניות", "60 שניות")
        val timeoutValues  = intArrayOf(5, 10, 15, 30, 60)
        spinnerTimeout.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timeoutOptions)
        val curTimeout = prefs.getInt(KEY_CONN_TIMEOUT, 15)
        spinnerTimeout.setSelection(timeoutValues.indexOfFirst { it == curTimeout }.takeIf { it >= 0 } ?: 2)
        spinnerTimeout.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                prefs.edit().putInt(KEY_CONN_TIMEOUT, timeoutValues[pos]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Show hidden files
        val switchHidden = findViewById<Switch>(R.id.switchShowHidden)
        switchHidden.isChecked = prefs.getBoolean(KEY_SHOW_HIDDEN, false)
        switchHidden.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_SHOW_HIDDEN, checked).apply()
        }

        // Overwrite files
        val switchOverwrite = findViewById<Switch>(R.id.switchOverwrite)
        switchOverwrite.isChecked = prefs.getBoolean(KEY_OVERWRITE_FILES, false)
        switchOverwrite.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_OVERWRITE_FILES, checked).apply()
        }

        // Confirm delete
        val switchConfirmDelete = findViewById<Switch>(R.id.switchConfirmDelete)
        switchConfirmDelete.isChecked = prefs.getBoolean(KEY_CONFIRM_DELETE, true)
        switchConfirmDelete.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_CONFIRM_DELETE, checked).apply()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
