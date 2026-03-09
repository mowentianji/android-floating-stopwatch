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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.floatingstopwatch.databinding.OverlayStopwatchBinding
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs

class FloatingStopwatchService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var binding: OverlayStopwatchBinding? = null

    private val handler = Handler(Looper.getMainLooper())
    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private var networkOffsetMs = 0L
    private var lastSyncLabel = "未同步"
    private var sourceSummary = "未同步"
    private var allSourceSummary = "未同步"
    private var autoSyncEnabled = false
    private val autoSyncIntervalMs = 5000L
    private var syncInFlight = false

    private val ticker = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 16)
        }
    }

    private val autoSyncRunnable = object : Runnable {
        override fun run() {
            if (autoSyncEnabled) {
                syncNetworkTime(autoTriggered = true)
                handler.postDelayed(this, autoSyncIntervalMs)
            }
        }
    }

    data class TimeProbeResult(
        val name: String,
        val url: String,
        val offsetMs: Long,
        val roundTripMs: Long
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
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
        runCatching {
            overlayView?.let { windowManager.removeView(it) }
        }
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
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
            handler.post(ticker)
        } catch (t: Throwable) {
            failGracefully("悬浮窗创建失败", t)
        }
    }

    private fun setupButtons() {
        binding?.btnStartPause?.setOnClickListener {
            syncNetworkTime(autoTriggered = false)
        }
        binding?.btnReset?.setOnClickListener {
            networkOffsetMs = 0L
            lastSyncLabel = "未同步"
            sourceSummary = "未同步"
            allSourceSummary = "未同步"
            updateTime()
            Toast.makeText(this, "已清零网络偏移", Toast.LENGTH_SHORT).show()
        }
        binding?.btnAutoSync?.setOnClickListener {
            toggleAutoSync()
        }
        binding?.btnClose?.setOnClickListener {
            stopSelf()
        }
        renderAutoSyncButton()
    }

    private fun toggleAutoSync() {
        autoSyncEnabled = !autoSyncEnabled
        handler.removeCallbacks(autoSyncRunnable)
        if (autoSyncEnabled) {
            handler.post(autoSyncRunnable)
            Toast.makeText(this, "已开启自动校时", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "已关闭自动校时", Toast.LENGTH_SHORT).show()
        }
        renderAutoSyncButton()
    }

    private fun renderAutoSyncButton() {
        binding?.btnAutoSync?.text = if (autoSyncEnabled) {
            "关闭自动校时(5秒)"
        } else {
            "开启自动校时(5秒)"
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

    private fun syncNetworkTime(autoTriggered: Boolean) {
        if (syncInFlight) return
        syncInFlight = true
        binding?.btnStartPause?.isEnabled = false
        binding?.tvSyncStatus?.text = if (autoTriggered) "网络校时：自动同步中..." else "网络校时：同步中..."
        binding?.tvSources?.text = "参考源：同步中..."
        binding?.tvAllSources?.text = "全部源：同步中..."

        thread(name = "network-time-sync") {
            val result = runCatching { fetchBestNetworkOffset() }
            handler.post {
                syncInFlight = false
                binding?.btnStartPause?.isEnabled = true
                result.onSuccess { allResults ->
                    val best = allResults.minByOrNull { it.roundTripMs }!!
                    networkOffsetMs = best.offsetMs
                    lastSyncLabel = if (best.offsetMs >= 0) "+${best.offsetMs}ms" else "${best.offsetMs}ms"
                    sourceSummary = buildSourceSummary(best)
                    allSourceSummary = buildAllSourcesSummary(allResults)
                    updateTime()
                    if (!autoTriggered) {
                        Toast.makeText(this, "校时完成：${best.name} ${lastSyncLabel}", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure {
                    val msg = "校时失败: ${it.javaClass.simpleName}"
                    lastSyncLabel = msg
                    sourceSummary = msg
                    allSourceSummary = msg
                    updateTime()
                    if (!autoTriggered) {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun fetchBestNetworkOffset(): List<TimeProbeResult> {
        val probes = listOf(
            "京东" to "https://www.jd.com",
            "淘宝" to "https://www.taobao.com",
            "天猫" to "https://www.tmall.com",
            "百度" to "https://www.baidu.com",
            "QQ" to "https://www.qq.com",
            "Cloudflare" to "https://www.cloudflare.com"
        )

        val success = mutableListOf<TimeProbeResult>()
        val errors = mutableListOf<String>()

        for ((name, url) in probes) {
            try {
                success += probeTime(name, url)
            } catch (t: Throwable) {
                errors += "$name:${t.javaClass.simpleName}"
            }
        }

        if (success.isEmpty()) {
            throw IllegalStateException(errors.joinToString(" | "))
        }

        return success.sortedBy { it.roundTripMs }
    }

    private fun probeTime(name: String, url: String): TimeProbeResult {
        val start = System.currentTimeMillis()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 5000
            readTimeout = 5000
            instanceFollowRedirects = true
            connect()
        }
        val serverTime = conn.date
        val end = System.currentTimeMillis()
        conn.disconnect()
        if (serverTime <= 0L) error("no-date")
        val midpoint = (start + end) / 2
        return TimeProbeResult(
            name = name,
            url = url,
            offsetMs = serverTime - midpoint,
            roundTripMs = end - start
        )
    }

    private fun buildSourceSummary(best: TimeProbeResult): String {
        val offsetText = if (best.offsetMs >= 0) "+${best.offsetMs}ms" else "${best.offsetMs}ms"
        val netText = formatRtt(best.roundTripMs)
        return "${best.name} ${offsetText} / RTT ${netText}"
    }

    private fun buildAllSourcesSummary(results: List<TimeProbeResult>): String {
        return results.joinToString(" | ") {
            val offsetText = if (it.offsetMs >= 0) "+${it.offsetMs}ms" else "${it.offsetMs}ms"
            "${it.name} ${offsetText} ${formatRtt(it.roundTripMs)}"
        }
    }

    private fun formatRtt(rtt: Long): String {
        return if (abs(rtt) >= 1000) {
            String.format(Locale.getDefault(), "%.2fs", rtt / 1000f)
        } else {
            "${rtt}ms"
        }
    }

    private fun updateTime() {
        val adjusted = System.currentTimeMillis() + networkOffsetMs
        binding?.tvTime?.text = timeFormatter.format(Date(adjusted))
        binding?.tvSyncStatus?.text = "网络校时：$lastSyncLabel"
        binding?.tvSources?.text = "参考源：$sourceSummary"
        binding?.tvAllSources?.text = "全部源：$allSourceSummary"
        renderAutoSyncButton()
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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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
