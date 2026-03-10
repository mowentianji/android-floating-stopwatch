package com.example.floatingstopwatch

import android.content.Context
import android.graphics.Color

private const val PREFS_NAME = "overlay_config"

data class OverlayConfig(
    val timeOffsetSeconds: Int = 0,
    val soundEnabled: Boolean = false,
    val countdownEnabled: Boolean = false,
    val countdownMinutes: Int = 1,
    val ledFontEnabled: Boolean = true,
    val showControlButtons: Boolean = true,
    val formatIndex: Int = 0,
    val backgroundIndex: Int = 0,
    val textColorIndex: Int = 0,
    val fontSizeSp: Int = 26,
    val lastSyncLabel: String = "本机时间"
)

object OverlayConfigStore {
    private const val KEY_OFFSET_SECONDS = "offset_seconds"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_COUNTDOWN_ENABLED = "countdown_enabled"
    private const val KEY_COUNTDOWN_MINUTES = "countdown_minutes"
    private const val KEY_LED_FONT_ENABLED = "led_font_enabled"
    private const val KEY_SHOW_CONTROL_BUTTONS = "show_control_buttons"
    private const val KEY_FORMAT_INDEX = "format_index"
    private const val KEY_BACKGROUND_INDEX = "background_index"
    private const val KEY_TEXT_COLOR_INDEX = "text_color_index"
    private const val KEY_FONT_SIZE_SP = "font_size_sp"
    private const val KEY_LAST_SYNC_LABEL = "last_sync_label"

    val formatOptions = listOf("HH:mm:ss.S", "HH:mm:ss.SSS", "mm:ss.SS")
    val backgroundOptions = listOf(
        "深空蓝" to Color.parseColor("#D910172A"),
        "石墨灰" to Color.parseColor("#D91F2937"),
        "墨绿" to Color.parseColor("#D9102A22"),
        "紫夜" to Color.parseColor("#D9261B3D")
    )
    val textColorOptions = listOf(
        "冰白" to Color.parseColor("#F9FAFB"),
        "天蓝" to Color.parseColor("#7DD3FC"),
        "薄荷" to Color.parseColor("#86EFAC"),
        "暖黄" to Color.parseColor("#FDE68A")
    )

    fun load(context: Context): OverlayConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return OverlayConfig(
            timeOffsetSeconds = prefs.getInt(KEY_OFFSET_SECONDS, 0),
            soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, false),
            countdownEnabled = prefs.getBoolean(KEY_COUNTDOWN_ENABLED, false),
            countdownMinutes = prefs.getInt(KEY_COUNTDOWN_MINUTES, 1),
            ledFontEnabled = prefs.getBoolean(KEY_LED_FONT_ENABLED, true),
            showControlButtons = prefs.getBoolean(KEY_SHOW_CONTROL_BUTTONS, true),
            formatIndex = prefs.getInt(KEY_FORMAT_INDEX, 0),
            backgroundIndex = prefs.getInt(KEY_BACKGROUND_INDEX, 0),
            textColorIndex = prefs.getInt(KEY_TEXT_COLOR_INDEX, 0),
            fontSizeSp = prefs.getInt(KEY_FONT_SIZE_SP, 26),
            lastSyncLabel = prefs.getString(KEY_LAST_SYNC_LABEL, "本机时间") ?: "本机时间"
        )
    }

    fun save(context: Context, config: OverlayConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_OFFSET_SECONDS, config.timeOffsetSeconds)
            .putBoolean(KEY_SOUND_ENABLED, config.soundEnabled)
            .putBoolean(KEY_COUNTDOWN_ENABLED, config.countdownEnabled)
            .putInt(KEY_COUNTDOWN_MINUTES, config.countdownMinutes)
            .putBoolean(KEY_LED_FONT_ENABLED, config.ledFontEnabled)
            .putBoolean(KEY_SHOW_CONTROL_BUTTONS, config.showControlButtons)
            .putInt(KEY_FORMAT_INDEX, config.formatIndex)
            .putInt(KEY_BACKGROUND_INDEX, config.backgroundIndex)
            .putInt(KEY_TEXT_COLOR_INDEX, config.textColorIndex)
            .putInt(KEY_FONT_SIZE_SP, config.fontSizeSp)
            .putString(KEY_LAST_SYNC_LABEL, config.lastSyncLabel)
            .apply()
    }
}
