package com.mjolnir.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val API_URL = "https://api.anthropic.com/v1/messages"
private const val MODEL = "claude-sonnet-4-20250514"
private const val MAX_TOKENS = 2048
private const val API_VERSION = "2023-06-01"

class ClaudeApiService(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun stream(
        history: List<Map<String, String>>,
        onToken: suspend (String) -> Unit,
        onError: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = buildRequest(history)
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onError("HTTP ${response.code}")
                    return@withContext
                }
                response.body?.source()?.let { source ->
                    while (!source.exhausted()) {
                        parseSSELine(source.readUtf8Line() ?: break)?.let { token ->
                            withContext(Dispatchers.Main) { onToken(token) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
        }
    }

    private fun buildRequest(history: List<Map<String, String>>): Request {
        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", MAX_TOKENS)
            put("stream", true)
            put("system", "You are MJOLNIR, an intelligent terminal assistant. " +
                "You help with security assessment, OSINT, system administration, " +
                "and code. Be concise and technically precise.")
            put("messages", JSONArray(history.map { msg ->
                JSONObject().put("role", msg["role"]).put("content", msg["content"])
            }))
        }.toString().toRequestBody("application/json".toMediaType())

        return Request.Builder()
            .url(API_URL)
            .post(body)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", API_VERSION)
            .build()
    }

    private fun parseSSELine(line: String): String? {
        if (!line.startsWith("data: ")) return null
        val data = line.removePrefix("data: ")
        if (data == "[DONE]") return null
        return runCatching {
            JSONObject(data).optJSONObject("delta")?.optString("text")?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}
