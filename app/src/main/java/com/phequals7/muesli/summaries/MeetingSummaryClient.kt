package com.phequals7.muesli.summaries

import android.content.Context
import android.util.Log
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.meetings.MeetingTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AI meeting summaries, ported from muesli-ios MeetingSummaryClient.
 * Primary backend: ChatGPT (WHAM responses endpoint, SSE).
 * Secondary: OpenRouter BYOK (chat completions).
 */
class MeetingSummaryClient(context: Context) {

    private val store = SharedStore(context.applicationContext)
    private val auth = ChatGptAuthManager(context.applicationContext)
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS) // summaries can stream for a while
        .build()

    companion object {
        private const val TAG = "MeetingSummary"
        private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val WHAM_URL = "https://chatgpt.com/backend-api/wham/responses"
        private const val MAX_OUTPUT_TOKENS = 2500

        val CHATGPT_MODEL_PRESETS = listOf("gpt-5.5", "gpt-5.4-mini")
        val OPENROUTER_MODEL_PRESETS = listOf(
            "stepfun/step-3.5-flash:free",
            "nvidia/nemotron-3-super-120b-a12b:free",
            "nvidia/nemotron-3-nano-30b-a3b:free",
            "arcee-ai/trinity-large-preview:free",
        )

        private const val BASE_SUMMARY_INSTRUCTIONS =
            "You are a meeting notes assistant. Given a raw meeting transcript, produce concise, professional markdown notes.\n" +
                "Do not invent facts. Prefer concrete takeaways over filler. Capture owners only when they are actually mentioned.\n" +
                "If a requested section has no content, write \"None noted.\""

        private const val TITLE_INSTRUCTIONS =
            "Generate a short, descriptive meeting title (3-7 words) from this transcript.\n" +
                "Return only the title text, with no quotes, prefix, or explanation."
    }

    class SummaryException(message: String) : Exception(message)

    data class SummaryResult(
        val notes: String,
        val title: String,
        val backend: String,
        val model: String,
    )

    /** True when the selected backend has usable credentials. */
    fun isConfigured(): Boolean = when (store.summaryBackend) {
        "openrouter" -> store.openRouterApiKey.isNotBlank()
        else -> auth.isAuthenticated
    }

    suspend fun summarize(
        transcript: String,
        meetingTitle: String,
        template: MeetingTemplate,
        manualNotesToRetain: String? = null,
    ): SummaryResult {
        val backend = store.summaryBackend
        val model = if (backend == "openrouter") store.openRouterModel else store.chatGptModel
        val userPrompt = buildSummaryPrompt(transcript, meetingTitle, template, manualNotesToRetain)

        val notes = if (backend == "openrouter") {
            callOpenRouter(BASE_SUMMARY_INSTRUCTIONS, userPrompt, model, MAX_OUTPUT_TOKENS)
        } else {
            callWham(BASE_SUMMARY_INSTRUCTIONS, userPrompt, model)
        }
        if (notes.isBlank()) throw SummaryException("The summary came back empty.")

        val title = try {
            val generated = if (backend == "openrouter") {
                callOpenRouter(TITLE_INSTRUCTIONS, transcript.take(1500), model, 80)
            } else {
                callWham(TITLE_INSTRUCTIONS, transcript.take(1500), model)
            }
            generated.trim().trim('"').ifEmpty { meetingTitle }
        } catch (e: Exception) {
            Log.w(TAG, "title generation failed: ${e.message}")
            meetingTitle
        }

        return SummaryResult(notes = notes, title = title, backend = backend, model = model)
    }

    /** iOS failureNotes(): preserves the raw transcript and written notes. */
    fun failureNotes(
        transcript: String,
        meetingTitle: String,
        error: Throwable,
        manualNotes: String? = null,
    ): String = buildString {
        append("## Summary failed\n\nMeeting: ").append(meetingTitle)
        append("\n\nMuesli could not generate structured meeting notes.\n\n")
        append(error.message ?: "Unknown error")
        if (!manualNotes.isNullOrBlank()) {
            append("\n\n## Written Notes\n\n").append(manualNotes.trim())
        }
        append("\n\n## Raw Transcript\n\n").append(transcript)
    }

    // ── prompt ───────────────────────────────────────────────────────────

    private fun buildSummaryPrompt(
        transcript: String,
        meetingTitle: String,
        template: MeetingTemplate,
        manualNotesToRetain: String?,
    ): String = buildString {
        append("Meeting title: ").append(meetingTitle).append("\n\n")
        append("Meeting note template: ").append(template.label).append("\n\n")
        append("Template guidance:\n").append(template.instructions)
        if (!manualNotesToRetain.isNullOrBlank()) {
            append("\n\nUser-written notes to retain:\n").append(manualNotesToRetain.trim())
        }
        append("\n\nRaw transcript:\n").append(transcript)
    }

    // ── OpenRouter ───────────────────────────────────────────────────────

    private suspend fun callOpenRouter(
        systemPrompt: String,
        userPrompt: String,
        model: String,
        maxTokens: Int,
    ): String = withContext(Dispatchers.IO) {
        val apiKey = store.openRouterApiKey
        if (apiKey.isBlank()) throw SummaryException("OpenRouter is not configured. Add an API key in Settings.")

        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", userPrompt)))
            .put("max_tokens", maxTokens)

        val request = Request.Builder()
            .url(OPENROUTER_URL)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .header("X-OpenRouter-Title", "Muesli")
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SummaryException("OpenRouter failed (HTTP ${response.code}): ${extractErrorMessage(text).take(300)}")
            }
            val choices = JSONObject(text).optJSONArray("choices")
            val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            content.trim()
        }
    }

    // ── ChatGPT WHAM (SSE) ───────────────────────────────────────────────

    private suspend fun callWham(
        systemPrompt: String,
        userPrompt: String,
        model: String,
    ): String = withContext(Dispatchers.IO) {
        val (token, accountId) = try {
            auth.validAccessToken()
        } catch (e: Exception) {
            throw SummaryException(e.message ?: "Not signed in to ChatGPT.")
        }

        val body = JSONObject()
            .put("model", model)
            .put("store", false)
            .put("stream", true)
            .put("instructions", systemPrompt)
            .put("input", JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("content", JSONArray().put(
                        JSONObject().put("type", "input_text").put("text", userPrompt)
                    ))
            ))

        val requestBuilder = Request.Builder()
            .url(WHAM_URL)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $token")
        if (accountId.isNotEmpty()) {
            requestBuilder.header("ChatGPT-Account-Id", accountId)
        }

        http.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val errorText = response.body?.string().orEmpty()
                throw SummaryException("ChatGPT failed (HTTP ${response.code}): ${extractErrorMessage(errorText).take(300)}")
            }

            val fullText = StringBuilder()
            val source = response.body!!.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val payload = line.removePrefix("data: ").trim()
                if (payload == "[DONE]") break
                try {
                    val json = JSONObject(payload)
                    val outputText = json.optString("output_text")
                    if (outputText.isNotEmpty()) {
                        fullText.setLength(0)
                        fullText.append(outputText)
                    }
                    if (json.optString("type") == "response.output_text.delta") {
                        fullText.append(json.optString("delta"))
                    }
                } catch (_: Exception) {
                    // unparseable SSE frame — skip, like iOS
                }
            }
            fullText.toString().trim()
        }
    }

    private fun extractErrorMessage(body: String): String = try {
        val json = JSONObject(body)
        json.optJSONObject("error")?.optString("message")
            ?: json.optString("message").ifEmpty { json.optString("detail") }
                .ifEmpty { body.take(300) }
    } catch (e: Exception) {
        body.take(300)
    }
}
