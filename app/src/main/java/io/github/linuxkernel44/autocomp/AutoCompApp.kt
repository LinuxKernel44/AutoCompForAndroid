package io.github.linuxkernel44.autocomp

import android.app.Application
import io.github.linuxkernel44.autocomp.core.Engine
import io.github.linuxkernel44.autocomp.core.SettingsStore
import io.github.linuxkernel44.autocomp.shizuku.ShizukuConnector

class AutoCompApp : Application() {
    lateinit var settings: SettingsStore; private set
    lateinit var shizuku: ShizukuConnector; private set
    lateinit var engine: Engine; private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        shizuku = ShizukuConnector(this).also { it.init() }
        engine = Engine(this, settings, shizuku)
    }
}

val android.content.Context.app get() = applicationContext as AutoCompApp
