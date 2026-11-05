package io.github.linuxkernel44.autocomp.spike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** adb shell am broadcast -a io.github.linuxkernel44.autocomp.ATTACH --ei session N */
class SpikeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = intent.getIntExtra("session", 0)
        when (intent.action) {
            "io.github.linuxkernel44.autocomp.ATTACH" -> SpikeCompressor.attach(s)
            "io.github.linuxkernel44.autocomp.RELEASE" -> SpikeCompressor.release(s)
        }
    }
}
