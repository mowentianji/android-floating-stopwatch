package com.example.floatingstopwatch

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.floatingstopwatch.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        renderLastError()

        binding.btnRequestPermission.setOnClickListener {
            requestOverlayPermission()
        }

        binding.btnStartOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val msg = "请先授予悬浮窗权限"
                ErrorStore.save(this, msg)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                renderLastError()
                requestOverlayPermission()
                return@setOnClickListener
            }
            try {
                val intent = Intent(this, FloatingStopwatchService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                ErrorStore.clear(this)
                renderLastError()
            } catch (t: Throwable) {
                val msg = "启动失败: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
                ErrorStore.save(this, msg)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                renderLastError()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        renderLastError()
    }

    private fun renderLastError() {
        val last = ErrorStore.get(this)
        binding.tvLastError.text = if (last.isNullOrBlank()) {
            "最近错误：无"
        } else {
            "最近错误：$last"
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
