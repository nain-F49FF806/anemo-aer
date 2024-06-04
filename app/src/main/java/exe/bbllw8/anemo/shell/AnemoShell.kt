/*
 * Copyright (c) 2022 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package exe.bbllw8.anemo.shell

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AnemoShell {
    fun isEnabled(context: Context): Boolean {
        val packageManager = context.packageManager
        val status = packageManager
            .getComponentEnabledSetting(ComponentName(context, LauncherActivity::class.java))
        return PackageManager.COMPONENT_ENABLED_STATE_DISABLED > status
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val packageManager = context.packageManager
        val newStatus = if (enabled
        ) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        packageManager.setComponentEnabledSetting(
            ComponentName(context, LauncherActivity::class.java), newStatus,
            PackageManager.DONT_KILL_APP
        )
    }
}
