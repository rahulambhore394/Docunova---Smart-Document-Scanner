package com.developer_rahul.docunova.Fragments.Files

data class DriveFileModel(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long, // Changed to Long to match Drive API
    val modifiedTime: Long, // This should be the upload date
    val thumbnailLink: String? = null, // Make nullable
    val webContentLink: String? = null, // Make nullable
    val createdTime: Long? = null // Add created time if available
)