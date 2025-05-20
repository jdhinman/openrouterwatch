package com.example.openrouterwatch

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterService {
    @POST("chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): ChatResponse
}

data class ChatRequest(
    val model: String,
    val messages: List<Message>
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val id: String,
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)

class OpenRouterClient(private val apiKey: String) {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/api/v1/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val service = retrofit.create(OpenRouterService::class.java)
    
    suspend fun chatWithAI(model: String, messages: List<Message>): String {
        val request = ChatRequest(model, messages)
        val response = service.getChatCompletion("Bearer $apiKey", request)
        return response.choices.firstOrNull()?.message?.content ?: "No response"
    }
}
