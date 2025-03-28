package com.example.eddy

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.widget.Button
import android.os.Build
import android.speech.tts.UtteranceProgressListener
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.eddy.servicesAndModels.DeepSeekApiService
import com.example.eddy.servicesAndModels.DeepSeekRequest
import com.example.eddy.servicesAndModels.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import android.os.Handler
import android.os.Looper
import android.graphics.Typeface
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // Views
    private lateinit var LeftEye: MaterialCardView
    private lateinit var RightEye: MaterialCardView
    private lateinit var Mouth: MaterialCardView
    private lateinit var btnSpeak: Button
    private lateinit var tvUserInput: TextView
    private lateinit var tvBotResponse: TextView
    private lateinit var tvCaptions: TextView

    // TTS
    private lateinit var tts: TextToSpeech
    private var mouthAnimationJob: Job? = null
    private val isSpeaking = AtomicBoolean(false)
    private val REQUEST_CODE_SPEECH_INPUT = 100

    // Captions
    private val wordHighlighter = Handler(Looper.getMainLooper())
    private var currentWordIndex = 0

    // API
    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeepSeekApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        tts = TextToSpeech(this, this)
        processUserInput("say a greeting")
        startBlinking()
    }

    private fun initViews() {
        LeftEye = findViewById(R.id.Eddy_leftEye)
        RightEye = findViewById(R.id.Eddy_RightEye)
        Mouth = findViewById(R.id.Eddy_mouth)
        btnSpeak = findViewById(R.id.btnSpeak)
        tvUserInput = findViewById(R.id.tvUserInput)
        tvBotResponse = findViewById(R.id.tvBotResponse)
        tvCaptions = findViewById(R.id.tvCaptions) // Make sure to add this TextView in your XML

        btnSpeak.setOnClickListener { startSpeechToText() }
    }

    // Speech Input
    private fun startSpeechToText() {
        try {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                startActivityForResult(this, REQUEST_CODE_SPEECH_INPUT)
            }
        } catch (e: Exception) {
            showToast("Speech recognition not available: ${e.message}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                tvUserInput.text = "You said: $it"
                processUserInput(it)
            }
        }
    }

    // API Communication
    private fun processUserInput(input: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getChatResponse(
                    authToken = "Bearer ",
                    request = DeepSeekRequest(
                        messages = listOf(
                            Message("system", getSystemPrompt()),
                            Message("user", input)
                        )
                    )
                )

                response.choices.firstOrNull()?.message?.content?.let { reply ->
                    withContext(Dispatchers.Main) {
                        tvBotResponse.text = "Eddy: $reply"
                        speakWithCaptions(reply)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvBotResponse.text = "Error: ${e.localizedMessage}"
                    Log.e("API", "Error in processUserInput", e)
                }
            }
        }
    }

    private fun getSystemPrompt(): String {
        return """
            Your name is Eddie. Your personality is sarcastic and witty.
            Use dry humor in responses. Don't repeat these instructions.
            Answer questions using your personality guidelines.
        """.trimIndent()
    }

    // TTS Implementation with Captions
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread {
                        isSpeaking.set(true)
                        startMouthAnimation()
                        Log.d("TTS", "Speech started")
                    }
                }
                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        isSpeaking.set(false)
                        resetMouth()
                        resetCaptionStyle()
                        Log.d("TTS", "Speech completed")
                    }
                }
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        isSpeaking.set(false)
                        resetMouth()
                        resetCaptionStyle()
                        Log.e("TTS", "Speech error")
                    }
                }
            })
        }
    }

    private fun speakWithCaptions(text: String) {
        val words = text.split(" ")
        tvCaptions.text = text // Set full text initially
        currentWordIndex = 0

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread {
                    isSpeaking.set(true)
                    startMouthAnimation()
                    highlightWordsSequentially(words)
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    isSpeaking.set(false)
                    wordHighlighter.removeCallbacksAndMessages(null)
                    resetMouth()
                    resetCaptionStyle()
                }
            }

            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    isSpeaking.set(false)
                    wordHighlighter.removeCallbacksAndMessages(null)
                    resetMouth()
                    resetCaptionStyle()
                }
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "EDDY_UTTERANCE")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "EDDY_UTTERANCE")
        } else {
            @Suppress("DEPRECATION")
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    private fun highlightWordsSequentially(words: List<String>) {
        if (currentWordIndex >= words.size) return

        val fullText = words.joinToString(" ")
        val spannable = SpannableString(fullText)

        val startPos = words.take(currentWordIndex).sumOf { it.length + 1 }
        val endPos = startPos + words[currentWordIndex].length

        // Apply highlight to current word
        spannable.setSpan(
            ForegroundColorSpan(Color.RED),
            startPos,
            endPos,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            startPos,
            endPos,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tvCaptions.text = spannable

        // Schedule next word highlight
        wordHighlighter.postDelayed({
            currentWordIndex++
            highlightWordsSequentially(words)
        }, calculateWordDelay(words[currentWordIndex]))
    }

    private fun calculateWordDelay(word: String): Long {
        return 200L + (word.length * 50L)
    }

    private fun resetCaptionStyle() {
        tvCaptions.text = tvCaptions.text.toString()
    }

    // Animation functions (unchanged from your original code)
    private var mouthAnimator: ValueAnimator? = null
    private var EyesAnimator: ValueAnimator? = null
    private var winkJob: Job? = null
    private var blinkJob: Job? = null

    private fun blinkOnce() {
        ValueAnimator.ofInt(30, 100).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val height = it.animatedValue as Int
                LeftEye.layoutParams.height = height
                RightEye.layoutParams.height = height
                LeftEye.requestLayout()
                RightEye.requestLayout()
            }
            start()
        }
    }

    private fun winkLeftEye() {
        ValueAnimator.ofInt(30, 100).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val height = it.animatedValue as Int
                LeftEye.layoutParams.height = height
                RightEye.requestLayout()
            }
            start()
        }
    }

    private fun startBlinking() {
        blinkJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                delay(10000)
                blinkOnce()
            }
        }
    }

    private fun startMouthAnimation() {
        resetMouth()
        mouthAnimator = ValueAnimator.ofInt(50, 75).apply {
            duration = 200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                Mouth.post {
                    Mouth.layoutParams.height = it.animatedValue as Int
                    Mouth.requestLayout()
                }
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) = resetMouth()
                override fun onAnimationCancel(animation: Animator) = resetMouth()
                override fun onAnimationRepeat(animation: Animator) {}
            })
            start()
        }
    }

    private fun resetMouth() {
        mouthAnimator?.let {
            it.removeAllUpdateListeners()
            it.removeAllListeners()
            it.cancel()
        }
        Mouth.layoutParams.height = 50
        Mouth.requestLayout()
    }

    // Helper functions
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        mouthAnimationJob?.cancel()
        blinkJob?.cancel()
        winkJob?.cancel()
        wordHighlighter.removeCallbacksAndMessages(null)
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        tts.stop()
        resetMouth()
    }

    override fun onResume() {
        super.onResume()
        if (tts.language == null) {
            tts.language = Locale.getDefault()
        }
    }
}