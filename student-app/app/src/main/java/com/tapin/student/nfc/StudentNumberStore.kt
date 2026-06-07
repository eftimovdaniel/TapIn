package com.tapin.student.nfc
import android.content.Context

// chuva broj i ime na studentot vo sharedpreferences za da gi chita hce servisot
object StudentNumberStore {

    // ime na prefs fajlot i klucevite pod koi se chuva sekoe pole
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

    // vrakja zachuvaniot broj na student (ili null ako nema)
    fun get(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NUMBER, null)
            ?.takeIf { it.isNotBlank() }

    // vrakja zachuvanoto ime na student (ili null ako nema)
    fun getName(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NAME, null)
            ?.takeIf { it.isNotBlank() }
}
