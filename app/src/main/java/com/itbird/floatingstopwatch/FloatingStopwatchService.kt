package com.itbird.floatingstopwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.itbird.floatingstopwatch.databinding.OverlayStopwatchBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class FloatingStopwatchService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var binding: OverlayStopwatchBinding? = null
    private var config = OverlayConfig()
    private var countdownEndAtMs: Long? = null
    private var lastCountdownAlert = false
    private var lastCountdownMinutes: Int = -1
    private var lastHourlyAlertHour = -1

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val ticker = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 50)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        config = OverlayConfigStore.load(this)
        if (config.countdownEnabled) countdownEndAtMs = System.currentTimeMillis() + config.countdownMinutes * 60_000L
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            showOverlaySafely()
            ErrorStore.clear(this)
        } catch (t: Throwable) {
            failGracefully("服务启动失败", t)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { overlayView?.let { windowManager.removeView(it) } }
        overlayView = null
        binding = null
        isRunning = false
        sendBroadcast(Intent(ACTION_OVERLAY_CLOSED))
        super.onDestroy()
    }

    private fun showOverlaySafely() {
        if (overlayView != null) return
        config = OverlayConfigStore.load(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        binding = OverlayStopwatchBinding.inflate(LayoutInflater.from(this))
        val view = binding!!.root
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 180
        }
        binding?.btnClose?.setOnClickListener { stopSelf() }
        binding?.btnMinimize?.setOnClickListener { setMinimized(true) }
        binding?.btnRestore?.setOnClickListener { setMinimized(false) }
        setupDrag(view, params)
        applyConfigToViews()
        updateTime()
        windowManager.addView(view, params)
        overlayView = view
        handler.post(ticker)
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

    private fun setMinimized(minimized: Boolean) {
        binding?.fullContent?.visibility = if (minimized) View.GONE else View.VISIBLE
        binding?.minimizedContent?.visibility = if (minimized) View.VISIBLE else View.GONE
    }

    private fun applyConfigToViews() {
        config = OverlayConfigStore.load(this)
        val bgColor = OverlayConfigStore.backgroundOptions[config.backgroundIndex].second
        val textColor = OverlayConfigStore.textColorOptions[config.textColorIndex].second
        val typeface = if (config.ledFontEnabled) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
        binding?.panelRoot?.setBackgroundColor(bgColor)
        binding?.tvTime?.setTextColor(textColor)
        binding?.tvMinimizedTime?.setTextColor(textColor)
        binding?.tvTime?.typeface = typeface
        binding?.tvMinimizedTime?.typeface = typeface
        binding?.tvTime?.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.fontSizeSp.toFloat())
        binding?.tvMinimizedTime?.setTextSize(TypedValue.COMPLEX_UNIT_SP, max(16, config.fontSizeSp - 6).toFloat())
        applyFixedWidth()
        val visible = if (config.showControlButtons) View.VISIBLE else View.GONE
        binding?.btnClose?.visibility = visible
        binding?.btnMinimize?.visibility = visible
    }

    private fun applyFixedWidth() {
        val pattern = OverlayConfigStore.formatOptions[config.formatIndex]
        val sample = when (pattern) {
            "HH:mm:ss.SSS" -> "88:88:88.888"
            "mm:ss.SS" -> "88:88.88"
            else -> "88:88:88.8"
        }
        val dm = resources.displayMetrics
        val maxWidth = (dm.widthPixels * 0.9f).toInt()
        binding?.tvTime?.let { tv ->
            val width = tv.paint.measureText(sample).toInt() + tv.paddingLeft + tv.paddingRight
            tv.minWidth = width.coerceAtMost(maxWidth)
            tv.maxWidth = width.coerceAtMost(maxWidth)
            tv.includeFontPadding = false
            tv.setLineSpacing(0f, 1f)
        }
        binding?.tvMinimizedTime?.let { tv ->
            val width = tv.paint.measureText(sample).toInt() + tv.paddingLeft + tv.paddingRight
            tv.minWidth = width.coerceAtMost(maxWidth)
            tv.maxWidth = width.coerceAtMost(maxWidth)
            tv.includeFontPadding = false
            tv.setLineSpacing(0f, 1f)
        }
        binding?.tvCountdown?.let { tv ->
            val sampleLabel = "倒计时中"
            val width = tv.paint.measureText(sampleLabel).toInt() + tv.paddingLeft + tv.paddingRight
            tv.minWidth = width.coerceAtMost(maxWidth)
            tv.maxWidth = width.coerceAtMost(maxWidth)
            tv.setLineSpacing(0f, 1f)
            tv.includeFontPadding = false
        }
    }

    private fun updateTime() {
        applyConfigToViews()
        val now = System.currentTimeMillis() + config.timeOffsetSeconds * 1000L
        if (config.countdownEnabled) {
            if (countdownEndAtMs == null || lastCountdownMinutes != config.countdownMinutes) {
                countdownEndAtMs = now + config.countdownMinutes * 60_000L
                lastCountdownMinutes = config.countdownMinutes
                lastCountdownAlert = false
            }
        } else {
            countdownEndAtMs = null
            lastCountdownMinutes = -1
            lastCountdownAlert = false
        }
        val text = if (config.countdownEnabled) {
            val remain = max(0L, (countdownEndAtMs ?: now) - now)
            formatCountdown(remain, OverlayConfigStore.formatOptions[config.formatIndex])
        } else {
            SimpleDateFormat(OverlayConfigStore.formatOptions[config.formatIndex], Locale.getDefault()).format(Date(now))
        }
        binding?.tvTime?.text = text
        binding?.tvMinimizedTime?.text = text
        binding?.tvCountdown?.apply {
            setText(if (config.countdownEnabled) {
                "倒计时中"
            } else {
                ""
            })
            visibility = if (config.countdownEnabled) View.VISIBLE else View.GONE
        }
        applyFixedWidth()
        maybePlayReminder(now)
    }

    private fun maybePlayReminder(now: Long) {
        if (!config.soundEnabled) return
        if (config.countdownEnabled) {
            val remain = max(0L, (countdownEndAtMs ?: now) - now)
            if (remain == 0L && !lastCountdownAlert) {
                playTone()
                lastCountdownAlert = true
            }
            return
        }
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        if (calendar.get(Calendar.MINUTE) == 0 && calendar.get(Calendar.SECOND) == 0 && lastHourlyAlertHour != hour) {
            playTone()
            lastHourlyAlertHour = hour
        }
    }

    private fun playTone() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringer = audioManager.ringerMode
        if (ringer == AudioManager.RINGER_MODE_SILENT) {
            Toast.makeText(this, "当前静音模式，无法播放提示音", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).startTone(ToneGenerator.TONE_PROP_BEEP, 180)
            return
        }
        runCatching {
            ToneGenerator(AudioManager.STREAM_ALARM, 90).startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            return
        }
        runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, 90).startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            return
        }
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone?.play()
            return
        }
        Toast.makeText(this, "声音提醒失败", Toast.LENGTH_SHORT).show()
    }

    private fun formatCountdown(remainMs: Long, pattern: String): String {
        val totalSeconds = remainMs / 1000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val tenth = (remainMs % 1000L) / 100L
        val millis = remainMs % 1000L
        return when (pattern) {
            "HH:mm:ss.SSS" -> String.format(Locale.getDefault(), "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
            "mm:ss.SS" -> String.format(Locale.getDefault(), "%02d:%02d.%02d", hours * 60 + minutes, seconds, millis / 10)
            else -> String.format(Locale.getDefault(), "%02d:%02d:%02d.%01d", hours, minutes, seconds, tenth)
        }
    }

    private fun formatRemainLabel(remainMs: Long): String {
        val totalSeconds = remainMs / 1000L
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun buildNotification(): Notification {
        createNotificationChannel()
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun failGracefully(prefix: String, t: Throwable) {
        val msg = "$prefix: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
        ErrorStore.save(this, msg)
        CrashLogger.recordNow(this, Thread.currentThread(), t)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    companion object {
        const val ACTION_OVERLAY_CLOSED = "com.itbird.floatingstopwatch.ACTION_OVERLAY_CLOSED"
        @Volatile
        var isRunning: Boolean = false
        private const val CHANNEL_ID = "floating_stopwatch_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
