package io.github.robex2005.limpid.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.robex2005.limpid.LimpidApp
import io.github.robex2005.limpid.adb.Bootstrapper
import io.github.robex2005.limpid.core.Controller
import io.github.robex2005.limpid.core.Mode
import java.util.concurrent.TimeUnit

/** Brings the helper back after a reboot or app update, as soon as the phone is on Wi-Fi. */
class ReconnectWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = LimpidApp.prefs
        if (!prefs.paired.value || prefs.mode.value == Mode.OFF) return Result.success()
        if (Controller.refresh()) return Result.success()
        return if (Bootstrapper.run(applicationContext).isSuccess) Result.success()
        else if (runAttemptCount < 8) Result.retry() else Result.failure()
    }

    companion object {
        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<ReconnectWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("reconnect", ExistingWorkPolicy.KEEP, req)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ReconnectWorker.enqueue(context)
        }
    }
}
