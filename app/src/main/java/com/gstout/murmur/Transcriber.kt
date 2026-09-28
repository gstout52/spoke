package com.gstout.murmur

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Speech-to-text via a Whisper-style /audio/transcriptions endpoint (Groq or OpenAI). */
object Transcriber {
    private val http = OkHttpClient.Builder()
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    fun transcribe(prefs: Prefs, audio: File): String {
        val (url, key, model) = when (prefs.provider) {
            Prefs.PROVIDER_OPENAI -> Triple(
                "https://api.openai.com/v1/audio/transcriptions",
                prefs.openaiKey,
                "gpt-4o-mini-transcribe",
            )
            else -> Triple(
                "https://api.groq.com/openai/v1/audio/transcriptions",
                prefs.groqKey,
                "whisper-large-v3-turbo",
            )
        }
        if (key.isBlank()) throw IOException("Add a transcription API key in the Murmur app")

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audio.name, audio.asRequestBody("audio/mp4".toMediaType()))
            .addFormDataPart("model", model)
            .addFormDataPart("language", "en")
            .addFormDataPart("response_format", "json")
            .apply {
                // Whisper uses the prompt as spelling context for names and jargon.
                val terms = prefs.vocabularyTerms()
                if (terms.isNotEmpty()) {
                    addFormDataPart("prompt", terms.joinToString(", ").take(800))
                }
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Transcription failed (${resp.code}): ${text.take(200)}")
            }
            return JSONObject(text).optString("text").trim()
        }
    }
}
