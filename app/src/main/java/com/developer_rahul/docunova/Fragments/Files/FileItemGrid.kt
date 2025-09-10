package com.developer_rahul.docunova.Fragments.Files

data class FileItemGrid(
    val title: String,
    val date: String,
    val size: String,
    val type: String,
    val thumbnailResId: Int // drawable resource ID
)