package com.example

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.Locale
import kotlin.math.abs

class FloatingWidgetService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingRootView: View? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false
    private var currentText = ""

    companion object {
        const val CHANNEL_ID = "floating_widget_channel"
        const val NOTIFICATION_ID = 2024
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        initFloatingOverlay()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart Voice Writer Floating Widget",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Enables floating microphone overlay for use over other apps"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Voice Writer Overlay Active")
            .setContentText("Floating mic is accessible across other apps. Tap to open.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFloatingOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val wmLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        val density = resources.displayMetrics.density

        // Root container
        val root = FrameLayout(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Circular Floating Mic Button (56dp x 56dp)
        val micButtonSize = (56 * density).toInt()
        val micButton = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF7C3AED.toInt()) // Electric Purple
                setStroke((2 * density).toInt(), 0xFFDDD6FE.toInt())
            }
            background = bg
            elevation = 12 * density
        }

        val micIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(Color.WHITE)
            val pad = (14 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        micButton.addView(
            micIcon,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Expandable mini card
        val cardWidth = (280 * density).toInt()
        val expandCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            val cardBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * density
                setColor(0xFF18181C.toInt()) // Deep Dark Card Surface
                setStroke((1.5f * density).toInt(), 0xFF7C3AED.toInt())
            }
            background = cardBg
            setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
            elevation = 16 * density
        }

        // Header Row in expandCard
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(this).apply {
            text = "Smart Voice Writer"
            setTextColor(0xFFE0E0E0.toInt())
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            this.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(0xFFA0A0A8.toInt())
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                expandCard.visibility = View.GONE
            }
        }
        headerRow.addView(titleText)
        headerRow.addView(closeBtn)
        expandCard.addView(headerRow)

        // Live text preview
        val transcriptView = TextView(this).apply {
            text = "Tap the mic to record voice over any app..."
            setTextColor(0xFFE0E0E0.toInt())
            textSize = 13f
            val textBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(0xFF121212.toInt())
                setStroke((1 * density).toInt(), 0xFF2C2C34.toInt())
            }
            background = textBg
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            this.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (70 * density).toInt()
            ).apply {
                topMargin = (8 * density).toInt()
                bottomMargin = (10 * density).toInt()
            }
        }
        expandCard.addView(transcriptView)

        // Actions Row
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val recordActionBtn = Button(this).apply {
            text = "Record"
            setTextColor(Color.WHITE)
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(0xFF7C3AED.toInt())
            }
            background = btnBg
            textSize = 12f
            setPadding((12 * density).toInt(), (4 * density).toInt(), (12 * density).toInt(), (4 * density).toInt())
            this.layoutParams = LinearLayout.LayoutParams(0, (38 * density).toInt(), 1f).apply {
                marginEnd = (6 * density).toInt()
            }
        }

        val copyActionBtn = Button(this).apply {
            text = "Copy"
            setTextColor(0xFFE0E0E0.toInt())
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(0xFF24242A.toInt())
            }
            background = btnBg
            textSize = 12f
            setPadding((12 * density).toInt(), (4 * density).toInt(), (12 * density).toInt(), (4 * density).toInt())
            this.layoutParams = LinearLayout.LayoutParams(0, (38 * density).toInt(), 1f).apply {
                marginEnd = (6 * density).toInt()
            }
            setOnClickListener {
                if (currentText.isNotBlank()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Smart Voice Writer", currentText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@FloatingWidgetService, "Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@FloatingWidgetService, "No text to copy", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val stopServiceBtn = Button(this).apply {
            text = "Exit"
            setTextColor(0xFFCF6679.toInt()) // Subdued Red
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(0xFF3B151D.toInt())
            }
            background = btnBg
            textSize = 12f
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            this.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (38 * density).toInt()
            )
            setOnClickListener {
                stopSelf()
            }
        }

        actionsRow.addView(recordActionBtn)
        actionsRow.addView(copyActionBtn)
        actionsRow.addView(stopServiceBtn)
        expandCard.addView(actionsRow)

        // Add mic button and expandable card to container
        container.addView(micButton, LinearLayout.LayoutParams(micButtonSize, micButtonSize))
        val cardLp = LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (8 * density).toInt()
        }
        container.addView(expandCard, cardLp)
        root.addView(container)

        floatingRootView = root

        // Setup Speech Recognition inside the floating widget
        recordActionBtn.setOnClickListener {
            if (!isRecording) {
                startOverlayDictation(recordActionBtn, transcriptView)
            } else {
                stopOverlayDictation(recordActionBtn, transcriptView)
            }
        }

        // Touch & Drag Handling on micButton
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        micButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = wmLayoutParams.x
                    initialY = wmLayoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (abs(deltaX) > 8 || abs(deltaY) > 8) {
                        isClick = false
                    }
                    wmLayoutParams.x = initialX + deltaX
                    wmLayoutParams.y = initialY + deltaY
                    windowManager?.updateViewLayout(floatingRootView, wmLayoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        expandCard.visibility = if (expandCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(floatingRootView, wmLayoutParams)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun startOverlayDictation(btn: Button, textOutput: TextView) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            currentText = "Speech recognition unavailable. Please use the main app."
            textOutput.text = currentText
            return
        }

        isRecording = true
        btn.text = "Stop"
        btn.setTextColor(0xFFFFD9DF.toInt())
        (btn.background as? GradientDrawable)?.setColor(0xFFCF6679.toInt()) // Red recording state
        textOutput.text = "Listening to your voice..."

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        stopOverlayDictation(btn, textOutput)
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val result = matches?.firstOrNull() ?: ""
                        if (result.isNotBlank()) {
                            currentText = result
                            textOutput.text = currentText
                        }
                        stopOverlayDictation(btn, textOutput)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { partial ->
                            currentText = partial
                            textOutput.text = partial
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            stopOverlayDictation(btn, textOutput)
        }
    }

    private fun stopOverlayDictation(btn: Button, textOutput: TextView) {
        isRecording = false
        btn.text = "Record"
        btn.setTextColor(Color.WHITE)
        val density = resources.displayMetrics.density
        (btn.background as? GradientDrawable)?.setColor(0xFF7C3AED.toInt())

        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
        if (floatingRootView != null && windowManager != null) {
            try {
                windowManager?.removeView(floatingRootView)
            } catch (e: Exception) {
                // View already detached
            }
            floatingRootView = null
        }
    }
}
