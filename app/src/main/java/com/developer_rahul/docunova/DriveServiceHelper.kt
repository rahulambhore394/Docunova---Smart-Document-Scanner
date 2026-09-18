package com.developer_rahul.docunova

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.developer_rahul.docunova.Fragments.Files.DriveFileModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.OutputStream

object DriveServiceHelper {

    private const val TAG = "DriveServiceHelper"
    private const val APP_FOLDER_NAME = "DocuNova"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"

    // In-memory cache for folder IDs partitioned by account
    private var currentAccountEmail: String? = null
    private var cachedAppFolderId: String? = null
    private var cachedDocumentsFolderId: String? = null
    private var cachedImagesFolderId: String? = null
    private var cachedPdfsFolderId: String? = null

    /**
     * Clear all cached session state on logout or account change.
     */
    fun clearDriveSession() {
        currentAccountEmail = null
        cachedAppFolderId = null
        cachedDocumentsFolderId = null
        cachedImagesFolderId = null
        cachedPdfsFolderId = null
    }

    /** Check if the account has granted Drive scope */
    fun isDriveAuthorized(context: Context, account: GoogleSignInAccount?): Boolean {
        return account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
    }

    /** Build Google Drive service using drive.file scope */
    fun buildService(context: Context, accountName: String): Drive {
        if (currentAccountEmail != accountName) {
            clearDriveSession()
            currentAccountEmail = accountName
        }

        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
        credential.selectedAccountName = accountName

        return Drive.Builder(
            com.google.api.client.http.javanet.NetHttpTransport(),
            com.google.api.client.json.gson.GsonFactory(),
            credential
        ).setApplicationName(APP_FOLDER_NAME)
            .build()
    }

    /**
     * Get or create the root "DocuNova" app folder in the user's Drive.
     * Prevents duplicate folder creation by searching first.
     */
    suspend fun getOrCreateAppFolder(drive: Drive): String = withContext(Dispatchers.IO) {
        cachedAppFolderId?.let { return@withContext it }

        val query = "mimeType='$FOLDER_MIME' and name='$APP_FOLDER_NAME' and 'root' in parents and trashed=false"
        val result = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val folderId = if (result.files.isNotEmpty()) {
            result.files[0].id
        } else {
            val metadata = File().apply {
                name = APP_FOLDER_NAME
                mimeType = FOLDER_MIME
            }
            val created = drive.files().create(metadata).setFields("id").execute()
            created.id
        }

        cachedAppFolderId = folderId
        folderId
    }

    /**
     * Get or create a specific subfolder inside parentFolderId (e.g. "PDFs", "Images", "Documents").
     */
    suspend fun getOrCreateSubfolder(drive: Drive, parentFolderId: String, subfolderName: String): String =
        withContext(Dispatchers.IO) {
            val query = "mimeType='$FOLDER_MIME' and name='$subfolderName' and '$parentFolderId' in parents and trashed=false"
            val result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            if (result.files.isNotEmpty()) {
                result.files[0].id
            } else {
                val metadata = File().apply {
                    name = subfolderName
                    mimeType = FOLDER_MIME
                    parents = listOf(parentFolderId)
                }
                val created = drive.files().create(metadata).setFields("id").execute()
                created.id
            }
        }

    /**
     * Resolve the target subfolder ID based on MIME type.
     */
    suspend fun getSubfolderForMimeType(drive: Drive, mimeType: String): String {
        val rootId = getOrCreateAppFolder(drive)
        return when {
            mimeType.equals("application/pdf", ignoreCase = true) -> {
                if (cachedPdfsFolderId == null) {
                    cachedPdfsFolderId = getOrCreateSubfolder(drive, rootId, "PDFs")
                }
                cachedPdfsFolderId!!
            }
            mimeType.startsWith("image/", ignoreCase = true) -> {
                if (cachedImagesFolderId == null) {
                    cachedImagesFolderId = getOrCreateSubfolder(drive, rootId, "Images")
                }
                cachedImagesFolderId!!
            }
            else -> {
                if (cachedDocumentsFolderId == null) {
                    cachedDocumentsFolderId = getOrCreateSubfolder(drive, rootId, "Documents")
                }
                cachedDocumentsFolderId!!
            }
        }
    }

    /**
     * Upload a local file into the appropriate DocuNova subfolder.
     * Returns the Drive file ID.
     */
    suspend fun uploadLocalFile(
        drive: Drive,
        localFile: java.io.File,
        fileName: String,
        mimeType: String
    ): String = withContext(Dispatchers.IO) {
        val targetFolderId = getSubfolderForMimeType(drive, mimeType)

        val fileMetadata = File().apply {
            name = fileName
            parents = listOf(targetFolderId)
        }

        val mediaContent = FileContent(mimeType, localFile)
        val uploaded = drive.files().create(fileMetadata, mediaContent)
            .setFields("id, name, mimeType, size")
            .execute()

        uploaded.id
    }

    /**
     * Upload a file via Android content Uri into the appropriate DocuNova subfolder.
     */
    suspend fun uploadFileToAppFolder(
        drive: Drive,
        context: Context,
        uri: Uri,
        fileName: String
    ): String = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            else -> "application/octet-stream"
        }

        val targetFolderId = getSubfolderForMimeType(drive, mimeType)

        val fileMetadata = File().apply {
            name = fileName
            parents = listOf(targetFolderId)
        }

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open stream for URI: $uri")
        val mediaContent = InputStreamContent(mimeType, inputStream)

        val uploaded = drive.files().create(fileMetadata, mediaContent)
            .setFields("id, name, mimeType, size")
            .execute()

        uploaded.id
    }

    /**
     * List all files from DocuNova folder and its subfolders (PDFs, Images, Documents).
     * Folders themselves are excluded from the result.
     * Supports pagination up to 1000 items and falls back to quotaBytesUsed if size is omitted.
     */
    suspend fun listFilesFromAppFolder(drive: Drive): List<DriveFileModel> = withContext(Dispatchers.IO) {
        val rootId = getOrCreateAppFolder(drive)
        val pdfsId = getOrCreateSubfolder(drive, rootId, "PDFs")
        val imagesId = getOrCreateSubfolder(drive, rootId, "Images")
        val docsId = getOrCreateSubfolder(drive, rootId, "Documents")

        val parentQuery = "('$rootId' in parents or '$pdfsId' in parents or '$imagesId' in parents or '$docsId' in parents)"
        val query = "$parentQuery and mimeType != '$FOLDER_MIME' and trashed=false"

        val fileList = mutableListOf<DriveFileModel>()
        var pageToken: String? = null

        do {
            val request = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setPageSize(1000)
                .setFields("nextPageToken, files(id, name, mimeType, size, quotaBytesUsed, thumbnailLink, webContentLink, createdTime, modifiedTime)")
            if (pageToken != null) {
                request.pageToken = pageToken
            }
            val result = request.execute()

            result.files?.forEach { file ->
                val fileSize = (file.size ?: file.quotaBytesUsed ?: 0L).toLong()
                fileList.add(
                    DriveFileModel(
                        id = file.id,
                        name = file.name,
                        mimeType = file.mimeType ?: "application/octet-stream",
                        size = fileSize,
                        modifiedTime = file.modifiedTime?.value ?: file.createdTime?.value ?: 0L,
                        thumbnailLink = file.thumbnailLink,
                        webContentLink = file.webContentLink,
                        createdTime = file.createdTime?.value
                    )
                )
            }
            pageToken = result.nextPageToken
        } while (pageToken != null)

        fileList
    }

    /**
     * Fetch Google Drive user storage quota (usage and limit in bytes).
     */
    suspend fun getDriveStorageQuota(drive: Drive): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        try {
            val about = drive.about().get().setFields("storageQuota").execute()
            val quota = about.storageQuota ?: return@withContext null
            val used = (quota.usageInDrive ?: quota.usage ?: 0L).toLong()
            val limit = (quota.limit ?: 0L).toLong()
            Pair(used, limit)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Format byte sizes into clean, human-readable strings without awkward decimals for raw bytes.
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }
        return if (value >= 100) {
            String.format(java.util.Locale.US, "%.0f %s", value, units[unitIndex])
        } else if (value >= 10) {
            String.format(java.util.Locale.US, "%.1f %s", value, units[unitIndex])
        } else {
            String.format(java.util.Locale.US, "%.2f %s", value, units[unitIndex])
        }
    }

    /**
     * Stream a remote file to a private internal cache file strictly for in-app viewing.
     * Never writes to user-visible public storage.
     */
    suspend fun streamFileForViewing(
        drive: Drive,
        fileId: String,
        targetCacheFile: java.io.File
    ) = withContext(Dispatchers.IO) {
        targetCacheFile.parentFile?.mkdirs()
        if (targetCacheFile.exists()) {
            targetCacheFile.delete()
        }
        FileOutputStream(targetCacheFile).use { outputStream ->
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        }
    }

    /**
     * Explicitly download the original document to the user's public Downloads directory.
     * Uses MediaStore on Android 10+ (API 29+) or legacy Environment storage on older versions.
     */
    suspend fun downloadFileToPublicDestination(
        context: Context,
        drive: Drive,
        fileId: String,
        fileName: String,
        mimeType: String
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocuNova")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                }
                return@withContext uri
            }
        }

        // Fallback for Android 9 and below
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appFolder = java.io.File(downloadsDir, "DocuNova").apply { if (!exists()) mkdirs() }
        val destFile = java.io.File(appFolder, fileName)
        FileOutputStream(destFile).use { outputStream ->
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        }
        Uri.fromFile(destFile)
    }

    /**
     * Direct file download to a specific output file.
     */
    fun downloadFile(driveService: Drive, fileId: String, outputFile: java.io.File) {
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { outputStream ->
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        }
    }

    /** Rename a file */
    suspend fun renameFile(drive: Drive, fileId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileMetadata = File().apply {
                name = newName
            }
            drive.files().update(fileId, fileMetadata).execute()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming file $fileId", e)
            false
        }
    }

    /**
     * Delete a file from Google Drive.
     * If the file is already deleted (404), treats it as successful deletion.
     */
    suspend fun deleteFile(drive: Drive, fileId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            drive.files().delete(fileId).execute()
            true
        } catch (e: GoogleJsonResponseException) {
            if (e.statusCode == 404) {
                Log.w(TAG, "File $fileId already deleted from Drive (404).")
                true
            } else {
                Log.e(TAG, "Drive error deleting file $fileId", e)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file $fileId", e)
            false
        }
    }
}