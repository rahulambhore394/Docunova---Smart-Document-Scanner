package com.developer_rahul.docunova.Fragments.Files

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.developer_rahul.docunova.DriveServiceHelper
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

        fun bind(file: DriveFileModel) {
            val fileExtension = file.name.substringAfterLast('.', "").uppercase()
            val placeholderRes = getPlaceholderForExtension(fileExtension)

            if (!file.thumbnailLink.isNullOrEmpty()) {
                Glide.with(context)
                    .load(file.thumbnailLink)
                    .placeholder(placeholderRes)
                    .error(placeholderRes)
                    .into(imageThumbnail)
            } else {
                imageThumbnail.setImageResource(placeholderRes)
            }

            fileName.text = file.name
            val displayType = if (fileExtension.isNotEmpty()) fileExtension else "DOC"
            textType.text = displayType
            applyBadgeStyling(textType, fileExtension)

            fileMetaData.text = "${DriveServiceHelper.formatFileSize(file.size)} • ${formatDate(file.modifiedTime)}"

            downloadButton.setOnClickListener { onDownloadClick(file) }
            moreButton.setOnClickListener { onMoreClick(file, moreButton) }
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
            val fileExtension = file.name.substringAfterLast('.', "").uppercase()
            val placeholderRes = getPlaceholderForExtension(fileExtension)

            if (!file.thumbnailLink.isNullOrEmpty()) {
                imageThumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
                imageThumbnail.setPadding(0, 0, 0, 0)
                Glide.with(context)
                    .load(file.thumbnailLink)
                    .placeholder(placeholderRes)
                    .error(placeholderRes)
                    .into(imageThumbnail)
            } else {
                imageThumbnail.scaleType = ImageView.ScaleType.CENTER_INSIDE
                val pad = (20 * context.resources.displayMetrics.density).toInt()
                imageThumbnail.setPadding(pad, pad, pad, pad)
                imageThumbnail.setImageResource(placeholderRes)
            }

            val displayType = if (fileExtension.isNotEmpty()) fileExtension else "DOC"
            textType.text = displayType
            applyBadgeStyling(textType, fileExtension)

            textTitle.text = file.name
            textDate.text = formatDate(file.modifiedTime)
            textSize.text = DriveServiceHelper.formatFileSize(file.size)

            textDownload.setOnClickListener { onDownloadClick(file) }
            iconMore.setOnClickListener { onMoreClick(file, iconMore) }
            itemView.setOnClickListener { onFileClick(file) }
        }
    }

    private fun getPlaceholderForExtension(ext: String): Int {
        return when (ext) {
            "PDF" -> R.drawable.ic_pdf
            "DOC", "DOCX" -> R.drawable.ic_word
            "TXT" -> R.drawable.ic_text
            "PNG", "JPG", "JPEG" -> R.drawable.ic_scanned_files
            else -> R.drawable.ic_files2
        }
    }

    private fun applyBadgeStyling(textView: TextView, ext: String) {
        when (ext) {
            "PDF" -> {
                textView.setBackgroundResource(R.drawable.bg_pill_badge_red)
                textView.setTextColor(Color.parseColor("#DC2626"))
            }
            "DOC", "DOCX" -> {
                textView.setBackgroundResource(R.drawable.bg_pill_badge_blue)
                textView.setTextColor(Color.parseColor("#2563EB"))
            }
            "TXT" -> {
                textView.setBackgroundResource(R.drawable.bg_pill_badge_green)
                textView.setTextColor(Color.parseColor("#059669"))
            }
            else -> {
                textView.setBackgroundResource(R.drawable.bg_pill_badge_amber)
                textView.setTextColor(Color.parseColor("#D97706"))
            }
        }
    }

    private fun formatDate(timestamp: Long): String {
        return try {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
        } catch (e: Exception) {
            "Unknown date"
        }
    }
}