/*
 * Copyright (c) 2021 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package alt.nainapps.aer.lock

import android.app.job.JobParameters
import android.app.job.JobService
import android.widget.Toast
import alt.nainapps.aer.R

class AutoLockJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val lockStore = LockStore.getInstance(applicationContext)
        if (lockStore != null) {
            if (!lockStore.isLocked) {
                lockStore.lock()
                Toast.makeText(this, getString(R.string.tile_auto_lock), Toast.LENGTH_LONG).show()
            }
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }
}
