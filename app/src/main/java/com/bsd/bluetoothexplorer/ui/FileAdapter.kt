package com.bsd.bluetoothexplorer.ui

import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bsd.bluetoothexplorer.R
import com.bsd.bluetoothexplorer.model.FileItem

class FileAdapter(
    private val onItemClick: (FileItem) -> Unit,
    private val onItemLongClick: (FileItem) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    private val files = mutableListOf<FileItem>()

    fun setFiles(newFiles: List<FileItem>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }

    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView  = view.findViewById(R.id.tvName)
        val info: TextView  = view.findViewById(R.id.tvInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val item = files[position]
        holder.name.text = item.name
        holder.info.text = if (item.isDirectory) {
            "תיקייה" + if (item.permissions.isNotEmpty()) " | ${item.permissions}" else ""
        } else {
            formatSize(item.size) + if (item.permissions.isNotEmpty()) " | ${item.permissions}" else ""
        }
        holder.icon.setImageResource(
            when {
                item.isDirectory -> R.drawable.ic_folder
                item.name.endsWith(".apk") -> R.drawable.ic_apk
                item.name.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|bmp)", RegexOption.IGNORE_CASE)) -> R.drawable.ic_image
                item.name.matches(Regex(".*\\.(mp4|mkv|avi|mov|3gp)", RegexOption.IGNORE_CASE)) -> R.drawable.ic_video
                item.name.matches(Regex(".*\\.(mp3|aac|flac|wav|ogg)", RegexOption.IGNORE_CASE)) -> R.drawable.ic_audio
                item.name.matches(Regex(".*\\.(pdf|doc|docx|txt|xls|xlsx)", RegexOption.IGNORE_CASE)) -> R.drawable.ic_document
                else -> R.drawable.ic_file
            }
        )
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { onItemLongClick(item); true }
    }

    override fun getItemCount() = files.size

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }
}
