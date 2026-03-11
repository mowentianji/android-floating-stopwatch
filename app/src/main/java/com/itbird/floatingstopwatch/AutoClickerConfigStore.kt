package com.itbird.floatingstopwatch

import android.content.Context

private const val PREFS_NAME_AUTO = "auto_clicker_config"

data class AutoClickerConfig(
    val intervalMs: Int = 50,
    val holdMs: Int = 50,
    val swipeMs: Int = 300,
    val pinchMs: Int = 3000,
    val repeatCount: Int = 1,
    val delayStartSec: Int = 0,
    val loopIntervalMs: Int = 0
)

object AutoClickerConfigStore {
    private const val KEY_INTERVAL = "interval_ms"
    private const val KEY_HOLD = "hold_ms"
    private const val KEY_SWIPE = "swipe_ms"
    private const val KEY_PINCH = "pinch_ms"
    private const val KEY_REPEAT = "repeat_count"
    private const val KEY_DELAY = "delay_start_sec"
    private const val KEY_LOOP_INTERVAL = "loop_interval_ms"

    fun load(context: Context): AutoClickerConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME_AUTO, Context.MODE_PRIVATE)
        return AutoClickerConfig(
            intervalMs = prefs.getInt(KEY_INTERVAL, 50),
            holdMs = prefs.getInt(KEY_HOLD, 50),
            swipeMs = prefs.getInt(KEY_SWIPE, 300),
            pinchMs = prefs.getInt(KEY_PINCH, 3000),
            repeatCount = prefs.getInt(KEY_REPEAT, 1),
            delayStartSec = prefs.getInt(KEY_DELAY, 0),
            loopIntervalMs = prefs.getInt(KEY_LOOP_INTERVAL, 0)
        )
    }

    fun save(context: Context, config: AutoClickerConfig) {
        context.getSharedPreferences(PREFS_NAME_AUTO, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INTERVAL, config.intervalMs)
            .putInt(KEY_HOLD, config.holdMs)
            .putInt(KEY_SWIPE, config.swipeMs)
            .putInt(KEY_PINCH, config.pinchMs)
            .putInt(KEY_REPEAT, config.repeatCount)
            .putInt(KEY_DELAY, config.delayStartSec)
            .putInt(KEY_LOOP_INTERVAL, config.loopIntervalMs)
            .apply()
    }

    fun reset(): AutoClickerConfig = AutoClickerConfig()
}
