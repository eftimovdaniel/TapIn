package com.tapin.teacher.data.local
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// dao — site sql zapisi nad tabelata so lokalni prisustva
@Dao
interface AttendanceDao {

    // vmetni nov zapis; ako vekje postoi ist (po unique index) go ignorira i vraka -1
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: AttendanceEntity): Long

    // site zapisi so daden status (pr. pending) za batch sync
    @Query("SELECT * FROM attendance_pending WHERE status = :status ORDER BY localId ASC")
    suspend fun byStatus(status: Int = AttendanceEntity.STATUS_PENDING): List<AttendanceEntity>

    // live stream na zapisite za edna sesija — za ui listata
    @Query("SELECT * FROM attendance_pending WHERE sessionId = :sessionId ORDER BY localId DESC")
    fun streamForSession(sessionId: Long): Flow<List<AttendanceEntity>>

    // live broj na zapisi so daden status — za badge/sync bar
    @Query("SELECT COUNT(*) FROM attendance_pending WHERE status = :status")
    fun streamCountByStatus(status: Int = AttendanceEntity.STATUS_PENDING): Flow<Int>

    // ist broj, no kako edno-kratno chitanje
    @Query("SELECT COUNT(*) FROM attendance_pending WHERE status = :status")
    suspend fun countByStatus(status: Int = AttendanceEntity.STATUS_PENDING): Int

    // oznachi zapis kako uspeshno sinhroniziran i ischisti greshka
    @Query("UPDATE attendance_pending SET status = :status, syncedAtIso = :syncedAt, lastError = NULL WHERE localId = :id")
    suspend fun markSynced(id: Long, status: Int = AttendanceEntity.STATUS_SYNCED, syncedAt: String)

    // neuspeshen sync — ostanuva pending za probuvanje podocna, broi obid
    @Query("UPDATE attendance_pending SET status = :status, lastError = :err, attempts = attempts + 1 WHERE localId = :id")
    suspend fun markFailed(id: Long, err: String, status: Int = AttendanceEntity.STATUS_PENDING)

    // backend go otfrli zapisot (pr. nevaliden potpis) — nema povtorno probuvanje
    @Query("UPDATE attendance_pending SET status = :status, lastError = :err, attempts = attempts + 1 WHERE localId = :id")
    suspend fun markRejected(id: Long, err: String, status: Int = AttendanceEntity.STATUS_REJECTED)

    // izbrishi gi sinhroniziranite zapisi (chistenje na lokalnata baza)
    @Query("DELETE FROM attendance_pending WHERE status = :status")
    suspend fun deleteByStatus(status: Int = AttendanceEntity.STATUS_SYNCED): Int
}
