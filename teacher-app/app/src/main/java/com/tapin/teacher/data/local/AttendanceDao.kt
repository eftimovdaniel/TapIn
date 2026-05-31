package com.tapin.teacher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: AttendanceEntity): Long

    @Query("SELECT * FROM attendance_pending WHERE status = :status ORDER BY localId ASC")
    suspend fun byStatus(status: Int = AttendanceEntity.STATUS_PENDING): List<AttendanceEntity>

    @Query("SELECT * FROM attendance_pending WHERE sessionId = :sessionId ORDER BY localId DESC")
    fun streamForSession(sessionId: Long): Flow<List<AttendanceEntity>>

    @Query("SELECT COUNT(*) FROM attendance_pending WHERE status = :status")
    fun streamCountByStatus(status: Int = AttendanceEntity.STATUS_PENDING): Flow<Int>

    @Query("SELECT COUNT(*) FROM attendance_pending WHERE status = :status")
    suspend fun countByStatus(status: Int = AttendanceEntity.STATUS_PENDING): Int

    @Query("UPDATE attendance_pending SET status = :status, syncedAtIso = :syncedAt, lastError = NULL WHERE localId = :id")
    suspend fun markSynced(id: Long, status: Int = AttendanceEntity.STATUS_SYNCED, syncedAt: String)

    @Query("UPDATE attendance_pending SET status = :status, lastError = :err, attempts = attempts + 1 WHERE localId = :id")
    suspend fun markFailed(id: Long, err: String, status: Int = AttendanceEntity.STATUS_PENDING)

    @Query("UPDATE attendance_pending SET status = :status, lastError = :err, attempts = attempts + 1 WHERE localId = :id")
    suspend fun markRejected(id: Long, err: String, status: Int = AttendanceEntity.STATUS_REJECTED)

    @Query("DELETE FROM attendance_pending WHERE status = :status")
    suspend fun deleteByStatus(status: Int = AttendanceEntity.STATUS_SYNCED): Int
}
