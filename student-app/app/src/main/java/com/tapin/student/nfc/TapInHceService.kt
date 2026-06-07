package com.tapin.student.nfc
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 *   Sekoj tap (uspeshen ili neuspeshen) se emitira na [tapEvents] SharedFlow
 *   kako [TapEvent], za UI-ot da pokaze "Запишано!" ili "Тап не успеа" pri
 *   slednoto obnovuvanje na ekranot (HCE servisot raboti i koga aplikacijata
 *   ne e otvorena).
 */
class TapInHceService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return STATUS_FAILURE

        if (!isSelectAid(commandApdu)) {
            Log.d(TAG, "Unknown APDU: ${commandApdu.toHex()}")
            return STATUS_FILE_NOT_FOUND
        }

        if (!aidMatches(commandApdu)) {
            Log.d(TAG, "AID mismatch: ${commandApdu.toHex()}")
            return STATUS_FILE_NOT_FOUND
        }

        val number = StudentNumberStore.get(applicationContext)
        if (number.isNullOrBlank()) {
            Log.w(TAG, "SELECT received but no student number stored (logged out?)")
            // Nasheto AID se sovpadna, no nema kreditencijali → real tap failure.
            _tapEvents.tryEmit(TapEvent.Failure(System.currentTimeMillis(), Reason.NOT_LOGGED_IN))
            return STATUS_NOT_LOGGED_IN
        }
        val name = StudentNumberStore.getName(applicationContext)

        // Sigurnosen potpisan payload (HMAC + timestamp) — sprechuva replay i
        // edinstveno backend-ot mozhe da go validira so spodeleniot kluch.
        // Spec 3.2.3: payload sodrzhi student_id + student_name (v2 format).
        val signed = SecureNfc.build(number, studentName = name)
        val payload = signed.toByteArray(Charsets.UTF_8)
        Log.i(TAG, "SELECT ok → signed payload (${payload.size} bytes, hasName=${!name.isNullOrBlank()})")

        // Notifyaj UI deka tapot e uspeshen
        _tapEvents.tryEmit(TapEvent.Success(System.currentTimeMillis()))

        return payload + STATUS_OK
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "Deactivated, reason=$reason")
    }

    // ─────────── helpers ───────────

    private fun isSelectAid(apdu: ByteArray): Boolean {
        // CLA=00, INS=A4, P1=04, P2=00 — SELECT BY NAME
        return apdu.size >= 5 &&
            apdu[0] == 0x00.toByte() &&
            apdu[1] == 0xA4.toByte() &&
            apdu[2] == 0x04.toByte() &&
            apdu[3] == 0x00.toByte()
    }

    private fun aidMatches(apdu: ByteArray): Boolean {
        // Lc at index 4, then AID bytes follow
        if (apdu.size < 5) return false
        val lc = apdu[4].toInt() and 0xFF
        if (lc != AID.size) return false
        if (apdu.size < 5 + lc) return false
        for (i in AID.indices) {
            if (apdu[5 + i] != AID[i]) return false
        }
        return true
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02X".format(it) }

    companion object {
        private const val TAG = "TapInHce"

        // F0 54 41 50 49 4E 30 31  ("TAPIN01")
        private val AID = byteArrayOf(
            0xF0.toByte(), 0x54, 0x41, 0x50, 0x49, 0x4E, 0x30, 0x31
        )

        private val STATUS_OK            = byteArrayOf(0x90.toByte(), 0x00)
        private val STATUS_NOT_LOGGED_IN = byteArrayOf(0x90.toByte(), 0x02)
        private val STATUS_FILE_NOT_FOUND= byteArrayOf(0x6A.toByte(), 0x82.toByte())
        private val STATUS_FAILURE       = byteArrayOf(0x6F.toByte(), 0x00)

        /** Emitira [TapEvent] (uspeh ili neuspeh) od HCE → UI. */
        private val _tapEvents = MutableSharedFlow<TapEvent>(
            replay = 1,
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val tapEvents: SharedFlow<TapEvent> = _tapEvents.asSharedFlow()
    }

    /** Pricina za neuspeshen tap — za prilagoden UI tekst. */
    enum class Reason { NOT_LOGGED_IN }

    /** Rezultat od eden NFC tap protiv nastavnichkiot telefon. */
    sealed interface TapEvent {
        val at: Long

        data class Success(override val at: Long) : TapEvent
        data class Failure(override val at: Long, val reason: Reason) : TapEvent
    }
}
