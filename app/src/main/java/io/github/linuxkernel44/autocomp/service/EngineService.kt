package io.github.linuxkernel44.autocomp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import io.github.linuxkernel44.autocomp.MainActivity
import io.github.linuxkernel44.autocomp.R
import io.github.linuxkernel44.autocomp.app
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class EngineService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            app.settings.update { it.copy(enabled = false) }
            stopSelf(); return START_NOT_STICKY
        }
        createChannel()
        startForeground(NOTIF_ID, notification(0, false), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        app.settings.update { it.copy(enabled = true) }
        if (job == null) {
            val engineJob = scope.launch { app.engine.run() }
            val notifJob = scope.launch {
                combine(app.engine.state, app.shizuku.state) { e, z -> e to z }.collect { (e, z) ->
                    val n = e.apps.count { it.compressed }
                    getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(n, z != io.github.linuxkernel44.autocomp.shizuku.ShizukuState.READY))
                }
            }
            val stopJob = scope.launch {
                app.settings.flow.collect { if (!it.enabled) { stopSelf() } }
            }
            job = scope.launch { joinAll(engineJob, notifJob, stopJob) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(apps: Int, waitingShizuku: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, EngineService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val text = if (waitingShizuku) getString(R.string.notif_waiting_shizuku) else resources.getQuantityString(R.plurals.notif_apps, apps, apps)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.action_turn_off), stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "engine"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "io.github.linuxkernel44.autocomp.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, EngineService::class.java))
        }

        fun stop(context: Context) {
            context.app.settings.update { it.copy(enabled = false) }
        }
    }
}
