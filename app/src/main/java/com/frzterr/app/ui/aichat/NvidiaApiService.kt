package com.frzterr.app.ui.aichat

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException

class NvidiaApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val apiKey  = "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"
    private val endpoint = "https://integrate.api.nvidia.com/v1/chat/completions"

    /**
     * Kirim pesan dengan streaming SSE.
     * @param onChunk  dipanggil setiap token baru (untuk update UI realtime)
     * @param onDone   dipanggil sekali saat selesai, berisi full teks bersih
     * @param onError  dipanggil bila ada error
     */
    fun sendMessage(
        history: List<AiChatMessage>,
        modelId: String,
        onChunk: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", "Kamu adalah asisten AI yang cerdas, ramah, dan membantu. Jawab dengan singkat dan jelas.")
            })
            for (msg in history) {
                put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }

        val bodyStr = JSONObject().apply {
            put("model", modelId)
            put("messages", messagesArray)
            put("max_tokens", 2048)
            put("temperature", 0.7)
            put("stream", true)
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyStr.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("Koneksi gagal: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errMsg = response.body?.string()?.take(300) ?: "kosong"
                    onError("Error ${response.code}: $errMsg")
                    return
                }

                val source = response.body?.source() ?: run {
                    onError("Response body kosong")
                    return
                }

                val fullText = StringBuilder()

                try {
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break

                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break

                        val chunk: String? = try {
                            val json = JSONObject(data)
                            val delta = json
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("delta")

                            when {
                                delta.has("content") && !delta.isNull("content") ->
                                    delta.getString("content")
                                else -> null
                            }
                        } catch (_: Exception) { null }

                        if (!chunk.isNullOrEmpty()) {
                            fullText.append(chunk)
                            onChunk(chunk)
                        }
                    }
                } catch (e: Exception) {
                    onError("Error saat membaca stream: ${e.message}")
                    return
                }

                // Bersihkan blok <think>...</think> dari model reasoning
                val result = fullText.toString()
                    .replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
                    .trim()

                if (result.isBlank()) {
                    onError("Respons kosong dari AI")
                } else {
                    onDone(result)
                }
            }
        })
    }
}
