/*
 * Copyright (c) 2021 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package exe.bbllw8.anemo.lock

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.hardware.biometrics.BiometricManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Optional
import java.util.function.Consumer
import kotlin.concurrent.Volatile

class LockStore private constructor(context: Context) : OnSharedPreferenceChangeListener {
    private val preferences: SharedPreferences
    private val listeners: MutableList<Consumer<Boolean>> = ArrayList()
    private val biometricManager: BiometricManager?

    private val jobScheduler: JobScheduler
    private val autoLockComponent: ComponentName

    init {
        preferences = context.getSharedPreferences(LOCK_PREFERENCES, Context.MODE_PRIVATE)
        preferences.registerOnSharedPreferenceChangeListener(this)

        jobScheduler = context.getSystemService(JobScheduler::class.java)
        biometricManager = if (Build.VERSION.SDK_INT >= 29
        ) context.getSystemService(BiometricManager::class.java)
        else null
        autoLockComponent = ComponentName(context, AutoLockJobService::class.java)
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        cancelAutoLock()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?
    ) {
        if (KEY_LOCK == key) {
            onLockChanged()
        }
    }

    @get:Synchronized
    val isLocked: Boolean
        get() = preferences.getBoolean(KEY_LOCK, DEFAULT_LOCK_VALUE)

    @Synchronized
    fun lock() {
        preferences.edit().putBoolean(KEY_LOCK, true).apply()
        cancelAutoLock()
    }

    @Synchronized
    fun unlock() {
        preferences.edit().putBoolean(KEY_LOCK, false).apply()
        if (isAutoLockEnabled) {
            scheduleAutoLock()
        }
    }

    @Synchronized
    fun setPassword(password: String): Boolean {
        return hashString(password).map { hashedPwd: String? ->
            preferences.edit().putString(KEY_PASSWORD, hashedPwd).apply()
            hashedPwd
        }.isPresent
    }

    @Synchronized
    fun passwordMatch(password: String): Boolean {
        return hashString(password)
            .map { hashedPwd: String -> hashedPwd == preferences.getString(KEY_PASSWORD, null) }
            .orElse(false)
    }

    @Synchronized
    fun hasPassword(): Boolean {
        return preferences.getString(KEY_PASSWORD, null) != null
    }

    @Synchronized
    fun removePassword() {
        preferences.edit().remove(KEY_PASSWORD).apply()
    }

    @get:Synchronized
    @set:Synchronized
    var isAutoLockEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTO_LOCK, DEFAULT_AUTO_LOCK_VALUE)
        set(enabled) {
            preferences.edit().putBoolean(KEY_AUTO_LOCK, enabled).apply()

            if (!isLocked) {
                if (enabled) {
                    // If auto-lock is enabled while the storage is unlocked, schedule the job
                    scheduleAutoLock()
                } else {
                    // If auto-lock is disabled while the storage is unlocked, cancel the job
                    cancelAutoLock()
                }
            }
        }

    fun canAuthenticateBiometric(): Boolean {
        return Build.VERSION.SDK_INT >= 29 && biometricManager != null && biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
    }

    @get:Synchronized
    @set:Synchronized
    var isBiometricUnlockEnabled: Boolean
        get() = canAuthenticateBiometric() && preferences.getBoolean(KEY_BIOMETRIC_UNLOCK, false)
        set(enabled) {
            preferences.edit().putBoolean(KEY_BIOMETRIC_UNLOCK, enabled).apply()
        }

    fun addListener(listener: Consumer<Boolean>) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: Consumer<Boolean>) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun onLockChanged() {
        val newValue = preferences.getBoolean(KEY_LOCK, DEFAULT_LOCK_VALUE)
        listeners.forEach(Consumer { listener: Consumer<Boolean> -> listener.accept(newValue) })
    }

    private fun scheduleAutoLock() {
        jobScheduler.schedule(
            JobInfo.Builder(AUTO_LOCK_JOB_ID, autoLockComponent)
                .setMinimumLatency(AUTO_LOCK_DELAY)
                .build()
        )
    }

    private fun cancelAutoLock() {
        jobScheduler.cancel(AUTO_LOCK_JOB_ID)
    }

    private fun hashString(string: String): Optional<String> {
        try {
            val digest = MessageDigest.getInstance(HASH_ALGORITHM)
            digest.update(string.toByteArray())
            return Optional.of(String(digest.digest()))
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "Couldn't get hash", e)
            return Optional.empty()
        }
    }

    companion object {
        private const val TAG = "LockStore"

        private const val LOCK_PREFERENCES = "lock_store"
        private const val KEY_LOCK = "is_locked"
        private const val KEY_PASSWORD = "password_hash"
        private const val KEY_AUTO_LOCK = "auto_lock"
        private const val KEY_BIOMETRIC_UNLOCK = "biometric_unlock"
        private const val DEFAULT_LOCK_VALUE = false
        private const val DEFAULT_AUTO_LOCK_VALUE = false

        private const val HASH_ALGORITHM = "SHA-256"

        private const val AUTO_LOCK_JOB_ID = 64

        // 15 minutes in milliseconds
        private const val AUTO_LOCK_DELAY = 1000L * 60L * 15L

        @Volatile
        private var instance: LockStore? = null

        @JvmStatic
        fun getInstance(context: Context): LockStore? {
            if (instance == null) {
                synchronized(LockStore::class.java) {
                    if (instance == null) {
                        instance = LockStore(context.applicationContext)
                    }
                }
            }
            return instance
        }
    }
}
