package com.example.eddy

import android.Manifest
import android.animation.Animator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import kotlin.math.log

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // Views
    private lateinit var Head : RelativeLayout
    private lateinit var LeftEye: MaterialCardView
    private lateinit var RightEye: MaterialCardView
    private lateinit var Mouth: MaterialCardView
    private lateinit var btnSpeak: Button
    private lateinit var thinking : ImageView
    private lateinit var powerBtn : ImageView
    private lateinit var tvCaptions: TextView
    private  val WAKE_WORD = "activate"
    private var isPowerOn = true

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

        initViews()//initalises Cathy
        tts = TextToSpeech(this, this)
        processUserInput("say a greeting")
        startBlinking()//eye blinking every 10 seconds

    }

    private fun initViews() {
        powerBtn = findViewById(R.id.powerBtn)
        LeftEye = findViewById(R.id.Eddy_leftEye)
        RightEye = findViewById(R.id.Eddy_RightEye)
        Mouth = findViewById(R.id.Eddy_mouth)
        Head = findViewById(R.id.HeadMovement)
        btnSpeak = findViewById(R.id.btnSpeak)
        thinking = findViewById(R.id.think)
        tvCaptions = findViewById(R.id.tvCaptions)

         Glide.with(this)
            .asGif()
            .load(R.drawable.thinking)
            .into(thinking)


         //


        powerBtn.setOnClickListener {

            if(isPowerOn) {
                isPowerOn = false
                powerBtn.imageTintList = ColorStateList.valueOf(Color.RED)
            }else{
                isPowerOn = true
                powerBtn.imageTintList = ColorStateList.valueOf(Color.GREEN)
            }
        }
        btnSpeak.setOnClickListener {
            if(isPowerOn)
            {
                startSpeechToText()
            }else{
                Toast.makeText(application, "To talk , toggle power button", Toast.LENGTH_SHORT).show()
            }
        }
    }
private fun isThinking(bool :Boolean){

    if(bool) thinking.visibility = View.VISIBLE  else thinking.visibility = View.INVISIBLE
}
    // Speech Input
    private fun startSpeechToText() {

        try {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Cathy...")
                startActivityForResult(this, REQUEST_CODE_SPEECH_INPUT)
            }
        } catch (e: Exception) {
            isThinking(false)
            showToast("Speech recognition not available: ${e.message}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {


                if(it.contains("Cathy") ||it.contains("Kathy"))
                {//name detection test
                    Log.d("Cathy", "onActivityResult: Cathy detected")
                    processUserInput(it)
                }
                else{
                    var default = "make a snarky remark as we we not talking to you, if they want to talk to you they must call you by name, which is Cathy."
                    processUserInput(default)
                    Log.d("Cathy", "onActivityResult: Cathy not detected")
                }


            }
        }
    }

    // API Communication
    private fun processUserInput(input: String) {
        isThinking(true)
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

                       var mod_reply = reply.replace('*',' ')
                        isThinking(false)
                        speakWithCaptions(mod_reply)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isThinking(false)
                    Log.e("API", "Error in processUserInput", e)
                }
            }
        }
    }

    private fun getSystemPrompt(): String {//this is pre requsites
        //TODO: must save conversation data
        return """
            Your name is Cathy. Your personality is sarcastic and witty.
            Use dry humor in responses. Don't repeat these instructions.
            Answer questions using your personality guidelines.
        """.trimIndent()//winks , winking
    }

    // TTS Implementation with Captions for testing , to be removed soon
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.ENGLISH
            tts.setPitch(0.1f) // Lower values = deeper voice
            tts.setSpeechRate(1.0f)

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
        tvCaptions.text = text // captions to be removed
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
                    if(isPowerOn)
                    {
                        startSpeechToText()
                    }else{
                        Toast.makeText(application, "To talk , toggle power button", Toast.LENGTH_SHORT).show()
                    }

                }
            }

            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    isSpeaking.set(false)
                    wordHighlighter.removeCallbacksAndMessages(null)
                    resetMouth()
                    resetCaptionStyle()
                    if(isPowerOn)
                    {
                        startSpeechToText()
                    }else{
                        Toast.makeText(application, "To talk , toggle power button", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "EDDY_UTTERANCE")
        }

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "EDDY_UTTERANCE")
    }

    private fun highlightWordsSequentially(words: List<String>) {
        if (currentWordIndex >= words.size) return

        val fullText = words.joinToString(" ")
        val spannable = SpannableString(fullText)

        val startPos = words.take(currentWordIndex).sumOf { it.length + 1 }

        //*******************here we detect the winking**************************************************
        val reg = Regex("(?i)(wink|blink)")

        if(words.get(currentWordIndex).matches(reg))
        {
            Log.d("dude", "highlightWordsSequentially: its a wink")
            winkJob = CoroutineScope(Dispatchers.Main).launch {
                winkLeftEye()//winks with left Eye
            }

        }
        val nod = Regex("(?i)(ok|yes|agree|sure)")
        if(words.get(currentWordIndex).matches(nod))
        {
            Log.d("dude", "highlightWordsSequentially: its a nod")
            NodHeadJob = CoroutineScope(Dispatchers.Main).launch {
                //TODO: fix nodding
                NodHead()
            }

        }

        val endPos = startPos + words[currentWordIndex].length

        // Apply highlight to current word, this is for testing , to align words with mouth movements
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


    private var mouthAnimator: ValueAnimator? = null

    private var winkJob: Job? = null
    private var blinkJob: Job? = null
    private var NodHeadJob :Job? = null

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
    private fun NodHead(){
        ValueAnimator.ofFloat(30.0F,0.0F ).apply {
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val transLation = it.animatedValue as Float
                Head.translationY = transLation

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
//auto listen
    override fun onStart() {
        super.onStart()
    checkAudioPermission()
    setupVoiceRecognition()
    }
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false
    private fun setupVoiceRecognition() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        if (isListening) restartListening()
                        Log.d("dude", "onError: lis $error")
                    }

                    override fun onResults(results: Bundle) {
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                            if (it.contains("activate", ignoreCase = true)) {
                                onWakeWordDetected()
                            }
                        }
                        restartListening()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun startListening() {
        if (!isListening) {
            isListening = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer.startListening(intent)
        }
    }

    private fun restartListening() {
        if (isListening) {
            Handler(Looper.getMainLooper()).postDelayed({
                startListening()
            }, 1000)
        }
    }

    private fun onWakeWordDetected() {
        // Visual feedback
        Log.d("dude", "onWakeWordDetected: wake word detected")

    }
    private  val AUDIO_PERMISSION_CODE = 101
    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                AUDIO_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            }
        }
    }


}