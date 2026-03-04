// ScanUploadEntity - encja Room + DAO dla kolejki uploadów | 2026-03-04
package com.clarion.scanner.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class UploadStatus { PENDING, UPLOADING, SUCCESS, FAILED }

@Entity(tableName = "scan_uploads")
data class ScanUploadEntity(
    @PrimaryKey val id: String,
    val imagePath: String,
    val status: UploadStatus = UploadStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val scanId: String? = null,
    val errorMessage: String? = null
)

@Dao
interface ScanUploadDao {

    @Query("SELECT * FROM scan_uploads ORDER BY createdAt DESC")
    fun getAllUploads(): Flow<List<ScanUploadEntity>>

    @Query("SELECT * FROM scan_uploads WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ScanUploadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(upload: ScanUploadEntity)

    @Update
    suspend fun update(upload: ScanUploadEntity)

    @Delete
    suspend fun delete(upload: ScanUploadEntity)

    @Query("DELETE FROM scan_uploads WHERE status = 'SUCCESS'")
    suspend fun deleteSuccessful()
}
