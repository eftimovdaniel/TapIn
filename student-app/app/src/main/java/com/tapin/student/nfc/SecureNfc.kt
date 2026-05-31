package com.tapin.student.nfc

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * NFC payload format koe go prajka studentskata aplikacija pri tap.
 *
 *   payload = "<studentNumber>|<unixSeconds>|<hmac16>"
 *
 *   hmac16 = HMAC-SHA256(SECRET, "<studentNumber>:<unixSeconds>") → hex → prvите 16 znaci
 *
 * Backend ja proveruva HMAC podpisоt + window ±60s na timestamp-ot.
 * Toa go spreчuva replay attacks (snimen NFC tag mozhe da se "playback-uje"
 * od drug ured posle nekolku sekundi → ne vazhi).
 *
 * Zaedничкi sekret e komajilan vo aplikaciite i vo backend-ot. Vo proizvodstvo
 * bi bil rotacioniran pri sekoja nova verzija.
 */
object SecureNfc {

    /** Споделен sekret — moora se istot kako na backend (config.NFC_SHARED_SECRET). */
    const val SHARED_SECRET = "TAPIN_NFC_SECRET_v1_2026"

    /** Plotki gradi payload za HCE odgovorot. */
    fun build(studentNumber: String, nowSeconds: Long = System.currentTimeMillis() / 1000): String {
        val msg = "$studentNumber:$nowSeconds"
        val mac = hmacSha256Hex(SHARED_SECRET, msg).take(16)
        return "$studentNumber|$nowSeconds|$mac"
    }

    private fun hmacSha256Hex(secret: String, msg: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal(msg.toByteArray(Charsets.UTF_8))
        return raw.joinToString("") { "%02x".format(it) }
    }
}
