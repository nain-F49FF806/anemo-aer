/*
 * Copyright (c) 2022 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package alt.nainapps.aer.config

import alt.nainapps.aer.R
import alt.nainapps.aer.config.password.ChangePasswordDialog
import alt.nainapps.aer.config.password.SetPasswordDialog
import alt.nainapps.aer.lock.LockStore
import alt.nainapps.aer.lock.UnlockActivity
import alt.nainapps.aer.shell.AnemoShell
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import java.util.function.Consumer

class ConfigurationActivity : Activity() {
    private var passwordSetView: TextView? = null
    private var changeLockView: TextView? = null
    private var biometricSwitch: Switch? = null

    private lateinit var lockStore: LockStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lockStore = LockStore.getInstance(applicationContext)!!
        lockStore.addListener(onLockChanged)

        setContentView(R.layout.configuration)

        passwordSetView = findViewById(R.id.configuration_password_set)

        val shortcutSwitch = findViewById<Switch>(R.id.configuration_show_shortcut)
        shortcutSwitch.isChecked = AnemoShell.isEnabled(application)
        shortcutSwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            AnemoShell.setEnabled(
                application,
                isChecked,
            )
        }

        changeLockView = findViewById(R.id.configuration_lock)
        changeLockView!!.setText(
            if (lockStore.isLocked
            ) {
                R.string.configuration_storage_unlock
            } else {
                R.string.configuration_storage_lock
            },
        )
        changeLockView!!.setOnClickListener {
            if (lockStore.isLocked) {
                startActivity(Intent(this, UnlockActivity::class.java))
            } else {
                lockStore.lock()
            }
        }

        val autoLockSwitch = findViewById<Switch>(R.id.configuration_auto_lock)
        autoLockSwitch.isChecked = lockStore.isAutoLockEnabled
        autoLockSwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            lockStore.isAutoLockEnabled = isChecked
        }

        biometricSwitch = findViewById(R.id.configuration_biometric_unlock)
        biometricSwitch!!.visibility = if (lockStore.canAuthenticateBiometric()) View.VISIBLE else View.GONE
        biometricSwitch!!.isChecked = lockStore.isBiometricUnlockEnabled
        biometricSwitch!!.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            lockStore.isBiometricUnlockEnabled = isChecked
        }

        setupPasswordViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        lockStore.removeListener(onLockChanged)
    }

    private fun setupPasswordViews() {
        if (lockStore.hasPassword()) {
            passwordSetView!!.setText(R.string.configuration_password_change)
            passwordSetView!!.setOnClickListener {
                ChangePasswordDialog(
                    this,
                    lockStore,
                ) { this.setupPasswordViews() }
                    .show()
            }
        } else {
            passwordSetView!!.setText(R.string.configuration_password_set)
            passwordSetView!!.setOnClickListener {
                SetPasswordDialog(
                    this,
                    lockStore,
                ) { this.setupPasswordViews() }.show()
            }
        }
        val enableViews = !lockStore.isLocked
        passwordSetView!!.isEnabled = enableViews
        biometricSwitch!!.isEnabled = enableViews
    }

    private val onLockChanged =
        Consumer { isLocked: Boolean ->
            passwordSetView!!.isEnabled = !isLocked
            biometricSwitch!!.isEnabled = !isLocked
            changeLockView!!.setText(
                if (isLocked
                ) {
                    R.string.configuration_storage_unlock
                } else {
                    R.string.configuration_storage_lock
                },
            )
        }
}
