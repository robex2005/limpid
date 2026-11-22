package io.github.robex2005.limpid

import android.app.Application
import io.github.robex2005.limpid.core.Controller
import io.github.robex2005.limpid.core.Prefs

class LimpidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        Controller.init(this)
    }

    companion object {
        lateinit var prefs: Prefs
            private set
    }
}
