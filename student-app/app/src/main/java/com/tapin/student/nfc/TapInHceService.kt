package com.tapin.student.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Card-emulation service for TapIn.
 *
 * --- Protocol ---
 *   Reader (teacher app) sends:
 *     SELECT AID:  00 A4 04 00 08 F0 54 41 50 49 4E 30 31 [Le?]
 *
 *   We respond with:
 *     <ASCII student_number bytes...> 90 00      (success)
 *     90 02                                      (no student number stored — locked / logged out)
 *     6A 82                                      (file not found — wrong AID)
 *     6F 00                                      (general failure)
 *
 * The teacher app's NfcReader strips the trailing 9000 and treats the
 * remaining bytes as UTF-8 text → that becomes the student number.
 *
 * --- UI feedback ---
 *   Sekoj uspesh tap se emitira na [tapEvents] SharedFlow,
 *   za UI-ot da pokaze "Запишано!" pri slednoto обновуvanje na ekranot
 *   (HCE servisot raboti i koga aplikacijaata ne e otvorena).
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
            return STATUS_NOT_LOGGED_IN
        }
        val name = StudentNumberStore.getName(applicationContext)

        // Sigurnosen potpisан payload (HMAC + timestamp) — sprečuva replay i
        // edinstvenо backend-ot mozhe da go validira со споделениoт klu5.
        // Spec 3.2.3: payload sodrzhi student_id + student_name (v2 format).
        val signed = SecureNfc.build(number, studentName = name)
        val payload = signed.toByteArray(Charsets.UTF_8)
        Log.i(TAG, "SELECT ok → signed payload (${payload.size} bytes, hasName=${!name.isNullOrBlank()})")

        // Notifyaj UI deka tapоt e uspesheн
        _tapEvents.tryEmit(System.currentTimeMillis())

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

        /** Emitira timestamp ms koga uspeshno e ispraten potpis (HCE → UI). */
        private val _tapEvents = MutableSharedFlow<Long>(
            replay = 1,
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val tapEvents: SharedFlow<Long> = _tapEvents.asSharedFlow()
    }
}
