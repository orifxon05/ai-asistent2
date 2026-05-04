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
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.aiasistent2.updater.AppUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var rootLayout: FrameLayout

    // Chat state
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private var messagesLayout: LinearLayout? = null
    private var chatScrollView: ScrollView? = null
    private var isTyping = false

    companion object {
        private const val PREF_NAME = "jarvis_prefs"
        private const val KEY_API_KEY = "gemini_api_key"
        private const val GEMINI_MODEL = "gemini-2.5-flash"
        private const val GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        setContentView(rootLayout)
        android.util.Log.d("JARVIS", "Application started")

        val savedKey = prefs.getString(KEY_API_KEY, null)
        if (!savedKey.isNullOrBlank()) {
            android.util.Log.d("JARVIS", "Saved key found: ${savedKey.take(10)}...")
            showChatScreen(savedKey)
        } else {
            android.util.Log.d("JARVIS", "No saved key, showing setup")
            showApiKeyScreen()
        }

        checkForUpdates()
    }

    // =============================================
    // API KEY INPUT SCREEN
    // =============================================
    @SuppressLint("SetTextI18n")
    private fun showApiKeyScreen() {
        rootLayout.removeAllViews()
        conversationHistory.clear()

        rootLayout.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#000000"),
                Color.parseColor("#000814"),
                Color.parseColor("#000a14")
            )
        )

        val scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(80, 120, 80, 120)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ---- Pulse circle ----
        val pulseContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 60
            }
        }
        val outerRing = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(200, 200, Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(3, Color.parseColor("#00E5FF"))
                setColor(Color.TRANSPARENT)
            }
        }
        outerRing.startAnimation(ScaleAnimation(
            0.8f, 1.2f, 0.8f, 1.2f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 2000; repeatCount = Animation.INFINITE; repeatMode = Animation.REVERSE
        })

        val innerCircle = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(120, 120, Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00334E"))
                setStroke(2, Color.parseColor("#00E5FF"))
            }
        }
        innerCircle.startAnimation(AlphaAnimation(0.4f, 1.0f).apply {
            duration = 1500; repeatCount = Animation.INFINITE; repeatMode = Animation.REVERSE
        })
        pulseContainer.addView(outerRing)
        pulseContainer.addView(innerCircle)
        container.addView(pulseContainer)

        // ---- JARVIS title ----
        container.addView(TextView(this).apply {
            text = "JARVIS"
            textSize = 38f
            typeface = Typeface.create("sans-serif-thin", Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            setShadowLayer(30f, 0f, 0f, Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        })

        container.addView(TextView(this).apply {
            text = "AI SYSTEM"
            textSize = 13f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Color.parseColor("#4DD0E1"))
            letterSpacing = 0.5f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 70 }
        })

        // ---- Label ----
        container.addView(TextView(this).apply {
            text = "🔑  Gemini API kalitini kiriting"
            textSize = 16f
            setTextColor(Color.parseColor("#80DEEA"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        })

        // ---- API Key input ----
        val keyInput = EditText(this).apply {
            hint = "AIzaSy..."
            setHintTextColor(Color.parseColor("#2A4A5A"))
            setTextColor(Color.parseColor("#E0F7FA"))
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            background = GradientDrawable().apply {
                cornerRadius = 30f
                setStroke(2, Color.parseColor("#00838F"))
                setColor(Color.parseColor("#0D1B2A"))
            }
            setPadding(50, 40, 50, 40)
            gravity = Gravity.CENTER
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14 }
        }
        container.addView(keyInput)

        container.addView(TextView(this).apply {
            text = "aistudio.google.com/app/api-keys saytidan oling"
            textSize = 12f
            setTextColor(Color.parseColor("#37474F"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 50 }
        })

        // ---- Status ----
        val statusText = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.parseColor("#FF5252"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        }
        container.addView(statusText)

        // ---- Connect button ----
        val connectBtn = TextView(this).apply {
            text = "⚡  BOSHLASH"
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 30f
                setColor(Color.parseColor("#00838F"))
            }
            setPadding(0, 42, 0, 42)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 30 }
            isClickable = true
            isFocusable = true
        }

            connectBtn.setOnClickListener {
            val key = keyInput.text.toString().trim()
            if (key.isEmpty() || !key.startsWith("AIza")) {
                statusText.text = "⚠️ Noto'g'ri API kalit formati (AIza bilan boshlanishi kerak)!"
                statusText.setTextColor(Color.parseColor("#FF5252"))
                return@setOnClickListener
            }
            connectBtn.text = "🔄  Tekshirilmoqda..."
            statusText.text = ""
            android.util.Log.d("JARVIS", "Testing API key...")

            lifecycleScope.launch {
                try {
                    val error = testApiKey(key)
                    if (error == null) {
                        android.util.Log.d("JARVIS", "API key valid, saving...")
                        prefs.edit().putString(KEY_API_KEY, key).apply()
                        showChatScreen(key)
                    } else {
                        android.util.Log.e("JARVIS", "API test failed: $error")
                        connectBtn.text = "⚡  BOSHLASH"
                        statusText.text = "❌ $error"
                        statusText.setTextColor(Color.parseColor("#FF5252"))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("JARVIS", "Exception during API test", e)
                    connectBtn.text = "⚡  BOSHLASH"
                    statusText.text = "❌ Kutilmagan xato: ${e.message}"
                }
            }
        }
        container.addView(connectBtn)

        container.addView(TextView(this).apply {
            text = "JARVIS v1.2  •  Antigravity AI"
            textSize = 11f
            setTextColor(Color.parseColor("#1A3A4A"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
        })

        scrollView.addView(container)
        rootLayout.addView(scrollView)
    }

    // =============================================
    // CHAT SCREEN
    // =============================================
    @SuppressLint("SetTextI18n")
    private fun showChatScreen(apiKey: String) {
        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.parseColor("#000814"))

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // ---- Top bar ----
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(30, 28, 30, 28)
            setBackgroundColor(Color.parseColor("#00050F"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(18, 18).apply { rightMargin = 15 }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E676"))
            }
        }
        statusDot.startAnimation(AlphaAnimation(0.3f, 1.0f).apply {
            duration = 1000; repeatCount = Animation.INFINITE; repeatMode = Animation.REVERSE
        })
        topBar.addView(statusDot)

        topBar.addView(TextView(this).apply {
            text = "JARVIS  —  Online"
            textSize = 15f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Settings / change key
        val settingsBtn = TextView(this).apply {
            text = "⚙️"
            textSize = 20f
            setPadding(15, 8, 15, 8)
            isClickable = true
            isFocusable = true
        }
        settingsBtn.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("API kalitni o'zgartirish")
                .setMessage("Hozirgi API kalit o'chiriladi. Davom etasizmi?")
                .setPositiveButton("Ha") { _, _ ->
                    prefs.edit().remove(KEY_API_KEY).commit()
                    conversationHistory.clear()
                    showApiKeyScreen()
                }
                .setNegativeButton("Yo'q", null)
                .show()
        }
        topBar.addView(settingsBtn)
        mainLayout.addView(topBar)

        // Separator
        mainLayout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#001a2e"))
        })

        // ---- Messages scroll ----
        val sv = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(Color.parseColor("#000814"))
            clipToPadding = false
            setPadding(0, 12, 0, 12)
        }
        chatScrollView = sv

        messagesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(20, 8, 20, 8)
        }
        sv.addView(messagesLayout)
        mainLayout.addView(sv)

        // Welcome message
        addMessage(
            "Salom! Men JARVIS — sizning shaxsiy AI yordamchingizman. " +
            "Savol bering yoki biror narsa haqida gaplashing, xush kelibsiz! 🤖",
            isUser = false
        )

        // ---- Input area ----
        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(16, 14, 16, 14)
            setBackgroundColor(Color.parseColor("#00050F"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val inputField = EditText(this).apply {
            hint = "Xabar yozing..."
            setHintTextColor(Color.parseColor("#2A4A5A"))
            setTextColor(Color.parseColor("#E0F7FA"))
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 5
            background = GradientDrawable().apply {
                cornerRadius = 25f
                setStroke(1, Color.parseColor("#00838F"))
                setColor(Color.parseColor("#0D1B2A"))
            }
            setPadding(40, 28, 40, 28)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 12
            }
        }
        inputBar.addView(inputField)

        val sendBtn = TextView(this).apply {
            text = "➤"
            textSize = 22f
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#003344"))
                setStroke(2, Color.parseColor("#00838F"))
            }
            val size = 130
            layoutParams = LinearLayout.LayoutParams(size, size)
            isClickable = true
            isFocusable = true
        }

        sendBtn.setOnClickListener {
            val msg = inputField.text.toString().trim()
            if (msg.isEmpty() || isTyping) return@setOnClickListener

            inputField.setText("")
            addMessage(msg, isUser = true)
            conversationHistory.add(Pair("user", msg))
            isTyping = true
            sendBtn.text = "⏳"

            val typingView = addTypingIndicator()

            lifecycleScope.launch {
                val reply = callGemini(apiKey)
                isTyping = false
                sendBtn.text = "➤"
                messagesLayout?.removeView(typingView)

                if (reply != null) {
                    addMessage(reply, isUser = false)
                    conversationHistory.add(Pair("model", reply))
                } else {
                    addMessage(
                        "⚠️ Xatolik yuz berdi. Internet yoki API kalitni tekshiring.",
                        isUser = false
                    )
                }
                scrollToBottom()
            }
        }
        inputBar.addView(sendBtn)
        mainLayout.addView(inputBar)

        rootLayout.addView(mainLayout)
    }

    // =============================================
    // ADD MESSAGE BUBBLE
    // =============================================
    private fun addMessage(text: String, isUser: Boolean): View {
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 15f
            setLineSpacing(0f, 1.3f)
            setTextColor(Color.parseColor(if (isUser) "#E0F7FA" else "#B2EBF2"))
            setPadding(42, 32, 42, 32)
            background = if (isUser) {
                GradientDrawable(
                    GradientDrawable.Orientation.BR_TL,
                    intArrayOf(Color.parseColor("#006064"), Color.parseColor("#00838F"))
                ).apply {
                    cornerRadii = floatArrayOf(30f, 30f, 8f, 30f, 30f, 30f, 30f, 30f)
                }
            } else {
                GradientDrawable().apply {
                    setColor(Color.parseColor("#0a1628"))
                    setStroke(1, Color.parseColor("#00838F"))
                    cornerRadii = floatArrayOf(8f, 30f, 30f, 30f, 30f, 30f, 30f, 8f)
                }
            }
        }

        bubble.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (isUser) Gravity.END else Gravity.START
            topMargin = 14
            if (isUser) leftMargin = 80 else rightMargin = 80
        }
        bubble.maxWidth = (resources.displayMetrics.widthPixels * 0.82).toInt()

        messagesLayout?.addView(bubble)
        scrollToBottom()
        return bubble
    }

    // =============================================
    // TYPING INDICATOR
    // =============================================
    private fun addTypingIndicator(): View {
        val dots = TextView(this).apply {
            text = "● ● ●"
            textSize = 14f
            setTextColor(Color.parseColor("#00838F"))
            setPadding(42, 28, 42, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0a1628"))
                setStroke(1, Color.parseColor("#00838F"))
                cornerRadii = floatArrayOf(8f, 30f, 30f, 30f, 30f, 30f, 30f, 8f)
            }
            startAnimation(AlphaAnimation(0.2f, 1.0f).apply {
                duration = 600; repeatCount = Animation.INFINITE; repeatMode = Animation.REVERSE
            })
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START
                topMargin = 14
                rightMargin = 80
            }
        }
        messagesLayout?.addView(dots)
        scrollToBottom()
        return dots
    }

    private fun scrollToBottom() {
        chatScrollView?.post { chatScrollView?.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // =============================================
    // GEMINI API CALL
    // =============================================
    private suspend fun callGemini(apiKey: String): String? = withContext(Dispatchers.IO) {
        try {
            val contentsArray = JSONArray()
            for ((role, content) in conversationHistory) {
                contentsArray.put(
                    JSONObject()
                        .put("role", role)
                        .put("parts", JSONArray().put(JSONObject().put("text", content)))
                )
            }

            val systemInstruction = JSONObject().put(
                "parts",
                JSONArray().put(
                    JSONObject().put(
                        "text",
                        "Siz JARVIS — aqlli, do'stona va foydali AI yordamchisiz. " +
                        "Qisqa va aniq javoblar bering. " +
                        "Foydalanuvchi qaysi tilda yozsa — Uzbek, Russian yoki English — " +
                        "shu tilda javob bering. Hech qachon teg yoki markdown ishlatmang."
                    )
                )
            )

            val body = JSONObject()
                .put("contents", contentsArray)
                .put("system_instruction", systemInstruction)
                .put(
                    "generationConfig", JSONObject()
                        .put("temperature", 0.7)
                        .put("maxOutputTokens", 2048)
                        .put("topP", 0.95)
                )

            val req = Request.Builder()
                .url("$GEMINI_URL?key=$apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { response ->
                val resBody = response.body?.string() ?: return@withContext null
                android.util.Log.d("JARVIS", "Gemini response code: ${response.code}")

                if (!response.isSuccessful) {
                    android.util.Log.e("JARVIS", "API Error: ${response.code} $resBody")
                    return@withContext null
                }

                val json = JSONObject(resBody)
                val candidates = json.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    val filters = json.optJSONArray("promptFeedback")
                    return@withContext "⚠️ Model javob bermadi (xavfsizlik filtri yoki boshqa cheklov)."
                }

                candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()
            }
        } catch (e: Exception) {
            android.util.Log.e("JARVIS", "callGemini error", e)
            null
        }
    }

    // =============================================
    // TEST API KEY (Returns null if OK, else error string)
    // =============================================
    private suspend fun testApiKey(key: String): String? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Hi")))
                )
            )
            val req = Request.Builder()
                .url("$GEMINI_URL?key=$key")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            httpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) return@withContext null
                
                val code = response.code
                val resStr = response.body?.string() ?: ""
                android.util.Log.e("JARVIS", "Test Key failed: $code $resStr")

                return@withContext when(code) {
                    400 -> "So'rovda xato (400)"
                    403 -> "API Key noto'g'ri yoki ruxsat yo'q (403)"
                    404 -> "Gemini modeli topilmadi: $GEMINI_MODEL (404)"
                    429 -> "Limit tugagan yoki tezkor so'rovlar (429)"
                    else -> "Xato: $code"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("JARVIS", "testApiKey exception", e)
            "Ulanishda xato: ${e.localizedMessage}"
        }
    }

    // =============================================
    // UPDATE CHECKER
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Chatda orqaga bosganda ilovadan chiqmaslik — fon rejimiga o'tish
        if (messagesLayout != null) {
            moveTaskToBack(true)
        } else {
            super.onBackPressed()
        }
    }
}
