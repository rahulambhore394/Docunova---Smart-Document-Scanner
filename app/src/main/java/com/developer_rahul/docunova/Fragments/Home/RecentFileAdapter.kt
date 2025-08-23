package com.developer_rahul.docunova.Adapters

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.developer_rahul.docunova.R
import com.developer_rahul.docunova.RoomDB.RecentFile
class RecentFileAdapter(
    private var list: List<RecentFile> = emptyList(),
    private val onItemClick: (RecentFile) -> Unit
) : RecyclerView.Adapter<RecentFileAdapter.FileViewHolder>() {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbImage: ImageView = view.findViewById(R.id.imageThumbnail2)
        val fileName: TextView = view.findViewById(R.id.fileTitle2)
        val fileDate: TextView = view.findViewById(R.id.fileMeta2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.file_item_single, parent, false)
        return FileViewHolder(view)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = list.getOrNull(position) ?: return

        holder.fileName.text = file.name
        holder.fileDate.text = file.date

        file.thumbnailUri.takeIf { it.isNotEmpty() }?.let { uri ->
            Glide.with(holder.itemView.context)
                .load(Uri.parse(uri))
                .placeholder(R.drawable.ic_pdf)
                .into(holder.thumbImage)
        } ?: holder.thumbImage.setImageResource(R.drawable.app_icon)

        holder.itemView.setOnClickListener {
            file.filePath.takeIf { it.isNotEmpty() }?.let { path ->
                try {
                    val fileUri = FileProvider.getUriForFile(
                        holder.itemView.context,
                        "${holder.itemView.context.packageName}.provider",
                        java.io.File(path)
                    )
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        holder.itemView.context.startActivity(this)
                    }
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(holder.itemView.context, "No PDF viewer found", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    onItemClick(file) // Fallback to download
                }
            } ?: onItemClick(file) // Trigger download if no local path
        }
    }

    fun updateFiles(newList: List<RecentFile>) {
        list = newList ?: emptyList()
        notifyDataSetChanged()
    }
}