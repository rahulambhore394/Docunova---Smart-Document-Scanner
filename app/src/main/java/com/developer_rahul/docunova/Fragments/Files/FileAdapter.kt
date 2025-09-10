package com.developer_rahul.docunova.Fragments.Files

import android.content.Context
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.developer_rahul.docunova.R
import java.text.SimpleDateFormat
import java.util.*

class FileAdapter(
    private val context: Context,
    private var files: List<DriveFileModel>,
    private var viewType: Int = VIEW_TYPE_LIST,
    private val onDownloadClick: (DriveFileModel) -> Unit,
    private val onFileClick: (DriveFileModel) -> Unit,
    private val onMoreClick: (DriveFileModel, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_LIST = 0
        const val VIEW_TYPE_GRID = 1
        private const val TYPE_LIST = 0
        private const val TYPE_GRID = 1
    }

    fun updateFiles(newFiles: List<DriveFileModel>) {
        files = newFiles
        notifyDataSetChanged()
    }

    fun setViewType(newViewType: Int) {
        viewType = newViewType
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (viewType == VIEW_TYPE_LIST) TYPE_LIST else TYPE_GRID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_LIST) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.file_item_single, parent, false)
            ListViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file_box, parent, false)
            GridViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val file = files[position]
        when (holder) {
            is ListViewHolder -> holder.bind(file)
            is GridViewHolder -> holder.bind(file)
        }
    }

    override fun getItemCount() = files.size

    inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileName: TextView = itemView.findViewById(R.id.fileTitle2)
        private val imageThumbnail: ImageView = itemView.findViewById(R.id.imageThumbnail2)
        private val textType: TextView = itemView.findViewById(R.id.fileType2)
        private val fileMetaData: TextView = itemView.findViewById(R.id.fileMeta2)
        private val downloadButton: ImageView = itemView.findViewById(R.id.downloadBtn)
        private val moreButton: ImageView = itemView.findViewById(R.id.Btn_more)
        private val iconMore: ImageView = itemView.findViewById(R.id.Btn_more)


        fun bind(file: DriveFileModel) {
            if (!file.thumbnailLink.isNullOrEmpty()) {
                Glide.with(context)
                    .load(file.thumbnailLink)
                    .placeholder(R.drawable.app_icon)
                    .into(imageThumbnail)
            } else {
                Glide.with(context)
                    .load(R.drawable.app_icon)
                    .into(imageThumbnail)
            }
            fileName.text = file.name
            val fileExtension = file.name.substringAfterLast('.', "").uppercase()
            textType.text = if (fileExtension.isNotEmpty()) fileExtension else "FILE"
            fileMetaData.text = "${formatFileSize(file.size)} • ${formatDate(file.modifiedTime)}"

            downloadButton.setOnClickListener { onDownloadClick(file) }
            moreButton.setOnClickListener { onMoreClick(file, moreButton) }
            iconMore.setOnClickListener { onMoreClick(file, iconMore) }
            itemView.setOnClickListener { onFileClick(file) }
        }
    }

    inner class GridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageThumbnail: ImageView = itemView.findViewById(R.id.imageThumbnail)
        private val textType: TextView = itemView.findViewById(R.id.textType)
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textDate: TextView = itemView.findViewById(R.id.textDate)
        private val textSize: TextView = itemView.findViewById(R.id.textSize)
        private val textDownload: TextView = itemView.findViewById(R.id.textDownload)
        private val iconMore: ImageView = itemView.findViewById(R.id.iconMore)

        fun bind(file: DriveFileModel) {
            // Set thumbnail - try to load from Drive if available, otherwise use placeholder
            if (!file.thumbnailLink.isNullOrEmpty()) {
                Glide.with(context)
                    .load(file.thumbnailLink)
                    .placeholder(R.drawable.app_icon)
                    .into(imageThumbnail)
            } else {
                Glide.with(context)
                    .load(R.drawable.app_icon)
                    .into(imageThumbnail)
            }

            // Set file type based on extension
            val fileExtension = file.name.substringAfterLast('.', "").uppercase()
            textType.text = if (fileExtension.isNotEmpty()) fileExtension else "FILE"

            textTitle.text = file.name
            textDate.text = formatDate(file.modifiedTime) // Use modifiedTime for display
            textSize.text = formatFileSize(file.size)

            textDownload.setOnClickListener { onDownloadClick(file) }
            iconMore.setOnClickListener { onMoreClick(file, iconMore) }
            itemView.setOnClickListener { onFileClick(file) }
        }
    }

    private fun formatFileSize(size: Long): String {
        return Formatter.formatFileSize(context, size)
    }

    private fun formatDate(timestamp: Long): String {
        return try {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
        } catch (e: Exception) {
            "Unknown date"
        }
    }
}