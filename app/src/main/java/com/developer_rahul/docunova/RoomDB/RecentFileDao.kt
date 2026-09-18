package com.developer_rahul.docunova.RoomDB

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface RecentFileDao {
    @Insert
    suspend fun insert(file: RecentFile): Long

    @Query("UPDATE recent_files SET filePath = :filePath WHERE id = :fileId")
    suspend fun updateFilePath(fileId: Int, filePath: String)

    @Query("UPDATE recent_files SET name = :newName, filePath = :newPath WHERE id = :fileId")
    suspend fun updateFileName(fileId: Int, newName: String, newPath: String)

    @Query("UPDATE recent_files SET isSynced = 1, driveFileId = :driveFileId, driveFolderId = :driveFolderId, uploadStatus = :status WHERE id = :fileId")
    suspend fun updateDriveInfo(fileId: Int, driveFileId: String, driveFolderId: String?, status: String = "UPLOADED")

    @Query("UPDATE recent_files SET uploadStatus = :status WHERE id = :fileId")
    suspend fun updateUploadStatus(fileId: Int, status: String)

    @Query("UPDATE recent_files SET isSynced = 1 WHERE id = :fileId")
    suspend fun markAsSynced(fileId: Int)

    @Query("SELECT * FROM recent_files WHERE userId = :userId ORDER BY date DESC")
    fun getFilesByUser(userId: String): LiveData<List<RecentFile>>

    @Query("SELECT * FROM recent_files WHERE userId = :userId AND uploadStatus = 'PENDING' ORDER BY date DESC")
    suspend fun getPendingUploads(userId: String): List<RecentFile>

    @Query("SELECT * FROM recent_files ORDER BY date DESC")
    fun getAllFiles(): LiveData<List<RecentFile>>

    @Query("SELECT * FROM recent_files WHERE id = :fileId")
    suspend fun getFileById(fileId: Int): RecentFile?

    @Query("SELECT * FROM recent_files WHERE driveFileId = :driveFileId LIMIT 1")
    suspend fun getFileByDriveId(driveFileId: String): RecentFile?

    @Query("DELETE FROM recent_files WHERE id = :fileId")
    suspend fun deleteById(fileId: Int)

    @Query("DELETE FROM recent_files WHERE driveFileId = :driveFileId")
    suspend fun deleteByDriveId(driveFileId: String)
}