package com.tapin.teacher.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Reads NFC taps from the student app (HCE) or programmed NFC tags (NDEF).
 *
 * --- HCE protocol (used by future student app) ---
 *   AID:  F0 54 41 50 49 4E 30 31     ("TAPIN01")
 *   SELECT command: 00 A4 04 00 08 <AID> 00
 *   Response: <ASCII student_number> 90 00
 *
 * --- NDEF fallback ---
 *   Reads first text/URI record. The payload is treated as student_number.
 *   Useful for testing with cheap NFC tags programmed via NXP TagWriter.
 *
 * --- Raw fallback ---
 *   Last resort: emits the tag's raw UID as hex. The teacher can then
 *   manually map UID -> student outside the app, or the backend may
 *   resolve UIDs in the future.
 */
class NfcReader(private val activity: Activity) {

    sealed interface Result {
        /** Got a usable student number (from HCE or NDEF). */
        data class Tapped(val studentNumber: String, val source: Source) : Result
        /** Could only read the raw card UID. */
        data class RawUid(val uid: String) : Result
        /** Tag couldn't be read at all. */
        data class Error(val message: String) : Result

        enum class Source { HCE, NDEF, RAW }
    }

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val isSupported: Boolean get() = adapter != null
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    private val _events = MutableSharedFlow<Result>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<Result> = _events.asSharedFlow()

    @Volatile private var listening = false

    fun start() {
        val a = adapter ?: return
        if (!a.isEnabled || listening) return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        // NFC_BARCODE constant only on API 19+, already covered by minSdk
        a.enableReaderMode(activity, ::onTag, flags, null)
        listening = true
    }

    fun stop() {
        if (!listening) return
        adapter?.disableReaderMode(activity)
        listening = false
    }

    private fun onTag(tag: Tag) {
        // 1) Try ISO-DEP (HCE)
        readHce(tag)?.let {
            _events.tryEmit(Result.Tapped(it, Result.Source.HCE))
            return
        }

        // 2) Try NDEF
        readNdef(tag)?.let {
            _events.tryEmit(Result.Tapped(it, Result.Source.NDEF))
            return
        }

        // 3) Raw UID fallback
        val uid = tag.id?.let { bytes -> bytes.joinToString("") { "%02X".format(it) } }
        if (!uid.isNullOrBlank()) {
            _events.tryEmit(Result.RawUid(uid))
        } else {
            _events.tryEmit(Result.Error("Tag couldn't be identified"))
        }
    }

    // ─── HCE: SELECT AID -> bytes + 9000 ───
    private fun readHce(tag: Tag): String? {
        val isoDep = IsoDep.get(tag) ?: return null
        return try {
            isoDep.connect()
            isoDep.timeout = 1500
            val resp = isoDep.transceive(SELECT_AID_APDU)
            if (resp.size < 2) return null
            val sw1 = resp[resp.size - 2].toInt() and 0xFF
            val sw2 = resp[resp.size - 1].toInt() and 0xFF
            if (sw1 != 0x90 || sw2 != 0x00) return null
            val payload = resp.copyOfRange(0, resp.size - 2)
            String(payload, Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } finally {
            try { isoDep.close() } catch (_: Exception) {}
        }
    }

    // ─── NDEF: prv text/uri zapis ───
    private fun readNdef(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null
        return try {
            ndef.connect()
            val msg = ndef.cachedNdefMessage ?: ndef.ndefMessage ?: return null
            for (rec in msg.records) {
                val text = parseRecord(rec.payload, rec.toMimeType()) ?: continue
                if (text.isNotBlank()) return text.trim()
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }

    private fun parseRecord(payload: ByteArray, mime: String?): String? {
        if (payload.isEmpty()) return null
        // NDEF text record: byte 0 = status, then language code, then UTF-8/16 text
        if (mime == null || mime.startsWith("text/")) {
            return runCatching {
                val status = payload[0].toInt() and 0xFF
                val langLen = status and 0x3F
                val isUtf16 = (status and 0x80) != 0
                val charset = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
                String(payload, 1 + langLen, payload.size - 1 - langLen, charset)
            }.getOrNull()
        }
        // Fallback: raw bytes as UTF-8
        return runCatching { String(payload, Charsets.UTF_8) }.getOrNull()
    }

    companion object {
        // 00 A4 04 00 08 F0 54 41 50 49 4E 30 31 00
        private val SELECT_AID_APDU = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x08,
            0xF0.toByte(), 0x54, 0x41, 0x50, 0x49, 0x4E, 0x30, 0x31,
            0x00
        )
    }
}
