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

    @Query("UPDATE recent_files SET isSynced = 1 WHERE id = :fileId")
    suspend fun markAsSynced(fileId: Int)

    @Query("SELECT * FROM recent_files ORDER BY date DESC")
    fun getAllFiles(): LiveData<List<RecentFile>>

    @Query("SELECT * FROM recent_files WHERE id = :fileId")
    suspend fun getFileById(fileId: Int): RecentFile?
}