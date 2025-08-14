package com.developer_rahul.docunova.RoomDB

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var name: String,
    var filePath: String,
    val thumbnailUri: String,
    val date: String,
    val isSynced: Boolean = false
)