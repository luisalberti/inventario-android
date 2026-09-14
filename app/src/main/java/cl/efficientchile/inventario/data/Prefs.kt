package cl.efficientchile.inventario.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.almacen by preferencesDataStore(name = "sesion")

/**
 * La sesion guardada en el celular: a que servidor apunta, con que token y
 * como quien.
 *
 * Se guarda en DataStore y no en SharedPreferences porque las lecturas son
 * flujos: la pantalla reacciona sola cuando el token cambia, sin tener que
 * acordarse de refrescar nada.
 *
 * El token es de sesion, no la contrasena. La contrasena no se guarda en
 * ninguna parte del telefono: si alguien pierde el aparato, lo que se
 * revoca es la sesion desde el panel, no hay que cambiarle la clave a todos
 * los vendedores.
 */
class Prefs(private val ctx: Context) {

    private object K {
        val TOKEN = stringPreferencesKey("token")
        val URL = stringPreferencesKey("base_url")
        val USER = stringPreferencesKey("username")
    }

    val token: Flow<String?> = ctx.almacen.data.map { it[K.TOKEN] }
    val baseUrl: Flow<String?> = ctx.almacen.data.map { it[K.URL] }
    val username: Flow<String?> = ctx.almacen.data.map { it[K.USER] }

    suspend fun guardarLogin(url: String, token: String, usuario: String) {
        ctx.almacen.edit {
            it[K.URL] = url
            it[K.TOKEN] = token
            it[K.USER] = usuario
        }
    }

    /**
     * Cierra sesion pero deja la URL.
     *
     * Es a proposito: el vendedor que se equivoco de clave no tiene por que
     * volver a teclear el dominio completo en un celular.
     */
    suspend fun cerrarSesion() {
        ctx.almacen.edit {
            it.remove(K.TOKEN)
            it.remove(K.USER)
        }
    }
}
