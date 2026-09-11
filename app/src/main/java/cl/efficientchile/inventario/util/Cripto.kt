package cl.efficientchile.inventario.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado local del token de sesion.
 *
 * El token es, a efectos practicos, la contrasena del vendedor: quien lo
 * tenga puede vender y consultar en su nombre hasta que expire. Guardado tal
 * cual en DataStore, queda en un archivo del telefono que se lee entero con
 * el aparato rooteado, con un respaldo mal configurado o con el terminal en
 * la mano cinco minutos.
 *
 * La clave de cifrado se genera dentro del Keystore de Android y nunca sale
 * de ahi: no vive en el codigo ni en el archivo, asi que copiar el archivo a
 * otro telefono no sirve de nada. En equipos con chip seguro la clave queda
 * en hardware.
 *
 * No se usa EncryptedSharedPreferences a proposito: obligaria a sumar la
 * dependencia androidx.security-crypto y a cambiar toda la lectura, que hoy
 * son Flow de DataStore. Aca se cifra solo el valor y el resto del codigo
 * sigue igual.
 *
 * Requiere API 23 o superior (Android 6, 2015).
 */
object Cripto {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "inventario_token_v1"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12

    /** Marca al principio del texto cifrado, para distinguirlo de uno antiguo en claro. */
    private const val MARCA = "v1:"

    private fun clave(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Sin exigir que el usuario se autentique: el vendedor abre la
                // app decenas de veces al dia y pedirle huella en cada una
                // termina con el token anotado en un papel.
                .setUserAuthenticationRequired(false)
                .build()
        )
        return gen.generateKey()
    }

    /**
     * Cifra un texto. Si algo falla devuelve null: el llamador decide si
     * prefiere no guardar nada antes que guardarlo en claro.
     */
    fun cifrar(texto: String): String? = try {
        val c = Cipher.getInstance(TRANSFORMACION)
        c.init(Cipher.ENCRYPT_MODE, clave())
        val datos = c.doFinal(texto.toByteArray(Charsets.UTF_8))
        // El IV cambia en cada cifrado y viaja delante del dato; no es secreto.
        val junto = c.iv + datos
        MARCA + Base64.encodeToString(junto, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    /**
     * Descifra lo que produjo [cifrar].
     *
     * Un valor sin la marca es de una version anterior, que guardaba el token
     * en claro: se devuelve tal cual para no obligar al vendedor a iniciar
     * sesion de nuevo al actualizar la app. La proxima escritura ya queda
     * cifrada.
     */
    fun descifrar(guardado: String): String? {
        if (!guardado.startsWith(MARCA)) return guardado
        return try {
            val bruto = Base64.decode(guardado.removePrefix(MARCA), Base64.NO_WRAP)
            if (bruto.size <= IV_BYTES) return null
            val c = Cipher.getInstance(TRANSFORMACION)
            c.init(
                Cipher.DECRYPT_MODE,
                clave(),
                GCMParameterSpec(TAG_BITS, bruto, 0, IV_BYTES)
            )
            String(c.doFinal(bruto, IV_BYTES, bruto.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            // Clave borrada (el usuario limpio los datos) o dato alterado.
            // Sin token, la app manda a iniciar sesion, que es lo correcto.
            null
        }
    }
}
