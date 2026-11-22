package io.github.robex2005.limpid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import io.github.robex2005.limpid.core.Controller
import io.github.robex2005.limpid.core.Prefs
import io.github.robex2005.limpid.work.ReconnectWorker

class LimpidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_SETUP, getString(R.string.channel_setup), NotificationManager.IMPORTANCE_HIGH)
        )
        Controller.init(this)
        if (prefs.paired.value) ReconnectWorker.enqueue(this)
    }

    companion object {
        const val CHANNEL_SETUP = "setup"
        lateinit var prefs: Prefs
            private set
    }
}
