package com.deltavoice

import android.content.Context

/**
 * Locally persisted ElevenLabs Instant Voice Clone ID for Translate My Same Voice.
 */
object SavedVoiceClone {
    private const val KEY_VOICE_ID = "saved_clone_elevenlabs_voice_id"
    private const val KEY_NAME = "saved_clone_name"
    private const val KEY_CREATED_AT = "saved_clone_created_at"

    data class Clone(val voiceId: String, val name: String, val createdAt: Long)

    fun get(context: Context): Clone? {
        val prefs = OverlayPrefs.prefs(context)
        val id = prefs.getString(KEY_VOICE_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val name = prefs.getString(KEY_NAME, "My Voice") ?: "My Voice"
        return Clone(id, name, prefs.getLong(KEY_CREATED_AT, 0L))
    }

    fun save(context: Context, voiceId: String, name: String) {
        OverlayPrefs.prefs(context).edit()
            .putString(KEY_VOICE_ID, voiceId)
            .putString(KEY_NAME, name)
            .putLong(KEY_CREATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun styleFor(voiceId: String): String = "clone_$voiceId"
}
