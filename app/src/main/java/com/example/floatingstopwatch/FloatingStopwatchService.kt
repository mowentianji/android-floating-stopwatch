package com.example.floatingstopwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.floatingstopwatch.databinding.OverlayStopwatchBinding
import java.util.Locale

class FloatingStopwatchService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var binding: OverlayStopwatchBinding? = null

    private val handler = Handler(Looper.getMainLooper())
    private var baseElapsed = 0L
    private var startedAt = 0L
    private var isRunning = false

    private val ticker = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 30)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        binding = null
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        binding = OverlayStopwatchBinding.inflate(LayoutInflater.from(this))
        val view = binding!!.root

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 120
            y = 220
        }

        setupButtons()
        setupDrag(view, params)
        updateTime()

        windowManager.addView(view, params)
        overlayView = view
    }

    private fun setupButtons() {
        binding?.btnStartPause?.setOnClickListener {
            if (isRunning) pause() else startOrResume()
        }
        binding?.btnReset?.setOnClickListener {
            reset()
        }
        binding?.btnClose?.setOnClickListener {
            stopSelf()
        }
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun startOrResume() {
        if (isRunning) return
        startedAt = SystemClock.elapsedRealtime()
        isRunning = true
        binding?.btnStartPause?.text = "暂停"
        handler.post(ticker)
    }

    private fun pause() {
        if (!isRunning) return
        baseElapsed += SystemClock.elapsedRealtime() - startedAt
        isRunning = false
        binding?.btnStartPause?.text = if (baseElapsed == 0L) "开始" else "继续"
        handler.removeCallbacks(ticker)
        updateTime()
    }

    private fun reset() {
        isRunning = false
        baseElapsed = 0L
        startedAt = 0L
        binding?.btnStartPause?.text = "开始"
        handler.removeCallbacks(ticker)
        updateTime()
    }

    private fun currentElapsed(): Long {
        return if (isRunning) {
            baseElapsed + (SystemClock.elapsedRealtime() - startedAt)
        } else {
            baseElapsed
        }
    }

    private fun updateTime() {
        val elapsed = currentElapsed()
        val minutes = elapsed / 60000
        val seconds = (elapsed % 60000) / 1000
        val centiseconds = (elapsed % 1000) / 10
        binding?.tvTime?.text = String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, centiseconds)
    }

    private fun buildNotification(): Notification {
        createNotificationChannel()
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "floating_stopwatch_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
