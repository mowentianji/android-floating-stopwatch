package com.example.floatingstopwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
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
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.floatingstopwatch.databinding.OverlayStopwatchBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class FloatingStopwatchService : Service() {

    private enum class OverlayPage(val title: String) {
        STOPWATCH("悬浮秒表"),
        AUTO_CLICKER("自动点击器"),
        PROFILE("我的")
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var binding: OverlayStopwatchBinding? = null
    private var config = OverlayConfig()
    private var currentPage = OverlayPage.STOPWATCH
    private var isMinimized = false
    private var countdownEndAtMs: Long? = null
    private var lastCountdownAlertMinute = -1L
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
        config = OverlayConfigStore.load(this)
        if (config.countdownEnabled) {
            countdownEndAtMs = System.currentTimeMillis() + config.countdownMinutes * 60_000L
        }
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
        super.onDestroy()
    }

    private fun showOverlaySafely() {
        try {
            if (overlayView != null) return
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
            setupButtons()
            setupDrag(view, params)
            showPage(currentPage)
            applyConfigToViews()
            updateTime()
            windowManager.addView(view, params)
            overlayView = view
            handler.post(ticker)
        } catch (t: Throwable) {
            failGracefully("悬浮窗创建失败", t)
        }
    }

    private fun setupButtons() {
        binding?.btnClose?.setOnClickListener { stopSelf() }
        binding?.btnMinimize?.setOnClickListener { setMinimized(true) }
        binding?.btnRestore?.setOnClickListener { setMinimized(false) }
        binding?.btnTabStopwatch?.setOnClickListener { showPage(OverlayPage.STOPWATCH) }
        binding?.btnTabAutoClicker?.setOnClickListener { showPage(OverlayPage.AUTO_CLICKER) }
        binding?.btnTabProfile?.setOnClickListener { showPage(OverlayPage.PROFILE) }
        binding?.btnSyncTime?.setOnClickListener {
            config = config.copy(lastSyncLabel = "本机时间")
            saveConfig()
            updateTime()
            Toast.makeText(this, "已同步本机时间", Toast.LENGTH_SHORT).show()
        }
        binding?.btnOffsetMinus?.setOnClickListener {
            config = config.copy(timeOffsetSeconds = max(-60, config.timeOffsetSeconds - 1))
            saveAndRender()
        }
        binding?.btnOffsetPlus?.setOnClickListener {
            config = config.copy(timeOffsetSeconds = min(60, config.timeOffsetSeconds + 1))
            saveAndRender()
        }
        binding?.switchSound?.setOnCheckedChangeListener { _, checked ->
            config = config.copy(soundEnabled = checked)
            saveAndRender()
        }
        binding?.switchCountdown?.setOnCheckedChangeListener { _, checked ->
            config = config.copy(countdownEnabled = checked)
            countdownEndAtMs = if (checked) System.currentTimeMillis() + config.countdownMinutes * 60_000L else null
            lastCountdownAlertMinute = -1L
            saveAndRender()
        }
        binding?.btnCountdownMinus?.setOnClickListener {
            config = config.copy(countdownMinutes = max(1, config.countdownMinutes - 1))
            if (config.countdownEnabled) countdownEndAtMs = System.currentTimeMillis() + config.countdownMinutes * 60_000L
            saveAndRender()
        }
        binding?.btnCountdownPlus?.setOnClickListener {
            config = config.copy(countdownMinutes = min(180, config.countdownMinutes + 1))
            if (config.countdownEnabled) countdownEndAtMs = System.currentTimeMillis() + config.countdownMinutes * 60_000L
            saveAndRender()
        }
        binding?.switchLed?.setOnCheckedChangeListener { _, checked ->
            config = config.copy(ledFontEnabled = checked)
            saveAndRender()
        }
        binding?.switchControls?.setOnCheckedChangeListener { _, checked ->
            config = config.copy(showControlButtons = checked)
            saveAndRender()
        }
        binding?.btnFormat?.setOnClickListener {
            config = config.copy(formatIndex = (config.formatIndex + 1) % OverlayConfigStore.formatOptions.size)
            saveAndRender()
        }
        binding?.btnBackgroundColor?.setOnClickListener {
            config = config.copy(backgroundIndex = (config.backgroundIndex + 1) % OverlayConfigStore.backgroundOptions.size)
            saveAndRender()
        }
        binding?.btnTextColor?.setOnClickListener {
            config = config.copy(textColorIndex = (config.textColorIndex + 1) % OverlayConfigStore.textColorOptions.size)
            saveAndRender()
        }
        binding?.btnFontMinus?.setOnClickListener {
            config = config.copy(fontSizeSp = max(18, config.fontSizeSp - 2))
            saveAndRender()
        }
        binding?.btnFontPlus?.setOnClickListener {
            config = config.copy(fontSizeSp = min(48, config.fontSizeSp + 2))
            saveAndRender()
        }
        binding?.btnLaunchOverlay?.setOnClickListener {
            if (config.countdownEnabled) countdownEndAtMs = System.currentTimeMillis() + config.countdownMinutes * 60_000L
            saveAndRender()
            Toast.makeText(this, "悬浮秒表已按当前配置开启", Toast.LENGTH_SHORT).show()
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
                    runCatching {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showPage(page: OverlayPage) {
        currentPage = page
        binding?.pageStopwatch?.visibility = if (page == OverlayPage.STOPWATCH) View.VISIBLE else View.GONE
        binding?.pageAutoClicker?.visibility = if (page == OverlayPage.AUTO_CLICKER) View.VISIBLE else View.GONE
        binding?.pageProfile?.visibility = if (page == OverlayPage.PROFILE) View.VISIBLE else View.GONE
        binding?.tvPageTitle?.text = page.title
        renderTabButtons()
    }

    private fun renderTabButtons() {
        renderTabButton(binding?.btnTabStopwatch, currentPage == OverlayPage.STOPWATCH)
        renderTabButton(binding?.btnTabAutoClicker, currentPage == OverlayPage.AUTO_CLICKER)
        renderTabButton(binding?.btnTabProfile, currentPage == OverlayPage.PROFILE)
    }

    private fun renderTabButton(button: Button?, active: Boolean) {
        button ?: return
        button.setBackgroundResource(if (active) R.drawable.overlay_button_primary else R.drawable.overlay_button_secondary)
        button.setTextColor(if (active) 0xFFFFFFFF.toInt() else 0xFFE5EDFF.toInt())
    }

    private fun setMinimized(minimized: Boolean) {
        isMinimized = minimized
        binding?.fullContent?.visibility = if (minimized) View.GONE else View.VISIBLE
        binding?.minimizedContent?.visibility = if (minimized) View.VISIBLE else View.GONE
    }

    private fun saveAndRender() {
        saveConfig()
        applyConfigToViews()
        updateTime()
    }

    private fun saveConfig() {
        OverlayConfigStore.save(this, config)
    }

    private fun applyConfigToViews() {
        val selectedBg = OverlayConfigStore.backgroundOptions[config.backgroundIndex]
        val selectedTextColor = OverlayConfigStore.textColorOptions[config.textColorIndex]
        val typeface = if (config.ledFontEnabled) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD

        binding?.panelRoot?.setBackgroundColor(selectedBg.second)
        binding?.tvTime?.setTextColor(selectedTextColor.second)
        binding?.tvMinimizedTime?.setTextColor(selectedTextColor.second)
        binding?.tvTime?.typeface = typeface
        binding?.tvMinimizedTime?.typeface = typeface
        binding?.tvTime?.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.fontSizeSp.toFloat())
        binding?.tvMinimizedTime?.setTextSize(TypedValue.COMPLEX_UNIT_SP, max(16, config.fontSizeSp - 6).toFloat())

        binding?.tvSyncStatus?.text = "同步时间：${config.lastSyncLabel}"
        binding?.tvOffsetValue?.text = "${config.timeOffsetSeconds} 秒"
        binding?.tvCountdownMinutes?.text = "${config.countdownMinutes} 分钟"
        binding?.tvFontSizeValue?.text = config.fontSizeSp.toString()
        binding?.switchSound?.isChecked = config.soundEnabled
        binding?.switchCountdown?.isChecked = config.countdownEnabled
        binding?.switchLed?.isChecked = config.ledFontEnabled
        binding?.switchControls?.isChecked = config.showControlButtons
        binding?.btnFormat?.text = "时间格式：${OverlayConfigStore.formatOptions[config.formatIndex]}"
        binding?.btnBackgroundColor?.text = "背景色：${selectedBg.first}"
        binding?.btnTextColor?.text = "字体颜色：${selectedTextColor.first}"
        binding?.btnClose?.visibility = if (config.showControlButtons) View.VISIBLE else View.GONE
        binding?.btnMinimize?.visibility = if (config.showControlButtons) View.VISIBLE else View.GONE
        binding?.tvMode?.text = "控制按钮：${if (config.showControlButtons) "开启" else "关闭"}"
    }

    private fun updateTime() {
        val now = System.currentTimeMillis() + config.timeOffsetSeconds * 1000L
        val formatted = if (config.countdownEnabled) {
            val remain = max(0L, (countdownEndAtMs ?: now) - now)
            formatCountdown(remain, OverlayConfigStore.formatOptions[config.formatIndex])
        } else {
            SimpleDateFormat(OverlayConfigStore.formatOptions[config.formatIndex], Locale.getDefault()).format(Date(now))
        }
        binding?.tvTime?.text = formatted
        binding?.tvMinimizedTime?.text = formatted
        binding?.tvCountdown?.text = if (config.countdownEnabled) {
            val remain = max(0L, (countdownEndAtMs ?: now) - now)
            "倒计时剩余：${formatRemainLabel(remain)}"
        } else {
            "当前模式：普通时间"
        }
        binding?.tvSources?.text = "时间偏移：${config.timeOffsetSeconds} 秒；声音提醒：${if (config.soundEnabled) "开启" else "关闭"}"
        binding?.tvAllSources?.text = "LED字体：${if (config.ledFontEnabled) "开启" else "关闭"}；格式：${OverlayConfigStore.formatOptions[config.formatIndex]}"
        maybePlayReminder(now)
    }

    private fun maybePlayReminder(now: Long) {
        if (!config.soundEnabled) return
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = now }
        if (!config.countdownEnabled) {
            val minute = calendar.get(java.util.Calendar.MINUTE)
            val second = calendar.get(java.util.Calendar.SECOND)
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            if (minute == 0 && second == 0 && lastHourlyAlertHour != hour) {
                playTone()
                lastHourlyAlertHour = hour
            }
            return
        }
        val remain = max(0L, (countdownEndAtMs ?: now) - now)
        val minuteMark = remain / 60_000L
        if (remain == 0L && lastCountdownAlertMinute != 0L) {
            playTone()
            lastCountdownAlertMinute = 0L
        } else if (remain in 1..59_999L && lastCountdownAlertMinute != minuteMark) {
            playTone()
            lastCountdownAlertMinute = minuteMark
        }
    }

    private fun playTone() {
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        }
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
        private const val CHANNEL_ID = "floating_stopwatch_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
