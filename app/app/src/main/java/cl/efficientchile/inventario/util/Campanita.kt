package cl.efficientchile.inventario.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Campanilla de caja registradora al cerrar una venta.
 *
 * Se sintetiza en el momento en vez de traer un archivo de audio: asi el
 * repositorio no necesita ningun binario y el sonido es identico en todos
 * los celulares. Son tres golpes con parciales inarmonicos, que es lo que
 * hace que un tono suene a campana y no a pitido.
 */
object Campanita {

    private const val SR = 44100
    private const val DUR = 1.6

    /** Suena en segundo plano; nunca bloquea ni revienta la pantalla. */
    fun sonar() {
        thread(isDaemon = true) {
            try {
                reproducir(generar())
            } catch (_: Throwable) {
                // Si el celular tiene el audio ocupado o silenciado, la venta
                // igual quedo registrada. El sonido es un extra, no un paso.
            }
        }
    }

    private fun generar(): ShortArray {
        val n = (SR * DUR).toInt()
        val buf = ShortArray(n)
        val golpes = doubleArrayOf(0.0, 0.16, 0.34)          // tilin-tilin-tilin
        val mult = doubleArrayOf(1.0, 2.0, 2.76, 5.40, 8.93) // parciales de campana
        val amp = doubleArrayOf(1.0, 0.62, 0.42, 0.22, 0.10)
        val f0 = 1174.7                                      // Re6

        for (i in 0 until n) {
            val t = i.toDouble() / SR
            var s = 0.0
            for (g in golpes) {
                val td = t - g
                if (td < 0.0) continue
                val env = exp(-td * 5.2)
                if (env < 0.001) continue
                for (k in mult.indices) {
                    s += amp[k] * env * sin(2.0 * PI * f0 * mult[k] * td)
                }
            }
            // Desvanecido final para que no termine con un chasquido.
            val fade = if (t > DUR - 0.05) (DUR - t) / 0.05 else 1.0
            buf[i] = (s * fade * 4200.0).coerceIn(-32000.0, 32000.0).toInt().toShort()
        }
        return buf
    }

    private fun reproducir(buf: ShortArray) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buf.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(buf, 0, buf.size)
        track.play()
        Thread.sleep(((DUR + 0.2) * 1000).toLong())
        try { track.stop() } catch (_: Throwable) { }
        track.release()
    }
}
