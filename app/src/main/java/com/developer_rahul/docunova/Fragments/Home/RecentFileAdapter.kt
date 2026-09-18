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
    private val onItemClick: (RecentFile) -> Unit,
    private val onMoreClick: ((RecentFile, View) -> Unit)? = null
) : RecyclerView.Adapter<RecentFileAdapter.FileViewHolder>() {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbImage: ImageView = view.findViewById(R.id.imageThumbnail2)
        val fileName: TextView = view.findViewById(R.id.fileTitle2)
        val fileDate: TextView = view.findViewById(R.id.fileMeta2)
        val fileType: TextView? = view.findViewById(R.id.fileType2)
        val downloadBtn: View? = view.findViewById(R.id.downloadBtn)
        val moreBtn: View? = view.findViewById(R.id.Btn_more)
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
        val formattedSize = if (file.size > 0) formatFileSize(file.size) else ""
        holder.fileDate.text = if (formattedSize.isNotEmpty()) "$formattedSize • ${file.date}" else file.date

        val ext = file.name.substringAfterLast('.', "PDF").uppercase()
        holder.fileType?.text = if (ext.isNotEmpty()) ext else "PDF"

        // For local recent files, hide download button since they are already stored locally
        holder.downloadBtn?.visibility = View.GONE

        file.thumbnailUri.takeIf { it.isNotEmpty() }?.let { uri ->
            Glide.with(holder.itemView.context)
                .load(Uri.parse(uri))
                .placeholder(R.drawable.ic_pdf)
                .into(holder.thumbImage)
        } ?: holder.thumbImage.setImageResource(R.drawable.ic_pdf)

        holder.itemView.setOnClickListener {
            val intent = com.developer_rahul.docunova.DocumentViewerActivity.createIntent(
                context = holder.itemView.context,
                fileId = file.driveFileId,
                fileName = file.name,
                mimeType = file.mimeType.takeIf { it.isNotEmpty() } ?: "application/pdf",
                localPath = file.filePath.takeIf { it.isNotEmpty() }
            )
            holder.itemView.context.startActivity(intent)
        }

        holder.moreBtn?.setOnClickListener {
            if (onMoreClick != null) {
                onMoreClick.invoke(file, it)
            } else {
                val intent = com.developer_rahul.docunova.DocumentViewerActivity.createIntent(
                    context = holder.itemView.context,
                    fileId = file.driveFileId,
                    fileName = file.name,
                    mimeType = file.mimeType.takeIf { it.isNotEmpty() } ?: "application/pdf",
                    localPath = file.filePath.takeIf { it.isNotEmpty() }
                )
                holder.itemView.context.startActivity(intent)
            }
        }
    }

    private fun formatFileSize(size: Long): String = com.developer_rahul.docunova.DriveServiceHelper.formatFileSize(size)

    fun updateFiles(newList: List<RecentFile>) {
        list = newList ?: emptyList()
        notifyDataSetChanged()
    }
}