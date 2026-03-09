package com.example.floatingstopwatch

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
