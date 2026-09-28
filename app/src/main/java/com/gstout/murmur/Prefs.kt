package com.gstout.murmur

import android.content.Context

/** Settings, stored in app-private SharedPreferences. */
class Prefs(context: Context) {
    // File name predates the rename to Spoke; kept so existing settings survive updates.
    private val sp = context.getSharedPreferences("murmur", Context.MODE_PRIVATE)

    var provider: String
        get() = sp.getString("provider", PROVIDER_GROQ) ?: PROVIDER_GROQ
        set(v) = sp.edit().putString("provider", v).apply()

    var groqKey: String
        get() = sp.getString("groq_key", "") ?: ""
        set(v) = sp.edit().putString("groq_key", v.trim()).apply()

    var openaiKey: String
        get() = sp.getString("openai_key", "") ?: ""
        set(v) = sp.edit().putString("openai_key", v.trim()).apply()

    var anthropicKey: String
        get() = sp.getString("anthropic_key", "") ?: ""
        set(v) = sp.edit().putString("anthropic_key", v.trim()).apply()

    var cleanupEnabled: Boolean
        get() = sp.getBoolean("cleanup_enabled", true)
        set(v) = sp.edit().putBoolean("cleanup_enabled", v).apply()

    /** Names and jargon, one per line or comma-separated. */
    var vocabulary: String
        get() = sp.getString("vocabulary", "") ?: ""
        set(v) = sp.edit().putString("vocabulary", v).apply()

    /** Bubble offset from its default spot (right edge, just above the keyboard). */
    var bubbleDx: Int
        get() = sp.getInt("bubble_dx", 0)
        set(v) = sp.edit().putInt("bubble_dx", v).apply()

    var bubbleDy: Int
        get() = sp.getInt("bubble_dy", 0)
        set(v) = sp.edit().putInt("bubble_dy", v).apply()

    fun vocabularyTerms(): List<String> =
        vocabulary.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val PROVIDER_GROQ = "groq"
        const val PROVIDER_OPENAI = "openai"
    }
}
