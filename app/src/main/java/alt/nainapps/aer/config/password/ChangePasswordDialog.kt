/*
 * Copyright (c) 2021 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package alt.nainapps.aer.config.password

import alt.nainapps.aer.R
import alt.nainapps.aer.lock.LockStore
import android.app.Activity
import android.content.DialogInterface
import android.view.View
import android.widget.Button
import android.widget.EditText

class ChangePasswordDialog(activity: Activity, lockStore: LockStore, onSuccess: Runnable) :
    PasswordDialog(
        activity, lockStore, onSuccess, R.string.password_change_title,
        R.layout.password_change
    ) {
    override fun build() {
        val currentField = dialog.findViewById<EditText>(R.id.currentFieldView)
        val passwordField = dialog.findViewById<EditText>(R.id.passwordFieldView)
        val repeatField = dialog.findViewById<EditText>(R.id.repeatFieldView)
        val positiveBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val neutralBtn = dialog.getButton(DialogInterface.BUTTON_NEUTRAL)

        val validator = buildTextListener(passwordField, repeatField, positiveBtn)
        passwordField.addTextChangedListener(validator)
        repeatField.addTextChangedListener(validator)

        positiveBtn.visibility = View.VISIBLE
        positiveBtn.setText(R.string.password_change_action)
        positiveBtn.isEnabled = false
        positiveBtn.setOnClickListener { v: View? ->
            val currentPassword = currentField.text.toString()
            val newPassword = passwordField.text.toString()
            if (lockStore.passwordMatch(currentPassword)) {
                if (lockStore.setPassword(newPassword)) {
                    dismiss()
                    lockStore.unlock()
                    onSuccess.run()
                }
            } else {
                currentField.setError(res.getString(R.string.password_error_wrong), errorIcon)
            }
        }

        neutralBtn.visibility = View.VISIBLE
        neutralBtn.setText(R.string.password_change_remove)
        neutralBtn.setOnClickListener { v: View? ->
            lockStore.removePassword()
            onSuccess.run()
            dismiss()
        }
    }

    private fun buildTextListener(
        passwordField: EditText, repeatField: EditText,
        positiveBtn: Button
    ): PasswordTextListener {
        return PasswordTextListener {
            val passwordValue = passwordField.text.toString()
            val repeatValue = repeatField.text.toString()
            if (passwordValue.length < MIN_PASSWORD_LENGTH) {
                positiveBtn.isEnabled = false
                passwordField.setError(
                    res.getString(R.string.password_error_length, MIN_PASSWORD_LENGTH),
                    errorIcon
                )
                repeatField.error = null
            } else if (passwordValue != repeatValue) {
                positiveBtn.isEnabled = false
                passwordField.error = null
                repeatField.setError(
                    res.getString(R.string.password_error_mismatch),
                    errorIcon
                )
            } else {
                positiveBtn.isEnabled = true
                passwordField.error = null
                repeatField.error = null
            }
        }
    }
}
