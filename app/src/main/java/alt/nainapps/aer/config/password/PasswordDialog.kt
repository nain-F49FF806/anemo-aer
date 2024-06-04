/*
 * Copyright (c) 2021 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package alt.nainapps.aer.config.password

import alt.nainapps.aer.R
import alt.nainapps.aer.lock.LockStore
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes

abstract class PasswordDialog(
    activity: Activity, @JvmField protected val lockStore: LockStore, @JvmField protected val onSuccess: Runnable,
    @StringRes title: Int, @LayoutRes layout: Int
) {
    @JvmField
    protected val res: Resources = activity.resources
    @JvmField
    protected val dialog: AlertDialog
    @JvmField
    protected val MIN_PASSWORD_LENGTH: Int = 4

    init {
        this.dialog = AlertDialog.Builder(activity, R.style.DialogTheme).setTitle(title)
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { d: DialogInterface?, which: Int -> dismiss() }
            .create()
    }

    fun dismiss() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }

    fun show() {
        dialog.show()
        build()
    }

    protected val errorIcon: Drawable
        get() {
            val drawable = dialog.context.getDrawable(R.drawable.ic_error)
            drawable!!.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            return drawable
        }

    protected abstract fun build()
}
