/*
 * Copyright (c) 2021 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package alt.nainapps.aer.lock

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import alt.nainapps.aer.R
import alt.nainapps.aer.lock.LockStore.Companion.getInstance
import java.util.function.Consumer

class LockTileService : TileService() {
    private var hasUnlockActivity = false
    private var lockStore: LockStore? = null

    override fun onBind(intent: Intent): IBinder? {
        lockStore = getInstance(this)

        val status = packageManager
            .getComponentEnabledSetting(ComponentName(this, UnlockActivity::class.java))
        hasUnlockActivity = PackageManager.COMPONENT_ENABLED_STATE_DISABLED != status

        return super.onBind(intent)
    }

    override fun onStartListening() {
        super.onStartListening()

        initializeTile()
        updateTile.accept(lockStore!!.isLocked)
        lockStore!!.addListener(updateTile)
    }

    override fun onStopListening() {
        super.onStopListening()
        lockStore!!.removeListener(updateTile)
    }

    override fun onClick() {
        super.onClick()

        if (lockStore!!.isLocked) {
            if (hasUnlockActivity) {
                val intent = Intent(this, UnlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivityAndCollapse(intent)
            } else {
                lockStore!!.unlock()
            }
        } else {
            lockStore!!.lock()
        }
    }

    private fun initializeTile() {
        val tile = qsTile
        tile.icon = Icon.createWithResource(
            this,
            R.drawable.ic_key_tile
        )
    }

    private val updateTile = Consumer { isLocked: Boolean ->
        val tile = qsTile
        if (isLocked) {
            tile.label = getString(R.string.tile_unlock)
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 30) {
                tile.stateDescription = getString(R.string.tile_status_locked)
            }
        } else {
            tile.label = getString(R.string.tile_lock)
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= 30) {
                tile.stateDescription = getString(R.string.tile_status_unlocked)
            }
        }
        tile.updateTile()
    }
}
