package com.example.eddy

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Intent

import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale

import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Job

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean


class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // Views
    private lateinit var LeftEye: MaterialCardView
    private lateinit var RightEye: MaterialCardView
    private lateinit var Mouth: MaterialCardView
    private lateinit var btnSpeak: Button
    private lateinit var tvUserInput: TextView
    private lateinit var tvBotResponse: TextView

    // TTS
    private lateinit var tts: TextToSpeech
    private var mouthAnimationJob: Job? = null
    private val isSpeaking = AtomicBoolean(false)
    private val REQUEST_CODE_SPEECH_INPUT = 100

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
        processUserInput("say a greeting ")
        startBlinking()
    }

    private fun initViews() {
        LeftEye = findViewById(R.id.Eddy_leftEye)
        RightEye = findViewById(R.id.Eddy_RightEye)
        Mouth = findViewById(R.id.Eddy_mouth)
        btnSpeak = findViewById(R.id.btnSpeak)
        tvUserInput = findViewById(R.id.tvUserInput)
        tvBotResponse = findViewById(R.id.tvBotResponse)

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
                        speakOut(reply)
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

    // TTS Implementation
// TTS Implementation
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
                        Log.d("TTS", "Speech completed")
                    }
                }
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        isSpeaking.set(false)
                        resetMouth()
                        Log.e("TTS", "Speech error")
                    }
                }
            })
        }
    }

    private fun speakOut(text: String) {
        mouthAnimationJob?.cancel()
        resetMouth() // Reset before new speech

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "EDDY_UTTERANCE")
        }

        // Clear any pending utterances
        tts.stop()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "EDDY_UTTERANCE")
            if (result == TextToSpeech.ERROR) {
                Log.e("TTS", "Failed to speak")
            }
        } else {
            @Suppress("DEPRECATION")
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    override fun onStart() {
        super.onStart()
        initViews()
        tts = TextToSpeech(this, this)
        speakOut("say a greeting ")
    }

    // Mouth Animation
    private var mouthAnimator: ValueAnimator? = null
    private var EyesAnimator: ValueAnimator? = null

    private fun blinkOnce() {
        ValueAnimator.ofInt(30, 200).apply {
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
    private var blinkJob: Job? = null

    private fun startBlinking() {
        blinkJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {  // Continue indefinitely
                delay(10000)    // Wait 10 seconds
                blinkOnce()     // Execute blink
            }
        }
    }
    private fun startMouthAnimation() {
        // Cancel any existing animation first
        resetMouth()

        mouthAnimator = ValueAnimator.ofInt(30, 150).apply {
            duration = 200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener {
                Mouth.post { // Ensure UI thread safety
                    Mouth.layoutParams.height = it.animatedValue as Int
                    Mouth.requestLayout()
                }
            }

            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    Log.d("dagger", "Mouth animation started")

                }
                override fun onAnimationEnd(animation: Animator) {
                    Log.d("dagger", "Mouth animation ended")

                    resetMouth() // Extra safety
                }
                override fun onAnimationCancel(animation: Animator) {
                    Log.d("dagger", "Mouth animation cancelled")
                    resetMouth() // Extra safety
                }
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
        Mouth.layoutParams.height = 30
        Mouth.requestLayout()
        Log.d("Animation", "Mouth fully reset")
    }

    // Helper
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        mouthAnimationJob?.cancel()
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
        // Reinitialize TTS if needed
        if (tts.language == null) {
            tts.language = Locale.getDefault()
        }
    }
}