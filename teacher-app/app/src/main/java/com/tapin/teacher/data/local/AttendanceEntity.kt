package com.tapin.teacher.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lokalen zapis za eden NFC tap.
 *
 * Zhivee samo na uredot. Sinhronizira se kon backend kade so se prajka i tamu.
 *
 * status:
 *   0 = PENDING (chekаt za sync)
 *   1 = SYNCED (uspeshno kachen)
 *   2 = REJECTED (backend ne go prifati — pr. nepoznat student)
 *
 * Zachuvuvame i `studentNumber` taka chto site posto offline tap-i mozhat
 * da se uploadат i posle koga imame net (resolve to studentId pri sync).
 */
@Entity(
    tableName = "attendance_pending",
    indices = [
        Index("sessionId"),
        Index("status"),
        Index(value = ["sessionId", "studentNumber"], unique = true) // local-level dedup
    ]
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val sessionId: Long,
    val studentNumber: String,
    val studentName: String? = null,
    val tappedAtIso: String,
    val status: Int = STATUS_PENDING,
    val lastError: String? = null,
    val attempts: Int = 0,
    val syncedAtIso: String? = null,
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_SYNCED  = 1
        const val STATUS_REJECTED = 2
    }
}
