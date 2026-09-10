package cl.efficientchile.inventario.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * La camara entrega fotos de varios MB. Subir eso por 4G desde el mesón es
 * lento y a veces se corta, asi que la foto se reduce a 1600 px de lado
 * mayor antes de enviarla: sigue siendo mas que suficiente para leer el
 * monto y la fecha de una transferencia, y pesa alrededor de 300 KB.
 */
object Fotos {

    private const val MAX_LADO = 1600
    private const val CALIDAD = 82

    fun comprimir(origen: File, destino: File): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(origen.absolutePath, bounds)

        // inSampleSize baja la memoria del decode; luego se afina con escalar().
        var sample = 1
        val mayor = max(bounds.outWidth, bounds.outHeight)
        while (mayor / sample > MAX_LADO * 2) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeFile(origen.absolutePath, opts)
            ?: throw IllegalStateException("No se pudo leer la foto tomada")

        bmp = rotarSegunExif(origen, bmp)
        bmp = escalar(bmp, MAX_LADO)

        FileOutputStream(destino).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, CALIDAD, out)
        }
        bmp.recycle()
        return destino
    }

    /** Decodifica para mostrar en pantalla, sin cargar la imagen completa. */
    fun decodificar(archivo: File, maxLado: Int = 1400): Bitmap? {
        if (!archivo.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.absolutePath, bounds)
        var sample = 1
        val mayor = max(bounds.outWidth, bounds.outHeight)
        while (mayor / sample > maxLado * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(archivo.absolutePath, opts)
    }

    /**
     * CameraX guarda la orientacion en los metadatos EXIF en vez de girar los
     * pixeles. Si no se aplica aca, la foto se sube acostada.
     */
    @Suppress("DEPRECATION")
    private fun rotarSegunExif(archivo: File, bmp: Bitmap): Bitmap {
        val grados = try {
            when (
                android.media.ExifInterface(archivo.absolutePath)
                    .getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL,
                    )
            ) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Throwable) {
            0f
        }
        if (grados == 0f) return bmp
        val m = Matrix().apply { postRotate(grados) }
        val girada = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (girada != bmp) bmp.recycle()
        return girada
    }

    private fun escalar(bmp: Bitmap, maxLado: Int): Bitmap {
        val mayor = max(bmp.width, bmp.height)
        if (mayor <= maxLado) return bmp
        val f = maxLado.toFloat() / mayor
        val escalada = Bitmap.createScaledBitmap(
            bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true,
        )
        if (escalada != bmp) bmp.recycle()
        return escalada
    }
}
