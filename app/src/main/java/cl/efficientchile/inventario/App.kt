package cl.efficientchile.inventario

import android.app.Application
import cl.efficientchile.inventario.data.Prefs
import cl.efficientchile.inventario.data.Repo

class App : Application() {
    lateinit var prefs: Prefs
    lateinit var repo: Repo

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repo = Repo(prefs)
    }
}
