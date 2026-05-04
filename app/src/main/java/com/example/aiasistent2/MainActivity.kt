package com.example.aiasistent2

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var rootLayout: FrameLayout
    private var tts: TextToSpeech? = null
    private var activeApiKey: String? = null
    private var activeSendButton: TextView? = null

    // Chat state
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private var messagesLayout: LinearLayout? = null
    private var chatScrollView: ScrollView? = null
    private var isTyping = false

    companion object {
        private const val PREF_NAME = "jarvis_prefs"
        private const val KEY_API_KEY = "gemini_api_key"
        private const val GEMINI_MODEL = "gemini-2.5-flash"
        private const val REQ_SPEECH = 101
        private const val REQ_PERMISSIONS = 102
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
        tts = TextToSpeech(this, this)
        requestAssistantPermissions()
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
        activeApiKey = apiKey
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

        val chatBtn = TextView(this).apply {
            text = "CHAT"
            textSize = 12f
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#003344"))
                setStroke(2, Color.parseColor("#00838F"))
            }
            val size = 130
            layoutParams = LinearLayout.LayoutParams(size, size).apply { rightMargin = 12 }
            isClickable = true
            isFocusable = true
        }
        chatBtn.setOnClickListener {
            inputField.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT)
        }
        inputBar.addView(chatBtn)

        val micBtn = TextView(this).apply {
            text = "MIC"
            textSize = 14f
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#003344"))
                setStroke(2, Color.parseColor("#00838F"))
            }
            val size = 130
            layoutParams = LinearLayout.LayoutParams(size, size).apply { rightMargin = 12 }
            isClickable = true
            isFocusable = true
        }
        micBtn.setOnClickListener {
            if (!isTyping) startVoiceInput()
        }
        inputBar.addView(micBtn)

        val controlBtn = TextView(this).apply {
            text = "A11Y"
            textSize = 12f
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#003344"))
                setStroke(2, Color.parseColor("#00838F"))
            }
            val size = 130
            layoutParams = LinearLayout.LayoutParams(size, size).apply { rightMargin = 12 }
            isClickable = true
            isFocusable = true
        }
        controlBtn.setOnClickListener {
            openAccessibilitySettings()
        }
        inputBar.addView(controlBtn)

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
        activeSendButton = sendBtn

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
        sendBtn.setOnClickListener {
            val msg = inputField.text.toString().trim()
            if (msg.isEmpty() || isTyping) return@setOnClickListener
            inputField.setText("")
            submitUserMessage(msg, apiKey, sendBtn)
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

    private fun submitUserMessage(msg: String, apiKey: String, sendBtn: TextView? = activeSendButton) {
        addMessage(msg, isUser = true)
        conversationHistory.add(Pair("user", msg))

        val localResult = executeLocalCommand(msg)
        if (localResult != null) {
            addAssistantMessage(localResult)
            return
        }

        isTyping = true
        sendBtn?.text = "..."
        val typingView = addTypingIndicator()

        lifecycleScope.launch {
            val rawReply = callGemini(apiKey)
            isTyping = false
            sendBtn?.text = ">"
            messagesLayout?.removeView(typingView)

            val reply = when {
                rawReply == null -> "Xatolik yuz berdi. Internet yoki API kalitni tekshiring."
                else -> executeToolPlan(rawReply) ?: rawReply
            }
            addAssistantMessage(reply)
            scrollToBottom()
        }
    }

    private fun addAssistantMessage(text: String) {
        addMessage(text, isUser = false)
        conversationHistory.add(Pair("model", text))
        speak(text)
    }

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAssistantPermissions()
            Toast.makeText(this, "Mikrofon ruxsatini bering", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Buyruqni ayting")
        }
        try {
            startActivityForResult(intent, REQ_SPEECH)
        } catch (e: Exception) {
            addAssistantMessage("Telefonda ovozni matnga aylantirish servisi topilmadi.")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SPEECH && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
            val key = activeApiKey
            if (!text.isNullOrBlank() && !key.isNullOrBlank()) {
                submitUserMessage(text, key)
            }
        }
    }

    private fun requestAssistantPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.CAMERA
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("uz", "UZ"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text.take(500), TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
    }

    private fun executeLocalCommand(raw: String): String? {
        val text = raw.lowercase(Locale.ROOT)
        return when {
            listOf("galereya", "gallery", "rasm", "foto").any { text.contains(it) } -> {
                openGallery()
                "Galereyani ochdim."
            }
            listOf("kamera", "camera").any { text.contains(it) } -> {
                openCamera()
                "Kamerani ochdim."
            }
            listOf("sozlama", "settings", "nastroyka").any { text.contains(it) } -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                "Sozlamalarni ochdim."
            }
            listOf("youtube", "telegram", "whatsapp", "chrome", "instagram").any { text.contains(it) } -> {
                val app = listOf("youtube", "telegram", "whatsapp", "chrome", "instagram").first { text.contains(it) }
                if (openAppByName(app)) "$app ilovasini ochdim." else "$app topilmadi."
            }
            text.contains("qidir") || text.contains("search") || text.contains("google") -> {
                val query = raw.replace("qidir", "", true).replace("search", "", true).replace("google", "", true).trim()
                openWebSearch(if (query.isBlank()) raw else query)
                "Qidiruvni ochdim."
            }
            text.contains("telefon qil") || text.contains("qo'ng'iroq") || text.contains("qongiroq") || text.contains("call") -> {
                val name = raw
                    .replace("telefon qil", "", true)
                    .replace("qo'ng'iroq qil", "", true)
                    .replace("qongiroq qil", "", true)
                    .replace("call", "", true)
                    .replace("kontaktimga", "", true)
                    .replace("kontaktga", "", true)
                    .trim()
                callContact(name)
            }
            text.contains("ovozni ko'tar") || text.contains("volume up") -> {
                changeVolume(AudioManager.ADJUST_RAISE)
                "Ovozni ko'tardim."
            }
            text.contains("ovozni pasaytir") || text.contains("volume down") -> {
                changeVolume(AudioManager.ADJUST_LOWER)
                "Ovozni pasaytirdim."
            }
            text.contains("orqaga") || text.contains("back") -> runAccessibilityAction("press_back", JSONObject())
            text.contains("home") || text.contains("bosh ekran") -> runAccessibilityAction("press_home", JSONObject())
            text.contains("pastga") || text.contains("scroll down") -> runAccessibilityAction("scroll_down", JSONObject())
            text.contains("yuqoriga") || text.contains("scroll up") -> runAccessibilityAction("scroll_up", JSONObject())
            text.contains("ekranni o'qi") || text.contains("ekranda nima") || text.contains("screen text") -> runAccessibilityAction("read_screen", JSONObject())
            text.startsWith("bos ") || text.startsWith("bosing ") || text.startsWith("tap ") -> {
                val target = raw
                    .replace("bosing", "", true)
                    .replace("bos", "", true)
                    .replace("tap", "", true)
                    .trim()
                runAccessibilityAction("tap_text", JSONObject().put("text", target))
            }
            text.startsWith("yoz ") || text.startsWith("type ") -> {
                val value = raw
                    .replace("yoz", "", true)
                    .replace("type", "", true)
                    .trim()
                runAccessibilityAction("type_text", JSONObject().put("text", value))
            }
            text.contains("accessibility") || text.contains("ruxsat") -> {
                openAccessibilitySettings()
                "Accessibility sozlamasini ochdim. JARVIS xizmatini yoqing."
            }
            else -> null
        }
    }

    private fun executeToolPlan(rawReply: String): String? {
        val jsonText = rawReply.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = try { JSONObject(jsonText) } catch (_: Exception) { return null }
        val steps = json.optJSONArray("steps")
        if (steps != null) {
            val results = mutableListOf<String>()
            for (i in 0 until steps.length()) {
                val step = steps.optJSONObject(i) ?: continue
                val result = executeSingleTool(step.optString("tool"), step.optJSONObject("args") ?: step.optJSONObject("parameters") ?: JSONObject())
                if (result.isNotBlank()) results.add(result)
            }
            return if (results.isEmpty()) "Vazifa bajarildi." else results.last()
        }
        val tool = json.optString("tool")
        val args = json.optJSONObject("args") ?: JSONObject()
        val result = executeSingleTool(tool, args)
        return result.ifBlank { null }
    }

    private fun executeSingleTool(tool: String, args: JSONObject): String {
        return when (tool) {
            "open_gallery" -> { openGallery(); "Galereyani ochdim." }
            "open_camera" -> { openCamera(); "Kamerani ochdim." }
            "open_settings" -> { startActivity(Intent(Settings.ACTION_SETTINGS)); "Sozlamalarni ochdim." }
            "open_accessibility_settings" -> { openAccessibilitySettings(); "Accessibility sozlamasini ochdim. JARVIS xizmatini yoqing." }
            "open_app" -> {
                val app = args.optString("app_name")
                if (openAppByName(app)) "$app ilovasini ochdim." else "$app topilmadi."
            }
            "call_contact" -> callContact(args.optString("contact_name"))
            "web_search" -> { openWebSearch(args.optString("query")); "Qidiruvni ochdim." }
            "tap_text", "type_text", "press_back", "press_home", "press_recents", "scroll_down", "scroll_up", "read_screen" -> {
                runAccessibilityAction(tool, args)
            }
            else -> ""
        }
    }

    private fun runAccessibilityAction(tool: String, args: JSONObject): String {
        val service = JarvisAccessibilityService.instance
        if (service == null) {
            openAccessibilitySettings()
            return "Telefonni to'liq boshqarish uchun Accessibility oynasida JARVIS xizmatini yoqing."
        }
        return when (tool) {
            "tap_text" -> {
                val text = args.optString("text")
                if (service.clickText(text)) "$text bosildi." else "$text ekranda topilmadi."
            }
            "type_text" -> {
                val text = args.optString("text")
                if (service.typeIntoFocused(text)) "Matn yozildi." else "Yozish maydoni fokusda emas."
            }
            "press_back" -> if (service.back()) "Orqaga qaytdim." else "Orqaga qaytish bajarilmadi."
            "press_home" -> if (service.home()) "Bosh ekranga qaytdim." else "Home bajarilmadi."
            "press_recents" -> if (service.recents()) "Oxirgi ilovalarni ochdim." else "Recent bajarilmadi."
            "scroll_down" -> if (service.scrollForward()) "Pastga scroll qildim." else "Scroll qilinadigan joy topilmadi."
            "scroll_up" -> if (service.scrollBackward()) "Yuqoriga scroll qildim." else "Scroll qilinadigan joy topilmadi."
            "read_screen" -> {
                val screen = service.screenText()
                if (screen.isBlank()) "Ekrandagi matn topilmadi." else "Ekranda: ${screen.take(600)}"
            }
            else -> "Noma'lum accessibility action."
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        startActivity(intent)
    }

    private fun openCamera() {
        startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
    }

    private fun openWebSearch(query: String) {
        val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun openAppByName(appName: String): Boolean {
        if (appName.isBlank()) return false
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val match = apps.firstOrNull {
            packageManager.getApplicationLabel(it).toString().lowercase(Locale.ROOT).contains(appName.lowercase(Locale.ROOT)) ||
                it.packageName.lowercase(Locale.ROOT).contains(appName.lowercase(Locale.ROOT))
        } ?: return false
        val intent = packageManager.getLaunchIntentForPackage(match.packageName) ?: return false
        startActivity(intent)
        return true
    }

    private fun callContact(name: String): String {
        if (name.isBlank()) return "Kimga qo'ng'iroq qilishni ayting."
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestAssistantPermissions()
            return "Kontaktlarni o'qish ruxsatini bering."
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val contactName = cursor.getString(nameIndex) ?: continue
                if (contactName.lowercase(Locale.ROOT).contains(name.lowercase(Locale.ROOT))) {
                    val number = cursor.getString(numberIndex)
                    val intent = if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                    } else {
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                    }
                    startActivity(intent)
                    return "$contactName kontaktiga qo'ng'iroq qilyapman."
                }
            }
        }
        return "$name kontaktlardan topilmadi."
    }

    private fun changeVolume(direction: Int) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
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
                        "Siz JARVIS, Android telefonni boshqaradigan assistantisiz. " +
                        "Mark-XXXIX protokoli: tez, aniq, foydalanuvchi tilida javob berasiz. " +
                        "Telefon amalini so'rasa 'qila olmayman' demang; tool JSON qaytaring. " +
                        "Bitta amal uchun: {\"tool\":\"tool_name\",\"args\":{...}}. " +
                        "Murakkab 2-5 qadamli vazifa uchun: {\"steps\":[{\"tool\":\"open_app\",\"args\":{\"app_name\":\"Telegram\"}},{\"tool\":\"tap_text\",\"args\":{\"text\":\"Jahongir\"}}]}. " +
                        "Toollar: open_gallery, open_camera, open_settings, open_accessibility_settings, open_app(app_name), call_contact(contact_name), web_search(query), " +
                        "tap_text(text), type_text(text), press_back, press_home, press_recents, scroll_down, scroll_up, read_screen. " +
                        "Agar oddiy savol bo'lsa JSON emas, qisqa tabiiy javob bering. " +
                        "Agar ekranni bosish yoki yozish kerak bo'lsa accessibility toollardan foydalaning."
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

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
