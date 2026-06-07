package com.tapin.student.nfc
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * NFC payload format koe go prajka studentskata aplikacija pri tap.
 *
 * Backend ja proveruva HMAC podpisot + window ±60s na timestamp-ot.
 * Toa go sprechuva replay attacks (snimen NFC tag mozhe da se "playback-uje"
 * od drug ured posle nekolku sekundi → ne vazhi).
 *
 * Zaednichki secret e kompajliran vo aplikaciite i vo backend-ot. Vo proizvodstvo
 * bi bil rotacioniran pri sekoja nova verzija.
 */
object SecureNfc {

    /** Споделен sekret — moora se istot kako na backend (config.NFC_SHARED_SECRET). */
    const val SHARED_SECRET = "TAPIN_NFC_SECRET_v1_2026"

    /**
     * Gradi v2 payload so studentName (spec 3.2.3).
     * Ako [studentName] e prazno, padame na v1 format (samo broj) za kompatibilnost.
     */
    fun build(
        studentNumber: String,
        studentName: String? = null,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
    ): String {
        val name = studentName?.let(::sanitizeName).orEmpty()
        if (name.isBlank()) {
            // Legacy v1 — zachuvani backward compat ako name ne e dostapno
            val msg = "$studentNumber:$nowSeconds"
            val mac = hmacSha256Hex(SHARED_SECRET, msg).take(16)
            return "$studentNumber|$nowSeconds|$mac"
        }
        val msg = "v2:$studentNumber:$name:$nowSeconds"
        val mac = hmacSha256Hex(SHARED_SECRET, msg).take(16)
        return "v2|$studentNumber|$name|$nowSeconds|$mac"
    }

    /** Pipe (`|`) e razdvojuvac vo payload-ot — go zamenuvame so razmak. */
    private fun sanitizeName(name: String): String =
        name.trim().replace('|', ' ').replace(Regex("\\s+"), " ")

    private fun hmacSha256Hex(secret: String, msg: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal(msg.toByteArray(Charsets.UTF_8))
        return raw.joinToString("") { "%02x".format(it) }
    }
}
