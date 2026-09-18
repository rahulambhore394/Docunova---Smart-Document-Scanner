package com.developer_rahul.docunova.RoomDB

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var userId: String = "",
    var name: String = "",
    var filePath: String = "",
    var mimeType: String = "application/pdf",
    var thumbnailUri: String = "",
    var date: String = "",
    var driveFileId: String? = null,
    var driveFolderId: String? = null,
    var uploadStatus: String = "UPLOADED", // PENDING, UPLOADING, UPLOADED, FAILED
    var isSynced: Boolean = false,
    var size: Long = 0L,
    var createdAt: Long = System.currentTimeMillis()
)