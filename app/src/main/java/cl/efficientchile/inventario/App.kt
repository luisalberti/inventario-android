package cl.efficientchile.inventario

import android.app.Application
import cl.efficientchile.inventario.data.Prefs
import cl.efficientchile.inventario.data.Repo

/**
 * Una sola instancia de Prefs y de Repo para toda la app.
 *
 * Sin esto, cada pantalla que necesitara el repositorio se armaba el suyo, y
 * con el se armaba otro cliente HTTP con su propio pool de conexiones. En un
 * celular de meson eso se nota.
 */
class App : Application() {
    val prefs: Prefs by lazy { Prefs(this) }
    val repo: Repo by lazy { Repo(prefs) }
}
