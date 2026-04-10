package com.example.aiasistent2

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.widget.LinearLayout
import android.view.Gravity
import android.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.example.aiasistent2.updater.AppUpdateChecker
import kotlinx.coroutines.launch

class MainActivity : androidx.appcompat.app.AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Premium Jarvis Dizayni yaratish
        val layout = android.widget.FrameLayout(this).apply {
            setBackground(android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#000000"), Color.parseColor("#001529"))
            ))
        }

        // Markaziy pulsatsiyalanuvchi doira (AI signali)
        val pulseView = android.view.View(this).apply {
            val size = 500
            layoutParams = android.widget.FrameLayout.LayoutParams(size, size, Gravity.CENTER)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setStroke(5, Color.parseColor("#00E5FF"))
                setColor(Color.parseColor("#00334E"))
                setAlpha(100)
            }
        }

        // Animatsiya qo'shish
        val animation = android.view.animation.AlphaAnimation(0.3f, 1.0f).apply {
            duration = 1500
            repeatCount = android.view.animation.Animation.INFINITE
            repeatMode = android.view.animation.Animation.REVERSE
        }
        pulseView.startAnimation(animation)

        // Matn
        val textView = TextView(this).apply {
            text = "JARVIS SYSTEM\nONLINE"
            textSize = 28f
            typeface = android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            setShadowLayer(20f, 0f, 0f, Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
        }
        
        layout.addView(pulseView)
        layout.addView(textView)
        setContentView(layout)

        // Yangilanishni tekshirish
        checkForUpdates()
    }

    private fun checkForUpdates() {
        val updater = AppUpdateChecker(this)
        lifecycleScope.launch {
            val updateInfo = updater.checkForUpdate()
            if (updateInfo != null) {
                updater.downloadAndInstall(updateInfo)
            }
        }
    }
}
