package io.github.linuxkernel44.autocomp.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.linuxkernel44.autocomp.app

class ToggleTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val on = app.settings.value.enabled
        if (on) EngineService.stop(this) else EngineService.start(this)
        qsTile?.apply { state = if (on) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; updateTile() }
    }

    private fun refresh() {
        qsTile?.apply { state = if (app.settings.value.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; updateTile() }
    }
}
