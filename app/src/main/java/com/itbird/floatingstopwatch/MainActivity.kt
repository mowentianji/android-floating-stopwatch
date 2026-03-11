package com.itbird.floatingstopwatch

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.itbird.floatingstopwatch.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private enum class MainPage(val title: String) {
        STOPWATCH("悬浮秒表"),
        AUTO_CLICKER("自动点击器"),
        PROFILE("我的")
    }

    private lateinit var binding: ActivityMainBinding
    private var config = OverlayConfig()
    private var currentPage = MainPage.STOPWATCH
    private val handler = Handler(Looper.getMainLooper())

    private val previewTicker = object : Runnable {
        override fun run() {
            renderPreviewTime()
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        config = OverlayConfigStore.load(this)
        setupUi()
        renderConfig()
        renderLastError()
    }

    override fun onResume() {
        super.onResume()
        config = OverlayConfigStore.load(this)
        renderConfig()
        renderLastError()
        handler.removeCallbacks(previewTicker)
        handler.post(previewTicker)
    }

    override fun onPause() {
        handler.removeCallbacks(previewTicker)
        super.onPause()
    }

    private fun setupUi() {
        binding.btnTabStopwatch.setOnClickListener { showPage(MainPage.STOPWATCH) }
        binding.btnTabAutoClicker.setOnClickListener { showPage(MainPage.AUTO_CLICKER) }
        binding.btnTabProfile.setOnClickListener { showPage(MainPage.PROFILE) }
        binding.btnSyncTime.setOnClickListener {
            config = config.copy(lastSyncLabel = "本机时间")
            saveAndRender()
            Toast.makeText(this, "已同步本机时间", Toast.LENGTH_SHORT).show()
        }
        binding.btnOffsetMinus.setOnClickListener {
            config = config.copy(timeOffsetSeconds = max(-60, config.timeOffsetSeconds - 1))
            saveAndRender()
        }
        binding.btnOffsetPlus.setOnClickListener {
            config = config.copy(timeOffsetSeconds = min(60, config.timeOffsetSeconds + 1))
            saveAndRender()
        }
        bindSwitch(binding.switchSound) { checked -> config = config.copy(soundEnabled = checked); saveAndRender() }
        bindSwitch(binding.switchCountdown) { checked -> config = config.copy(countdownEnabled = checked); saveAndRender() }
        bindSwitch(binding.switchLed) { checked -> config = config.copy(ledFontEnabled = checked); saveAndRender() }
        bindSwitch(binding.switchControls) { checked -> config = config.copy(showControlButtons = checked); saveAndRender() }
        binding.btnCountdownMinus.setOnClickListener {
            config = config.copy(countdownMinutes = max(1, config.countdownMinutes - 1))
            saveAndRender()
        }
        binding.btnCountdownPlus.setOnClickListener {
            config = config.copy(countdownMinutes = min(180, config.countdownMinutes + 1))
            saveAndRender()
        }
        binding.btnFormat.setOnClickListener {
            config = config.copy(formatIndex = (config.formatIndex + 1) % OverlayConfigStore.formatOptions.size)
            saveAndRender()
        }
        binding.btnBackgroundColor.setOnClickListener {
            config = config.copy(backgroundIndex = (config.backgroundIndex + 1) % OverlayConfigStore.backgroundOptions.size)
            saveAndRender()
        }
        binding.btnTextColor.setOnClickListener {
            config = config.copy(textColorIndex = (config.textColorIndex + 1) % OverlayConfigStore.textColorOptions.size)
            saveAndRender()
        }
        binding.btnFontMinus.setOnClickListener {
            config = config.copy(fontSizeSp = max(18, config.fontSizeSp - 2))
            saveAndRender()
        }
        binding.btnFontPlus.setOnClickListener {
            config = config.copy(fontSizeSp = min(48, config.fontSizeSp + 2))
            saveAndRender()
        }
        binding.btnStartOverlay.setOnClickListener { launchOverlay() }
        showPage(MainPage.STOPWATCH)
    }

    private fun bindSwitch(switch: CompoundButton, onChange: (Boolean) -> Unit) {
        switch.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun showPage(page: MainPage) {
        currentPage = page
        binding.pageStopwatch.visibility = if (page == MainPage.STOPWATCH) View.VISIBLE else View.GONE
        binding.pageAutoClicker.visibility = if (page == MainPage.AUTO_CLICKER) View.VISIBLE else View.GONE
        binding.pageProfile.visibility = if (page == MainPage.PROFILE) View.VISIBLE else View.GONE
        binding.tvPageTitle.text = page.title
        renderTabButton(binding.btnTabStopwatch, page == MainPage.STOPWATCH)
        renderTabButton(binding.btnTabAutoClicker, page == MainPage.AUTO_CLICKER)
        renderTabButton(binding.btnTabProfile, page == MainPage.PROFILE)
    }

    private fun renderTabButton(button: Button, active: Boolean) {
        button.alpha = if (active) 1f else 0.65f
    }

    private fun saveAndRender() {
        OverlayConfigStore.save(this, config)
        renderConfig()
    }

    private fun renderConfig() {
        binding.tvSyncStatus.text = "同步时间：${config.lastSyncLabel}"
        binding.tvOffsetValue.text = "${config.timeOffsetSeconds} 秒"
        binding.tvCountdownMinutes.text = "${config.countdownMinutes} 分钟"
        binding.tvFontSizeValue.text = config.fontSizeSp.toString()
        binding.switchSound.isChecked = config.soundEnabled
        binding.switchCountdown.isChecked = config.countdownEnabled
        binding.switchLed.isChecked = config.ledFontEnabled
        binding.switchControls.isChecked = config.showControlButtons
        binding.btnFormat.text = "时间格式：${OverlayConfigStore.formatOptions[config.formatIndex]}"
        binding.btnBackgroundColor.text = "背景色：${OverlayConfigStore.backgroundOptions[config.backgroundIndex].first}"
        binding.btnTextColor.text = "字体颜色：${OverlayConfigStore.textColorOptions[config.textColorIndex].first}"
        binding.tvMode.text = "控制按钮：${if (config.showControlButtons) "开启" else "关闭"}"
        renderPreviewStyle()
        renderPreviewTime()
    }

    private fun renderPreviewStyle() {
        val bgColor = OverlayConfigStore.backgroundOptions[config.backgroundIndex].second
        val textColor = OverlayConfigStore.textColorOptions[config.textColorIndex].second
        binding.previewPanel.setBackgroundColor(bgColor)
        binding.tvTime.setTextColor(textColor)
        binding.tvTime.typeface = if (config.ledFontEnabled) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
        binding.tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.fontSizeSp.toFloat())
    }

    private fun renderPreviewTime() {
        val now = System.currentTimeMillis() + config.timeOffsetSeconds * 1000L
        if (config.countdownEnabled) {
            val remainMs = config.countdownMinutes * 60_000L
            binding.tvTime.text = formatCountdown(remainMs, OverlayConfigStore.formatOptions[config.formatIndex])
            binding.tvCountdown.text = "倒计时模式：${config.countdownMinutes} 分钟"
        } else {
            val formatter = SimpleDateFormat(OverlayConfigStore.formatOptions[config.formatIndex], Locale.getDefault())
            binding.tvTime.text = formatter.format(Date(now))
            binding.tvCountdown.text = "当前模式：普通时间"
        }
        binding.tvSources.text = "时间偏移：${config.timeOffsetSeconds} 秒；声音提醒：${if (config.soundEnabled) "开启" else "关闭"}"
    }

    private fun launchOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许悬浮窗权限，已自动跳转", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        try {
            val intent = Intent(this, FloatingStopwatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            ErrorStore.clear(this)
            renderLastError()
            Toast.makeText(this, "已开启时间悬浮窗", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            val msg = "启动失败: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
            ErrorStore.save(this, msg)
            renderLastError()
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
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

    private fun renderLastError() {
        val last = ErrorStore.get(this)
        binding.tvLastError.text = if (last.isNullOrBlank()) "最近错误：无" else "最近错误：$last"
    }
}
