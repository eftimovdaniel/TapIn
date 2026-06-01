package com.tapin.student.nfc

import android.content.Context

/**
 * Lightweight, synchronous store za student broj + ime — koristen od HCE.
 *
 * HCE callbacks (`processCommandApdu`) MUST return the response within a few
 * hundred milliseconds and run on the main thread. This rules out DataStore
 * (suspend) or any network call. We mirror the student number/name into plain
 * SharedPreferences whenever the user logs in / out, and read it from there
 * inside the HCE service.
 */
object StudentNumberStore {

    private const val PREFS = "tapin_hce"
    private const val KEY_NUMBER = "student_number"
    private const val KEY_NAME = "student_name"

    /** Postavi (ili izbrishi) broj + ime. Ako e null/blank → brishe. */
    fun set(context: Context, number: String?, name: String? = null) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (number.isNullOrBlank()) {
                remove(KEY_NUMBER); remove(KEY_NAME)
            } else {
                putString(KEY_NUMBER, number.trim())
                if (name.isNullOrBlank()) remove(KEY_NAME) else putString(KEY_NAME, name.trim())
            }
            apply()
        }
    }

    fun get(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NUMBER, null)
            ?.takeIf { it.isNotBlank() }

    fun getName(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NAME, null)
            ?.takeIf { it.isNotBlank() }
}
