package cl.efficientchile.inventario.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    val token: Flow<String?> = ctx.ds.data.map { it[KEY_TOKEN] }
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
        ctx.ds.edit {
            it[KEY_URL] = url
            it[KEY_TOKEN] = token
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
