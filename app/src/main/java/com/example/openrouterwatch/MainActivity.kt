package com.example.openrouterwatch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var chatDisplay: TextView
    private lateinit var modelSelector: Button
    private lateinit var micButton: Button
    
    private lateinit var openRouterClient: OpenRouterClient
    private var selectedModel = "anthropic/claude-3-haiku"
    private val chatHistory = mutableListOf<Message>()
    
    private lateinit var gestureDetector: GestureDetectorCompat
    
    private var isModelSelectionMode = false
    private val availableModels = listOf(
        "anthropic/claude-3-haiku",
        "anthropic/claude-3-sonnet", 
        "anthropic/claude-3-opus",
        "openai/gpt-4",
        "openai/gpt-3.5-turbo",
        "meta/llama-3-70b-instruct"
    )
    private var modelSelectionIndex = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Get the API key from SharedPreferences
        val sharedPrefs = getSharedPreferences("OpenRouterPrefs", Context.MODE_PRIVATE)
        val apiKey = sharedPrefs.getString("api_key", "")
        
        if (apiKey.isNullOrEmpty()) {
            // No API key yet, go to setup
            startActivity(Intent(this, ApiKeySetupActivity::class.java))
            finish()
            return
        }
        
        // Initialize client with the stored API key
        openRouterClient = OpenRouterClient(apiKey)
        
        // Initialize UI components
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        chatDisplay = findViewById(R.id.chatDisplay)
        modelSelector = findViewById(R.id.modelSelector)
        micButton = findViewById(R.id.micButton)
        
        modelSelector.text = getDisplayNameForModel(selectedModel)
        
        // Set up click listeners
        sendButton.setOnClickListener {
            val userMessage = messageInput.text.toString().trim()
            if (userMessage.isNotEmpty()) {
                addMessage("user", userMessage)
                messageInput.text.clear()
                sendToAI()
            }
        }
        
        modelSelector.setOnClickListener {
            toggleModelSelectionMode()
        }
        
        micButton.setOnClickListener {
            startSpeechRecognition()
        }
        
        // Setup gesture detection for swipe (alternative to bezel rotation)
        setupGestureDetection()
    }
    
    private fun setupGestureDetection() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                
                // Check if the swipe is more horizontal than vertical
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (diffX > 0) {
                        // Swipe right - similar to clockwise bezel rotation
                        handleClockwiseRotation()
                    } else {
                        // Swipe left - similar to counter-clockwise bezel rotation
                        handleCounterClockwiseRotation()
                    }
                } else {
                    if (diffY > 0) {
                        // Swipe down
                        val scrollView = findViewById<ScrollView>(R.id.scrollView)
                        scrollView.smoothScrollBy(0, 50)
                    } else {
                        // Swipe up
                        val scrollView = findViewById<ScrollView>(R.id.scrollView)
                        scrollView.smoothScrollBy(0, -50)
                    }
                }
                
                return true
            }
        })
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }
    
    private fun handleClockwiseRotation() {
        if (isModelSelectionMode) {
            // Move to next model
            modelSelectionIndex = (modelSelectionIndex + 1) % availableModels.size
            updateModelSelectionDisplay()
        } else {
            // Normal scrolling in chat
            val scrollView = findViewById<ScrollView>(R.id.scrollView)
            scrollView.smoothScrollBy(0, 50)
        }
    }
    
    private fun handleCounterClockwiseRotation() {
        if (isModelSelectionMode) {
            // Move to previous model
            modelSelectionIndex = (modelSelectionIndex - 1 + availableModels.size) % availableModels.size
            updateModelSelectionDisplay()
        } else {
            // Normal scrolling in chat
            val scrollView = findViewById<ScrollView>(R.id.scrollView)
            scrollView.smoothScrollBy(0, -50)
        }
    }
    
    private fun toggleModelSelectionMode() {
        isModelSelectionMode = !isModelSelectionMode
        if (isModelSelectionMode) {
            // Enter model selection mode
            Toast.makeText(this, "Swipe left/right to select model", Toast.LENGTH_SHORT).show()
            modelSelectionIndex = availableModels.indexOf(selectedModel).coerceAtLeast(0)
            updateModelSelectionDisplay()
        } else {
            // Exit model selection mode
            selectedModel = availableModels[modelSelectionIndex]
            Toast.makeText(this, "Model selected: ${getDisplayNameForModel(selectedModel)}", Toast.LENGTH_SHORT).show()
            modelSelector.text = getDisplayNameForModel(selectedModel)
            // Restore chat display
            updateChatDisplay()
        }
    }
    
    private fun updateModelSelectionDisplay() {
        chatDisplay.text = "Select Model:\n\n${availableModels.mapIndexed { index, model ->
            if (index == modelSelectionIndex) "→ ${getDisplayNameForModel(model)}" 
            else "  ${getDisplayNameForModel(model)}"
        }.joinToString("\n")}"
    }
    
    private fun getDisplayNameForModel(modelId: String): String {
        return modelId.split("/").last().capitalize(Locale.ROOT)
    }
    
    private fun addMessage(role: String, content: String) {
        chatHistory.add(Message(role, content))
        updateChatDisplay()
    }
    
    private fun updateChatDisplay() {
        val displayText = chatHistory.joinToString("\n\n") { 
            if (it.role == "user") "You: ${it.content}" else "AI: ${it.content}" 
        }
        chatDisplay.text = displayText
        
        // Scroll to bottom
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
    
    private fun sendToAI() {
        lifecycleScope.launch {
            try {
                // Show loading state
                chatDisplay.append("\n\nAI: Loading...")
                
                val response = withContext(Dispatchers.IO) {
                    openRouterClient.chatWithAI(selectedModel, chatHistory)
                }
                
                // Remove loading text and add proper response
                chatHistory.removeIf { it.role == "assistant" && it.content == "Loading..." }
                addMessage("assistant", response)
            } catch (e: Exception) {
                // Remove loading text if present
                chatHistory.removeIf { it.role == "assistant" && it.content == "Loading..." }
                addMessage("assistant", "Error: ${e.message ?: "Unknown error"}")
            }
        }
    }
    
    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your message")
        }
        startActivityForResult(intent, SPEECH_REQUEST_CODE)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0) ?: ""
            messageInput.setText(spokenText)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
    
    companion object {
        private const val SPEECH_REQUEST_CODE = 100
    }
}

// Extension function to capitalize first letter of a string
fun String.capitalize(locale: Locale): String {
    return if (this.isEmpty()) "" else this[0].uppercaseChar() + this.substring(1)
}
