package com.ridesync.app.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Reads this device's battery level on demand. We deliberately do NOT register
 * a continuous receiver — the level is sampled only when a heartbeat is about
 * to be sent (every couple of seconds), which the spec asks for to keep the
 * radio and CPU idle.
 */
class BatteryReader(context: Context) {

    private val appContext = context.applicationContext
    private val batteryManager =
        appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    /** 0..100, or null if unavailable. */
    fun currentPercent(): Int? {
        batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.let {
            if (it in 0..100) return it
        }
        return try {
            val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100 / scale) else null
        } catch (_: Exception) {
            null
        }
    }
}
