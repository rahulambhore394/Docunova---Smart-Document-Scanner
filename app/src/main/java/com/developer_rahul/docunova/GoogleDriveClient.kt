package com.developer_rahul.docunova

import android.content.Context
import android.net.Uri
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleDriveClient {

    suspend fun uploadUri(
        driveService: Drive,
        context: Context,
        fileUri: Uri,
        fileName: String
    ): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(fileUri)
        val mediaContent = InputStreamContent("application/pdf", inputStream)

        // ✅ Create metadata with a valid name
        val metadata = File().apply {
            name = fileName // Ensure it's NOT empty
        }

        val uploadedFile = driveService.files().create(metadata, mediaContent)
            .setFields("id")
            .execute()

        uploadedFile.id
    }
}
