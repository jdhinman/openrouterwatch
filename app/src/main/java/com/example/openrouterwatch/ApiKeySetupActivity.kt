package com.example.openrouterwatch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity

class ApiKeySetupActivity : ComponentActivity() {
    private lateinit var apiKeyInput: EditText
    private lateinit var saveButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_key_setup)
        
        apiKeyInput = findViewById(R.id.apiKeyInput)
        saveButton = findViewById(R.id.saveButton)
        
        // Check if API key already exists
        val sharedPrefs = getSharedPreferences("OpenRouterPrefs", Context.MODE_PRIVATE)
        val existingKey = sharedPrefs.getString("api_key", "")
        if (!existingKey.isNullOrEmpty()) {
            apiKeyInput.setText(existingKey)
        }
        
        saveButton.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Please enter your API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Save API key securely
            sharedPrefs.edit().putString("api_key", apiKey).apply()
            
            // Go to main activity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
