package com.itbird.floatingstopwatch

import android.content.Context

private const val PREFS_AUTO_POINTS = "auto_clicker_points"

data class AutoClickerPoints(
    val tapX: Int = -1,
    val tapY: Int = -1,
    val swipeStartX: Int = -1,
    val swipeStartY: Int = -1,
    val swipeEndX: Int = -1,
    val swipeEndY: Int = -1
)

object AutoClickerPointStore {
    private const val KEY_TAP_X = "tap_x"
    private const val KEY_TAP_Y = "tap_y"
    private const val KEY_SWIPE_START_X = "swipe_start_x"
    private const val KEY_SWIPE_START_Y = "swipe_start_y"
    private const val KEY_SWIPE_END_X = "swipe_end_x"
    private const val KEY_SWIPE_END_Y = "swipe_end_y"

    fun load(context: Context): AutoClickerPoints {
        val prefs = context.getSharedPreferences(PREFS_AUTO_POINTS, Context.MODE_PRIVATE)
        return AutoClickerPoints(
            tapX = prefs.getInt(KEY_TAP_X, -1),
            tapY = prefs.getInt(KEY_TAP_Y, -1),
            swipeStartX = prefs.getInt(KEY_SWIPE_START_X, -1),
            swipeStartY = prefs.getInt(KEY_SWIPE_START_Y, -1),
            swipeEndX = prefs.getInt(KEY_SWIPE_END_X, -1),
            swipeEndY = prefs.getInt(KEY_SWIPE_END_Y, -1)
        )
    }

    fun save(context: Context, points: AutoClickerPoints) {
        context.getSharedPreferences(PREFS_AUTO_POINTS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TAP_X, points.tapX)
            .putInt(KEY_TAP_Y, points.tapY)
            .putInt(KEY_SWIPE_START_X, points.swipeStartX)
            .putInt(KEY_SWIPE_START_Y, points.swipeStartY)
            .putInt(KEY_SWIPE_END_X, points.swipeEndX)
            .putInt(KEY_SWIPE_END_Y, points.swipeEndY)
            .apply()
    }
}
