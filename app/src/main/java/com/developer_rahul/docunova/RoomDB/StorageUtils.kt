package com.developer_rahul.docunova.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object StorageUtils {
    fun getDocunovaStorageDir(context: Context): File {
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Docunova")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    fun createNewPdfFile(context: Context, fileName: String): File {
        val storageDir = getDocunovaStorageDir(context)
        return File(storageDir, "$fileName.pdf")
    }

    fun getCurrentDate(): String {
        return SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
    }

    fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "")
    }
}