package com.tapin.teacher.data

import android.content.Context
import com.tapin.teacher.data.api.ApiClient
import com.tapin.teacher.data.api.ApiException
import com.tapin.teacher.data.api.BulkAttendanceRequest
import com.tapin.teacher.data.api.TapRecord
import com.tapin.teacher.data.local.AppDatabase
import com.tapin.teacher.data.local.AttendanceDao
import com.tapin.teacher.data.local.AttendanceEntity
import kotlinx.coroutines.flow.Flow
import java.time.OffsetDateTime

/**
 * Repozitorium koj go raboti **offline-first** patterno:
 *
 *  1. Tap-ot vednash se vmetnuva vo Room (status = PENDING)
 *  2. Posle se obiduvame da go uploadame na server-ot
 *  3. Ako uspee → status = SYNCED
 *  4. Ako padне (mreza) → ostanuva PENDING; "Sync now" kopcheto ke probas pak
 *  5. Ako server-ot vrati 4xx (npr. nepoznat student) → status = REJECTED
 */
class AttendanceRepository(context: Context) {

    private val dao: AttendanceDao = AppDatabase.get(context).attendanceDao()

    /** Live stream na pending broj — za badge/sync bar UI. */
    fun pendingCount(): Flow<Int> = dao.streamCountByStatus(AttendanceEntity.STATUS_PENDING)

    /**
     * Vmetni eden NFC tap.
     *
     * Ako [signedPayload] e prosledena (od HCE servisot na studentot),
     * backend-ot ke ja validira i ke go najde studentot — ovaa apliкacija
     * ne treba ni da znae IDto. Toa овозможuva replay-protection и
     * server-side autentikacija na tapот.
     *
     * Ako [signedPayload] ne postoi (rachno vnesuvanje), frlame student-by-number
     * lookup ушte na klient.
     */
    suspend fun recordTap(
        sessionId: Long,
        studentNumber: String,
        signedPayload: String? = null,
        studentName: String? = null,
        tappedAtIso: String = OffsetDateTime.now().toString(),
    ): RecordOutcome {
        val localId = dao.insert(
            AttendanceEntity(
                sessionId = sessionId,
                studentNumber = studentNumber,
                studentName = studentName,
                tappedAtIso = tappedAtIso,
                signedPayload = signedPayload,
            )
        )

        if (localId == -1L) {
            return RecordOutcome.Duplicate(studentName ?: studentNumber)
        }

        return try {
            // Ako imame potpis — go pratame direktno; servero validira HMAC + ts
            if (signedPayload != null) {
                val resp = ApiClient.uploadAttendance(
                    BulkAttendanceRequest(
                        sessionId = sessionId,
                        records = listOf(
                            TapRecord(signedPayload = signedPayload, tappedAt = tappedAtIso)
                        )
                    )
                )
                val display = studentName?.takeIf { it.isNotBlank() } ?: studentNumber
                when {
                    resp.accepted > 0 -> {
                        dao.markSynced(localId, syncedAt = OffsetDateTime.now().toString())
                        RecordOutcome.Recorded(display, studentNumber)
                    }
                    resp.duplicates > 0 -> {
                        dao.markSynced(localId, syncedAt = OffsetDateTime.now().toString())
                        RecordOutcome.Duplicate(display)
                    }
                    resp.invalidSignatures > 0 -> {
                        dao.markRejected(localId, "Невалиден потпис")
                        RecordOutcome.Failed("Невалиден или истечен NFC потпис")
                    }
                    else -> {
                        dao.markRejected(localId, "Отфрлено")
                        RecordOutcome.Failed("Отфрлено")
                    }
                }
            } else {
                // Legacy / manuelno: lookup po broj na klient, posle toa upload
                val student = ApiClient.findStudentByNumber(studentNumber)
                val resp = ApiClient.uploadAttendance(
                    BulkAttendanceRequest(
                        sessionId = sessionId,
                        records = listOf(TapRecord(studentId = student.id, tappedAt = tappedAtIso))
                    )
                )
                when {
                    resp.accepted > 0 -> {
                        dao.markSynced(localId, syncedAt = OffsetDateTime.now().toString())
                        RecordOutcome.Recorded(student.fullName, student.studentNumber)
                    }
                    resp.duplicates > 0 -> {
                        dao.markSynced(localId, syncedAt = OffsetDateTime.now().toString())
                        RecordOutcome.Duplicate(student.fullName)
                    }
                    else -> {
                        dao.markRejected(localId, "Сите записи отфрлени")
                        RecordOutcome.Failed("Отфрлено")
                    }
                }
            }
        } catch (e: ApiException) {
            if (e.statusCode == 404) {
                dao.markRejected(localId, "Студентот не е најден")
                RecordOutcome.StudentNotFound(studentNumber)
            } else {
                dao.markFailed(localId, e.friendlyMessage)
                RecordOutcome.QueuedOffline(studentNumber)
            }
        } catch (e: Exception) {
            dao.markFailed(localId, e.message ?: "Мрежна грешка")
            RecordOutcome.QueuedOffline(studentNumber)
        }
    }

    /**
     * Bulk sync na site PENDING zapisi.
     * Vraka progress preku [onProgress] (synced, total).
     */
    suspend fun syncPending(
        onProgress: (synced: Int, total: Int) -> Unit = { _, _ -> },
    ): SyncResult {
        val pending = dao.byStatus(AttendanceEntity.STATUS_PENDING)
        if (pending.isEmpty()) return SyncResult(0, 0, 0)

        var synced = 0
        var rejected = 0
        var failed = 0

        // grupiraj po sessionId, batch upload
        val bySession = pending.groupBy { it.sessionId }
        var processed = 0

        bySession.forEach { (sessionId, list) ->
            // Izgradi TapRecord za sekoj pending zapis.
            //  - ako ima signedPayload (NFC tap) → go pratame potpisот direktno,
            //    serverot validira HMAC + ja naoga studentskata smetka (bez lookup)
            //  - inaku (rachno vnesuvanje) → resolve student number → id
            val records = mutableListOf<Pair<AttendanceEntity, TapRecord>>()
            for (e in list) {
                val signed = e.signedPayload
                if (!signed.isNullOrBlank()) {
                    records += e to TapRecord(signedPayload = signed, tappedAt = e.tappedAtIso)
                    continue
                }
                try {
                    val s = ApiClient.findStudentByNumber(e.studentNumber)
                    records += e to TapRecord(studentId = s.id, tappedAt = e.tappedAtIso)
                } catch (ex: ApiException) {
                    if (ex.statusCode == 404) {
                        dao.markRejected(e.localId, "Студентот не е најден")
                        rejected++
                    } else {
                        dao.markFailed(e.localId, ex.friendlyMessage)
                        failed++
                    }
                    processed++; onProgress(processed, pending.size)
                } catch (ex: Exception) {
                    dao.markFailed(e.localId, ex.message ?: "?")
                    failed++
                    processed++; onProgress(processed, pending.size)
                }
            }

            if (records.isEmpty()) return@forEach

            try {
                ApiClient.uploadAttendance(
                    BulkAttendanceRequest(
                        sessionId = sessionId,
                        records = records.map { (_, record) -> record }
                    )
                )
                // server prifaki ili otfri po sila — markirame site kako synced/duplicate
                val now = OffsetDateTime.now().toString()
                records.forEach { (e, _) ->
                    dao.markSynced(e.localId, syncedAt = now)
                    synced++
                    processed++; onProgress(processed, pending.size)
                }
            } catch (ex: ApiException) {
                records.forEach { (e, _) ->
                    dao.markFailed(e.localId, ex.friendlyMessage)
                    failed++
                    processed++; onProgress(processed, pending.size)
                }
            } catch (ex: Exception) {
                records.forEach { (e, _) ->
                    dao.markFailed(e.localId, ex.message ?: "?")
                    failed++
                    processed++; onProgress(processed, pending.size)
                }
            }
        }

        return SyncResult(synced = synced, rejected = rejected, failed = failed)
    }

    /** Briski sinhronizirani zapisi za da ne raste lokalnata baza. */
    suspend fun pruneSynced(): Int = dao.deleteByStatus(AttendanceEntity.STATUS_SYNCED)

    sealed interface RecordOutcome {
        data class Recorded(val name: String, val number: String?) : RecordOutcome
        data class Duplicate(val name: String) : RecordOutcome
        data class StudentNotFound(val number: String) : RecordOutcome
        data class QueuedOffline(val number: String) : RecordOutcome
        data class Failed(val message: String) : RecordOutcome
    }

    data class SyncResult(val synced: Int, val rejected: Int, val failed: Int)
}

