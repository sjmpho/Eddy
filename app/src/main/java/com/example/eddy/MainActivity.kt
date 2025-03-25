package com.example.eddy

import android.content.Intent
import android.graphics.Bitmap

import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity


import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.eddy.servicesAndModels.DeepSeekApiService
import com.example.eddy.servicesAndModels.DeepSeekRequest
import com.example.eddy.servicesAndModels.Message

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import kotlin.math.log
import androidx.core.graphics.createBitmap
import androidx.core.view.size
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Job

import kotlinx.coroutines.delay


class MainActivity : AppCompatActivity(),TextToSpeech.OnInitListener {

    private lateinit var LeftEye : MaterialCardView
    private lateinit var RightEye : MaterialCardView
    private lateinit var Mouth : MaterialCardView
    private lateinit var btnSpeak: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvUserInput: TextView
    private lateinit var tvBotResponse: TextView
    private lateinit var tts: TextToSpeech

    private val REQUEST_CODE_SPEECH_INPUT = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        LeftEye = findViewById(R.id.Eddy_leftEye)
        RightEye = findViewById(R.id.Eddy_RightEye)
        Mouth = findViewById(R.id.Eddy_mouth)
        btnSpeak = findViewById(R.id.btnSpeak)
        tvStatus = findViewById(R.id.tvStatus)
        tvUserInput = findViewById(R.id.tvUserInput)
        tvBotResponse = findViewById(R.id.tvBotResponse)

        // Initialize TextToSpeech


        tts = TextToSpeech(this, this)

        btnSpeak.setOnClickListener {
            startSpeechToText()
        }
    }

    override fun onStart() {
        super.onStart()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                processUserInput(preRequsites())
            } catch (e: Exception) {
                tvBotResponse.text = "Error: ${e.message}"
            }
        }
    }
    private fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_SPEECH_INPUT -> {
                if (resultCode == RESULT_OK && data != null) {
                    val spokenText = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
                    spokenText?.let { userInput ->
                        tvUserInput.text = "You said: $userInput"

                        // Fallback using Main dispatcher
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                processUserInput(userInput)
                            } catch (e: Exception) {
                                tvBotResponse.text = "Error: ${e.message}"
                            }
                        }
                    }
                }
            }
        }
    }
    private suspend fun processUserInput(input: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/") // Confirm the actual API URL
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(DeepSeekApiService::class.java)
        val messages = listOf(Message("user", preRequsites()+input))

        try {
            val response = service.getChatResponse(
                authToken = "Bearer sk-78f4384683604045b1b5925c52a99988", // Replace with your key
                request = DeepSeekRequest(messages = messages)
            )

            if (response.choices.isNotEmpty()) {
                val botResponse = response.choices[0].message.content
                runOnUiThread {
                    tvBotResponse.text = "Bot says: $botResponse"
                    speakOut(botResponse)
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                Log.d("dagger", "processUserInput: ${e.message}")
                tvBotResponse.text = "Error: ${e.localizedMessage}"
            }
        }
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.getDefault())

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Text-to-Speech initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun preRequsites() :String{

        var name : String =" Eddie"
        var personality : String = "Sarcastic and witty, Use dry humor in responses."


    return "this is your personlity guideline , you name is $name , your personality is $personality,dont repeat this back , answer questions using the personality guidelines question is : "
    }
    private var mouthAnimationJob: Job? = null

    private fun speakOut(text: String) {
        // Cancel any previous animation
        mouthAnimationJob?.cancel()

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "").also {
            if (it == TextToSpeech.SUCCESS) {
                startMouthAnimation()
            }
        }
    }

    private fun startMouthAnimation() {
        mouthAnimationJob = CoroutineScope(Dispatchers.Main).launch {
            val mouthView = Mouth // Assuming this is your view

            while (tts.isSpeaking) {
                // Close mouth
                mouthView.layoutParams.height = 30
                mouthView.requestLayout() // This is crucial!
                delay(150) // Short delay for closing

                // Open mouth
                mouthView.layoutParams.height = 150
                mouthView.requestLayout() // This is crucial!
                delay(150) // Short delay for opening
            }

            // Reset mouth when done
            mouthView.layoutParams.height = 30
            mouthView.requestLayout()
        }
    }
    private fun StopSpeech(){

    }
    private fun genericResponses(Statement : String){
        //these are generic responses
        if(Statement.contains(" your name")){

        }


    }
    override fun onDestroy() {
        // Shutdown TextToSpeech
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}