/*
 * Copyright (c) 2024 nain
 * SPDX-License-Identifier: GPL-3.0-only
 */

package alt.nainapps.aer.config.autolock

import alt.nainapps.aer.R
import alt.nainapps.aer.lock.LockStore
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

fun interface AutoLockDelayMinutesTextListener : TextWatcher {

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
    }
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
    }
    override fun afterTextChanged(s: Editable) {
        afterTextChanged(s.toString())
    }
    // this will be provided as lambda
    fun afterTextChanged(text: String)
}

fun buildValidatedAutoLockDelayListener(context: Context, lockstore: LockStore, input: EditText): AutoLockDelayMinutesTextListener {
    return AutoLockDelayMinutesTextListener { text ->
        // validate text isNumber
        try {
            text.toLong()
        } catch (error: NumberFormatException) {
            input.setError("NaN: Not a Number",
                context.getDrawable(R.drawable.ic_error))
            return@AutoLockDelayMinutesTextListener
        }
        val minutes: Long = text.toLong()
        // validate not zero
        if (minutes == 0L) {
            input.setError("Auto lock delay can't be zero",
                context.getDrawable(R.drawable.ic_error))
        }
        lockstore.autoLockDelayMinutes = minutes
    }
}