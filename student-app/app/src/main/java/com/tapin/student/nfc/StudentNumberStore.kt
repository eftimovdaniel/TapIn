package com.tapin.student.nfc

import android.content.Context

/**
 * Lightweight, synchronous store for the student number used by HCE.
 *
 * HCE callbacks (`processCommandApdu`) MUST return the response within a few
 * hundred milliseconds and run on the main thread. This rules out DataStore
 * (suspend) or any network call. We mirror the student number into plain
 * SharedPreferences whenever the user logs in / out, and read it from there
 * inside the HCE service.
 */
object StudentNumberStore {

    private const val PREFS = "tapin_hce"
    private const val KEY = "student_number"

    fun set(context: Context, value: String?) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY) else putString(KEY, value.trim())
            apply()
        }
    }

    fun get(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.takeIf { it.isNotBlank() }
}
