package io.github.linuxkernel44.autocomp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.linuxkernel44.autocomp.app

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = context.app.settings.value
        if (s.enabled && s.startOnBoot) runCatching { EngineService.start(context) }
    }
}
