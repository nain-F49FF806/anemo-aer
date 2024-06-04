/*
 * Copyright (c) 2022 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package alt.nainapps.aer.lock

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.RequiresApi
import alt.nainapps.aer.R
import alt.nainapps.aer.config.ConfigurationActivity
import alt.nainapps.aer.config.password.TextListener
import alt.nainapps.aer.lock.LockStore.Companion.getInstance
import alt.nainapps.aer.shell.LauncherActivity

class UnlockActivity : Activity() {
    private var lockStore: LockStore? = null
    private var onUnlocked: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lockStore = getInstance(this)
        onUnlocked = getOnUnlocked(intent)
        if (lockStore!!.hasPassword()) {
            if (lockStore!!.isBiometricUnlockEnabled) {
                unlockViaBiometricAuthentication()
            } else {
                setupUI()
            }
        } else {
            doUnlock()
        }
    }

    private fun setupUI() {
        setContentView(R.layout.password_input)
        setFinishOnTouchOutside(true)

        val passwordField = findViewById<EditText>(R.id.passwordFieldView)
        val configBtn = findViewById<ImageView>(R.id.configurationButton)
        val unlockBtn = findViewById<Button>(R.id.unlockButton)
        val cancelBtn = findViewById<Button>(R.id.cancelButton)

        passwordField.addTextChangedListener(TextListener { text: String? ->
            unlockBtn.isEnabled = passwordField.text.length >= MIN_PASSWORD_LENGTH
        })

        configBtn.setOnClickListener { v: View? ->
            startActivity(Intent(this, ConfigurationActivity::class.java))
            setResult(RESULT_CANCELED)
            finish()
        }

        unlockBtn.isEnabled = false
        unlockBtn.setOnClickListener { v: View? ->
            val value = passwordField.text.toString()
            if (lockStore!!.passwordMatch(value)) {
                doUnlock()
            } else {
                passwordField.error = getString(R.string.password_error_wrong)
            }
        }

        cancelBtn.setOnClickListener { v: View? ->
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun doUnlock() {
        lockStore!!.unlock()
        onUnlocked!!.run()
    }

    @RequiresApi(29)
    private fun unlockViaBiometricAuthentication() {
        val executor = mainExecutor
        val cancellationSignal = CancellationSignal()
        cancellationSignal.setOnCancelListener { this.finish() }

        val prompt = BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.tile_unlock))
            .setDescription(getString(R.string.password_input_biometric_message))
            .setNegativeButton(
                getString(R.string.password_input_biometric_fallback), executor
            ) { dialog: DialogInterface?, which: Int -> setupUI() }
            .build()
        prompt.authenticate(cancellationSignal, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    doUnlock()
                }

                override fun onAuthenticationFailed() {
                    setResult(RESULT_CANCELED)
                    finish()
                }
            })
    }

    private fun getOnUnlocked(intent: Intent): Runnable {
        return if (intent.getBooleanExtra(OPEN_AFTER_UNLOCK, false)) {
            Runnable {
                startActivity(
                    Intent(
                        this,
                        LauncherActivity::class.java
                    )
                )
                setResult(RESULT_OK)
                finish()
            }
        } else {
            Runnable {
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    companion object {
        const val OPEN_AFTER_UNLOCK: String = "open_after_unlock"
        private const val MIN_PASSWORD_LENGTH = 4
    }
}
