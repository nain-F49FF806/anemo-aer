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

class SetPasswordDialog(activity: Activity, lockStore: LockStore, onSuccess: Runnable) :
    PasswordDialog(
        activity, lockStore, onSuccess, R.string.password_set_title,
        R.layout.password_first_set
    ) {
    override fun build() {
        val passwordField = dialog.findViewById<EditText>(R.id.passwordFieldView)
        val repeatField = dialog.findViewById<EditText>(R.id.repeatFieldView)
        val positiveBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

        val validator = buildValidator(passwordField, repeatField, positiveBtn)
        passwordField.addTextChangedListener(validator)
        repeatField.addTextChangedListener(validator)

        positiveBtn.visibility = View.VISIBLE
        positiveBtn.setText(R.string.password_set_action)
        positiveBtn.isEnabled = false
        positiveBtn.setOnClickListener { v: View? ->
            val passwordValue = passwordField.text.toString()
            if (lockStore.setPassword(passwordValue)) {
                dismiss()
                lockStore.unlock()
                onSuccess.run()
            }
        }
    }

    private fun buildValidator(
        passwordField: EditText, repeatField: EditText,
        positiveBtn: Button
    ): TextListener {
        return TextListener {
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
