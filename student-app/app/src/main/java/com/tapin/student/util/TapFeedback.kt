package com.tapin.student.util
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

//Vibracija + zvuk koga studentot uspeshno tapna telefon do nastavnikot.
object TapFeedback {

    private fun vibrator(ctx: Context): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun success(ctx: Context) {
        vibrator(ctx)?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // dvostruka kratka vibracija — distinktiven feedback
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 40), -1))
            } else {
                @Suppress("DEPRECATION") v.vibrate(longArrayOf(0, 60, 80, 40), -1)
            }
        }
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 200)
        }
    }

    fun failure(ctx: Context) {
        vibrator(ctx)?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // podolga edinechna vibracija — distinktiven "greshka" feedback
                v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") v.vibrate(300)
            }
        }
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone.startTone(ToneGenerator.TONE_SUP_ERROR, 350)
        }
    }
}
