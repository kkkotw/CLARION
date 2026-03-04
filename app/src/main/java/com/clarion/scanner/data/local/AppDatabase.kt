// AppDatabase - Room database singleton | 2026-03-04
package com.clarion.scanner.data.local

import android.content.Context
import androidx.room.*

class UploadStatusConverter {
    @TypeConverter
    fun fromStatus(status: UploadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): UploadStatus =
        runCatching { UploadStatus.valueOf(value) }.getOrDefault(UploadStatus.FAILED)
}

@Database(
    entities = [ScanUploadEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(UploadStatusConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scanUploadDao(): ScanUploadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "clarion_scanner.db"
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
