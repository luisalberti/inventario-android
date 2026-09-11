package cl.efficientchile.inventario.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cl.efficientchile.inventario.util.Cripto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore(name = "inventario_prefs")

class Prefs(private val ctx: Context) {

    private val KEY_URL = stringPreferencesKey("base_url")
    private val KEY_TOKEN = stringPreferencesKey("token")
    private val KEY_USER = stringPreferencesKey("username")
    private val KEY_EMPRESA = stringPreferencesKey("empresa")
    private val KEY_EXIGIR_NUM = stringPreferencesKey("exigir_numero")
    private val KEY_EXIGIR_FOTO = stringPreferencesKey("exigir_foto")

    val baseUrl: Flow<String?> = ctx.ds.data.map { it[KEY_URL] }

    /**
     * El token sale descifrado. En disco esta cifrado con una clave del
     * Keystore de Android: antes se guardaba tal cual, y quien tuviera el
     * telefono rooteado —o un respaldo— podia leerlo y vender en nombre del
     * vendedor hasta que expirara.
     */
    val token: Flow<String?> = ctx.ds.data.map { prefs ->
        prefs[KEY_TOKEN]?.let { Cripto.descifrar(it) }
    }

    val username: Flow<String?> = ctx.ds.data.map { it[KEY_USER] }
    val empresa: Flow<String?> = ctx.ds.data.map { it[KEY_EMPRESA] }

    /** Si el vendedor debe teclear el numero de boleta. Por defecto si. */
    val exigirNumero: Flow<Boolean> = ctx.ds.data.map { it[KEY_EXIGIR_NUM] != "0" }

    /** "nunca" | "transferencia" | "siempre". Lo define el cliente en Ajustes. */
    val exigirFoto: Flow<String> = ctx.ds.data.map { it[KEY_EXIGIR_FOTO] ?: "transferencia" }

    suspend fun guardarLogin(
        url: String,
        token: String,
        user: String,
        empresa: String? = null,
        exigirNumero: Boolean = true,
        exigirFoto: String = "transferencia",
    ) {
        // Si el cifrado falla no se guarda el token en claro: es preferible
        // que el vendedor tenga que iniciar sesion otra vez a dejar la llave
        // del sistema tirada en un archivo del telefono.
        val cifrado = Cripto.cifrar(token)

        ctx.ds.edit {
            it[KEY_URL] = url
            if (cifrado != null) it[KEY_TOKEN] = cifrado else it.remove(KEY_TOKEN)
            it[KEY_USER] = user
            it[KEY_EMPRESA] = empresa ?: ""
            it[KEY_EXIGIR_NUM] = if (exigirNumero) "1" else "0"
            it[KEY_EXIGIR_FOTO] = exigirFoto
        }
    }

    suspend fun cerrarSesion() {
        ctx.ds.edit {
            it.remove(KEY_TOKEN)
            it.remove(KEY_USER)
        }
    }
}
