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

    val baseUrl: Flow<String?> = ctx.ds.data.map { it[KEY_URL] }
    val token: Flow<String?> = ctx.ds.data.map { it[KEY_TOKEN] }
    val username: Flow<String?> = ctx.ds.data.map { it[KEY_USER] }

    suspend fun guardarLogin(url: String, token: String, user: String) {
        ctx.ds.edit {
            it[KEY_URL] = url
            it[KEY_TOKEN] = token
            it[KEY_USER] = user
        }
    }

    suspend fun cerrarSesion() {
        ctx.ds.edit {
            it.remove(KEY_TOKEN)
            it.remove(KEY_USER)
        }
    }
}
