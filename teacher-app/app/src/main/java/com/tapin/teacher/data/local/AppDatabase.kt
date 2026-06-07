package com.tapin.teacher.data.local
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// room baza za lokalno chuvanje na prisustva (offline red za sync)
@Database(
    entities = [AttendanceEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun attendanceDao(): AttendanceDao

    companion object {
        // edna ista instanca za cela aplikacija (singleton)
        @Volatile private var INSTANCE: AppDatabase? = null

        // vrakja postoechka instanca ili gradi nova (thread-safe)
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(context.applicationContext, AppDatabase::class.java, "tapin_teacher.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
