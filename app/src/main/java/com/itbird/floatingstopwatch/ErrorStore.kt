package com.itbird.floatingstopwatch

import android.content.Context

object ErrorStore {
    private const val PREFS = "floating_stopwatch_prefs"
    private const val KEY_LAST_ERROR = "last_error"

    fun save(context: Context, message: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ERROR, message)
            .apply()
    }

    fun get(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_ERROR, null)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_ERROR)
            .apply()
    }
}
