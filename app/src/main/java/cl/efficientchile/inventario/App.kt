package cl.efficientchile.inventario

import android.app.Application
import cl.efficientchile.inventario.data.Prefs
import cl.efficientchile.inventario.data.Repo

/**
 * Contenedor de las dos piezas que viven mientras vive la app.
 *
 * Prefs guarda la sesion en disco y Repo habla con el servidor. Las dos tienen
 * que ser unicas: si cada pantalla creara su propio Repo, cada una armaria su
 * propio cliente HTTP y se perderia el Retrofit ya construido que Repo cachea
 * por URL.
 *
 * Se crean con "by lazy" y no en onCreate() para que se construyan la primera
 * vez que alguien las pide. Asi el arranque de la app no paga el costo de
 * abrir DataStore antes de que haga falta, y no hay ventana en la que la
 * propiedad exista pero todavia no este inicializada.
 */
class App : Application() {

    val prefs: Prefs by lazy { Prefs(this) }

    val repo: Repo by lazy { Repo(prefs) }
}
