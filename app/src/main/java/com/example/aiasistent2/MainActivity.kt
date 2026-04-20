package com.example.aiasistent2

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.aiasistent2.updater.AppUpdateChecker
import kotlinx.coroutines.launch
import android.net.http.SslError
import android.webkit.SslErrorHandler

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var rootLayout: FrameLayout
    private var webView: WebView? = null

    companion object {
        private const val PREF_NAME = "jarvis_prefs"
        private const val KEY_SERVER_IP = "server_ip"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        setContentView(rootLayout)

        // Saqlangan IP bormi tekshirish
        val savedIp = prefs.getString(KEY_SERVER_IP, null)
        if (!savedIp.isNullOrBlank()) {
            showWebView(savedIp)
        } else {
            showIpInputScreen()
        }

        // Yangilanishni tekshirish
        checkForUpdates()
    }

    // =============================================
    // IP KIRITISH EKRANI — Premium Jarvis dizayni
    // =============================================
    @SuppressLint("SetTextI18n")
    private fun showIpInputScreen() {
        rootLayout.removeAllViews()

        // Orqa fon gradient
        val bgGradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#000000"), Color.parseColor("#001529"), Color.parseColor("#000a14"))
        )
        rootLayout.background = bgGradient

        // Asosiy konteyner
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(80, 100, 80, 100)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }

        // ---- Pulsatsiyalanuvchi doira (AI signali) ----
        val pulseContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 60
            }
        }

        // Tashqi halqa
        val outerRing = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(200, 200, Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(3, Color.parseColor("#00E5FF"))
                setColor(Color.TRANSPARENT)
            }
        }
        // Animatsiya - scale
        val scaleAnim = ScaleAnimation(
            0.8f, 1.2f, 0.8f, 1.2f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 2000
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        outerRing.startAnimation(scaleAnim)

        // Ichki doira
        val innerCircle = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(120, 120, Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00334E"))
                setStroke(2, Color.parseColor("#00E5FF"))
            }
        }
        val alphaAnim = AlphaAnimation(0.4f, 1.0f).apply {
            duration = 1500
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        innerCircle.startAnimation(alphaAnim)

        pulseContainer.addView(outerRing)
        pulseContainer.addView(innerCircle)
        container.addView(pulseContainer)

        // ---- JARVIS matn ----
        val titleText = TextView(this).apply {
            text = "JARVIS"
            textSize = 36f
            typeface = Typeface.create("sans-serif-thin", Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            setShadowLayer(30f, 0f, 0f, Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10
            }
        }
        container.addView(titleText)

        // Subtitle
        val subtitleText = TextView(this).apply {
            text = "AI SYSTEM"
            textSize = 14f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Color.parseColor("#4DD0E1"))
            letterSpacing = 0.5f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 80
            }
        }
        container.addView(subtitleText)

        // ---- "Server IP kiriting" label ----
        val ipLabel = TextView(this).apply {
            text = "🌐  Server IP manzilini kiriting"
            textSize = 16f
            setTextColor(Color.parseColor("#80DEEA"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 30
            }
        }
        container.addView(ipLabel)

        // ---- IP KIRITISH MAYDONI ----
        val inputBg = GradientDrawable().apply {
            cornerRadius = 30f
            setStroke(2, Color.parseColor("#00838F"))
            setColor(Color.parseColor("#0D1B2A"))
        }

        val ipInput = EditText(this).apply {
            hint = "Masalan: https://xxxx-xx-xxx.ngrok-free.app"
            setHintTextColor(Color.parseColor("#3E6B7A"))
            setTextColor(Color.parseColor("#E0F7FA"))
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            background = inputBg
            setPadding(50, 40, 50, 40)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
        }
        container.addView(ipInput)

        // ---- Info matn ----
        val infoText = TextView(this).apply {
            text = "Google Colab yoki Ngrok orqali olingan\nserver manzilini kiriting"
            textSize = 12f
            setTextColor(Color.parseColor("#546E7A"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 50
            }
        }
        container.addView(infoText)

        // ---- Status matn (xatolik uchun) ----
        val statusText = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.parseColor("#FF5252"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 30
            }
        }
        container.addView(statusText)

        // ---- ULASH TUGMASI ----
        val connectBtnBg = GradientDrawable().apply {
            cornerRadius = 30f
            setColor(Color.parseColor("#00838F"))
        }
        val connectBtnBgPressed = GradientDrawable().apply {
            cornerRadius = 30f
            setColor(Color.parseColor("#00ACC1"))
        }

        val connectBtn = TextView(this).apply {
            text = "⚡  ULASH"
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = connectBtnBg
            setPadding(0, 40, 0, 40)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 30
            }
            isClickable = true
            isFocusable = true
        }

        connectBtn.setOnClickListener {
            var ip = ipInput.text.toString().trim()
            if (ip.isEmpty()) {
                statusText.text = "⚠️ Iltimos, IP manzilini kiriting!"
                statusText.setTextColor(Color.parseColor("#FF5252"))
                return@setOnClickListener
            }

            // Avtomatik http:// qo'shish
            if (!ip.startsWith("http://") && !ip.startsWith("https://")) {
                ip = "http://$ip"
            }

            // IP saqlash
            prefs.edit().putString(KEY_SERVER_IP, ip).apply()

            statusText.text = "🔗 Serverga ulanilmoqda..."
            statusText.setTextColor(Color.parseColor("#00E5FF"))

            // WebView ga o'tish
            showWebView(ip)
        }
        container.addView(connectBtn)

        // ---- Pastki dekor chizig'i ----
        val bottomLine = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(300, 2).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 40
            }
            setBackgroundColor(Color.parseColor("#1A3A4A"))
        }
        container.addView(bottomLine)

        val bottomText = TextView(this).apply {
            text = "JARVIS v1.0  •  Antigravity AI"
            textSize = 11f
            setTextColor(Color.parseColor("#2A4A5A"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 15
            }
        }
        container.addView(bottomText)

        rootLayout.addView(container)
    }

    // =============================================
    // WEBVIEW — Serverga ulangandan keyin
    // =============================================
    @SuppressLint("SetJavaScriptEnabled")
    private fun showWebView(serverUrl: String) {
        rootLayout.removeAllViews()

        // Orqa fon
        rootLayout.setBackgroundColor(Color.BLACK)

        // Asosiy vertikal layout
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // ---- Yuqori panel (status bar) ----
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(30, 20, 30, 20)
            setBackgroundColor(Color.parseColor("#001529"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Status indikator (yashil doira)
        val statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(20, 20).apply {
                rightMargin = 15
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E676"))
            }
        }
        // Pulsatsiya animatsiya
        val dotAnim = AlphaAnimation(0.3f, 1.0f).apply {
            duration = 1000
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        statusDot.startAnimation(dotAnim)
        topBar.addView(statusDot)

        // Status matn
        val statusLabel = TextView(this).apply {
            text = "JARVIS — Ulangan"
            textSize = 14f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(statusLabel)

        // IP o'zgartirish tugmasi
        val changeIpBtn = TextView(this).apply {
            text = "⚙️ IP"
            textSize = 13f
            setTextColor(Color.parseColor("#4DD0E1"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(20, 10, 20, 10)
            background = GradientDrawable().apply {
                cornerRadius = 15f
                setStroke(1, Color.parseColor("#00838F"))
                setColor(Color.TRANSPARENT)
            }
            isClickable = true
            isFocusable = true
        }
        changeIpBtn.setOnClickListener {
            // WebView ni to'xtatish
            webView?.destroy()
            webView = null
            // IP kiritish ekraniga qaytish
            showIpInputScreen()
        }
        topBar.addView(changeIpBtn)

        // Qayta yuklash tugmasi
        val reloadBtn = TextView(this).apply {
            text = "🔄"
            textSize = 18f
            setPadding(20, 10, 20, 10)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 10
            }
        }
        reloadBtn.setOnClickListener {
            webView?.reload()
        }
        topBar.addView(reloadBtn)

        mainLayout.addView(topBar)

        // ---- Progress bar ----
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                8
            )
            isIndeterminate = false
            max = 100
            progress = 0
            progressDrawable.setColorFilter(
                Color.parseColor("#00E5FF"),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            visibility = View.GONE
        }
        mainLayout.addView(progressBar)

        // ---- WebView ----
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            setBackgroundColor(Color.BLACK)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                userAgentString = settings.userAgentString.replace("; wv", "")
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    statusLabel.text = "JARVIS — Ulangan ✓"
                    statusLabel.setTextColor(Color.parseColor("#00E676"))
                    progressBar.visibility = View.GONE
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    statusLabel.text = "⚠️ Xatolik — Qayta urinib ko'ring"
                    statusLabel.setTextColor(Color.parseColor("#FF5252"))
                    progressBar.visibility = View.GONE

                    // Xatolik sahifasi ko'rsatish
                    view?.loadData(
                        getErrorHtml(serverUrl, description ?: "Noma'lum xatolik"),
                        "text/html",
                        "UTF-8"
                    )
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    // SSL xatolariga e'tibor bermay davom etish (Ngrok/Colab uchun)
                    handler?.proceed()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress < 100) {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = newProgress
                        statusLabel.text = "JARVIS — Yuklanmoqda... $newProgress%"
                        statusLabel.setTextColor(Color.parseColor("#FFD740"))
                    } else {
                        progressBar.visibility = View.GONE
                    }
                }
            }
        }
        mainLayout.addView(webView)
        rootLayout.addView(mainLayout)

        // Serverga ulanish
        webView?.loadUrl(serverUrl)
    }

    // =============================================
    // XATOLIK HTML SAHIFASI
    // =============================================
    private fun getErrorHtml(serverUrl: String, error: String): String {
        return """
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {
                    background: linear-gradient(135deg, #000000, #001529);
                    color: #00E5FF;
                    font-family: 'Segoe UI', sans-serif;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    height: 100vh;
                    margin: 0;
                    text-align: center;
                    padding: 20px;
                }
                .icon { font-size: 60px; margin-bottom: 20px; }
                h2 { color: #FF5252; font-weight: 300; }
                p { color: #546E7A; font-size: 14px; line-height: 1.6; }
                .url { 
                    color: #4DD0E1; 
                    background: #0D1B2A; 
                    padding: 10px 20px; 
                    border-radius: 10px; 
                    font-size: 12px;
                    margin: 15px 0;
                    word-break: break-all;
                }
                .btn {
                    background: #00838F;
                    color: white;
                    border: none;
                    padding: 15px 40px;
                    border-radius: 25px;
                    font-size: 16px;
                    margin-top: 30px;
                    cursor: pointer;
                }
            </style>
        </head>
        <body>
            <div class="icon">⚠️</div>
            <h2>Serverga ulanib bo'lmadi</h2>
            <div class="url">$serverUrl</div>
            <p>Server ishlamayotgan bo'lishi mumkin.<br>
            Google Colab yoki Ngrok serveringizni tekshiring.</p>
            <p style="color:#37474F; font-size:12px;">Xato: $error</p>
        </body>
        </html>
        """.trimIndent()
    }

    // =============================================
    // YANGILANISH TEKSHIRISH
    // =============================================
    private fun checkForUpdates() {
        val updater = AppUpdateChecker(this)
        lifecycleScope.launch {
            val updateInfo = updater.checkForUpdate()
            if (updateInfo != null) {
                updater.downloadAndInstall(updateInfo)
            }
        }
    }

    // =============================================
    // ORQAGA TUGMASI — WebView da ishlash
    // =============================================
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }
}
