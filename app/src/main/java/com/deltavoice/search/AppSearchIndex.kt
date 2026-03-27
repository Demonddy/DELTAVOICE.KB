package com.deltavoice.search

import android.content.Context
import android.content.SharedPreferences
import com.deltavoice.AccountActivity
import com.deltavoice.AIChatConfigActivity
import com.deltavoice.MainActivity
import com.deltavoice.PermissionsActivity
import com.deltavoice.SettingsActivity
import com.deltavoice.ThemesActivity
import com.deltavoice.VideoConfigActivity
import com.deltavoice.VideoRecordingActivity
import com.deltavoice.VoiceConfigActivity
import com.deltavoice.R

/**
 * Builds searchable rows from static catalog, [deltavoice_prefs] stats, subscription state,
 * and [clipboard_prefs] history (same keys as [com.deltavoice.MainKeyboardService]).
 */
object AppSearchIndex {

    private const val PREFS_NAME = "deltavoice_prefs"
    private const val KEY_STAT_VIDEOS = "stat_videos"
    private const val KEY_STAT_VOICES = "stat_voices"
    private const val KEY_STAT_CHATS = "stat_chats"
    private const val KEY_IS_PREMIUM = "is_premium"

    private const val CLIPBOARD_PREFS = "clipboard_prefs"
    private const val CLIPBOARD_HISTORY_KEY = "clipboard_history"
    private const val CLIPBOARD_DELIMITER = "\u001E"

    fun search(context: Context, query: String): List<SearchableItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return buildAll(context).filter { it.matches(q) }
    }

    private fun buildAll(context: Context): List<SearchableItem> {
        val c = context.applicationContext
        return staticCatalog(c) +
            activityStats(c) +
            subscriptionRow(c) +
            clipboardRows(c)
    }

    private fun blob(vararg parts: String): String =
        parts.joinToString(" ").lowercase()

    private fun staticCatalog(ctx: Context): List<SearchableItem> {
        val catF = ctx.getString(R.string.search_category_features)
        val catP = ctx.getString(R.string.search_category_policy)
        val catS = ctx.getString(R.string.search_category_services)

        return listOf(
            SearchableItem(
                id = "feat_video",
                categoryLabel = catF,
                title = ctx.getString(R.string.video_title),
                subtitle = ctx.getString(R.string.record_translate_videos),
                matchBlob = blob(
                    ctx.getString(R.string.video_title),
                    ctx.getString(R.string.record_translate_videos),
                    "video translate record upload camera media"
                ),
                action = SearchAction.LaunchActivity(VideoConfigActivity::class.java)
            ),
            SearchableItem(
                id = "feat_voice",
                categoryLabel = catF,
                title = ctx.getString(R.string.voice),
                subtitle = ctx.getString(R.string.voice_input_cloning),
                matchBlob = blob(
                    ctx.getString(R.string.voice),
                    ctx.getString(R.string.voice_input_cloning),
                    "voice speech microphone clone stt transcribe audio"
                ),
                action = SearchAction.LaunchActivity(VoiceConfigActivity::class.java)
            ),
            SearchableItem(
                id = "feat_ai",
                categoryLabel = catF,
                title = ctx.getString(R.string.ai_chat_title),
                subtitle = ctx.getString(R.string.smart_ai_assistant),
                matchBlob = blob(
                    ctx.getString(R.string.ai_chat_title),
                    ctx.getString(R.string.smart_ai_assistant),
                    "ai chat assistant llm openai"
                ),
                action = SearchAction.LaunchActivity(AIChatConfigActivity::class.java)
            ),
            SearchableItem(
                id = "feat_themes",
                categoryLabel = catF,
                title = ctx.getString(R.string.themes),
                subtitle = ctx.getString(R.string.customize_keyboard),
                matchBlob = blob(
                    ctx.getString(R.string.themes),
                    ctx.getString(R.string.customize_keyboard),
                    "theme appearance keyboard skin"
                ),
                action = SearchAction.LaunchActivity(ThemesActivity::class.java)
            ),
            SearchableItem(
                id = "feat_account",
                categoryLabel = catF,
                title = ctx.getString(R.string.account),
                subtitle = ctx.getString(R.string.manage_profile_login),
                matchBlob = blob(
                    ctx.getString(R.string.account),
                    ctx.getString(R.string.manage_profile_login),
                    "profile login sign user email"
                ),
                action = SearchAction.LaunchActivity(AccountActivity::class.java)
            ),
            SearchableItem(
                id = "feat_camera",
                categoryLabel = catF,
                title = ctx.getString(R.string.record_video),
                subtitle = ctx.getString(R.string.nav_camera),
                matchBlob = blob(
                    ctx.getString(R.string.record_video),
                    "camera recording film capture"
                ),
                action = SearchAction.LaunchActivity(VideoRecordingActivity::class.java)
            ),
            SearchableItem(
                id = "feat_keyboard_ime",
                categoryLabel = catF,
                title = ctx.getString(R.string.enable_keyboard),
                subtitle = ctx.getString(R.string.tap_to_enable_keyboard),
                matchBlob = blob(
                    ctx.getString(R.string.enable_keyboard),
                    ctx.getString(R.string.tap_to_enable_keyboard),
                    "input method ime keyboard enable system settings"
                ),
                action = SearchAction.OpenKeyboardSettings
            ),
            SearchableItem(
                id = "feat_settings",
                categoryLabel = catF,
                title = ctx.getString(R.string.settings),
                subtitle = ctx.getString(R.string.search_settings_subtitle),
                matchBlob = blob(
                    ctx.getString(R.string.settings),
                    "preferences options sound vibration height"
                ),
                action = SearchAction.LaunchActivity(SettingsActivity::class.java)
            ),
            SearchableItem(
                id = "feat_permissions",
                categoryLabel = catF,
                title = ctx.getString(R.string.search_permissions_title),
                subtitle = ctx.getString(R.string.search_permissions_subtitle),
                matchBlob = blob(
                    ctx.getString(R.string.search_permissions_title),
                    ctx.getString(R.string.search_permissions_subtitle),
                    "microphone camera storage permission allow"
                ),
                action = SearchAction.LaunchActivity(PermissionsActivity::class.java)
            ),
            SearchableItem(
                id = "feat_dictionary",
                categoryLabel = catF,
                title = ctx.getString(R.string.dictionary),
                subtitle = ctx.getString(R.string.search_dictionary_subtitle),
                matchBlob = blob(
                    ctx.getString(R.string.dictionary),
                    "dictionary definition words lookup translate overlay keyboard"
                ),
                action = SearchAction.LaunchActivity(MainActivity::class.java)
            ),
            SearchableItem(
                id = "pol_privacy",
                categoryLabel = catP,
                title = ctx.getString(R.string.privacy_policy),
                subtitle = ctx.getString(R.string.search_policy_open_settings),
                matchBlob = blob(
                    ctx.getString(R.string.privacy_policy),
                    "privacy data gdpr policy legal"
                ),
                action = SearchAction.LaunchActivity(SettingsActivity::class.java)
            ),
            SearchableItem(
                id = "pol_terms",
                categoryLabel = catP,
                title = ctx.getString(R.string.terms_of_service),
                subtitle = ctx.getString(R.string.search_policy_open_settings),
                matchBlob = blob(
                    ctx.getString(R.string.terms_of_service),
                    "terms conditions legal service"
                ),
                action = SearchAction.LaunchActivity(SettingsActivity::class.java)
            ),
            SearchableItem(
                id = "svc_translation",
                categoryLabel = catS,
                title = ctx.getString(R.string.translate_text),
                subtitle = ctx.getString(R.string.search_service_translation_desc),
                matchBlob = blob(
                    ctx.getString(R.string.translate_text),
                    ctx.getString(R.string.search_service_translation_desc),
                    "supabase edge function cloud"
                ),
                action = SearchAction.LaunchActivity(VoiceConfigActivity::class.java)
            ),
            SearchableItem(
                id = "svc_voice_stt",
                categoryLabel = catS,
                title = ctx.getString(R.string.search_service_voice_title),
                subtitle = ctx.getString(R.string.search_service_voice_desc),
                matchBlob = blob(
                    ctx.getString(R.string.search_service_voice_title),
                    ctx.getString(R.string.search_service_voice_desc),
                    "whisper speech recognition"
                ),
                action = SearchAction.LaunchActivity(VoiceConfigActivity::class.java)
            ),
            SearchableItem(
                id = "svc_ai_backend",
                categoryLabel = catS,
                title = ctx.getString(R.string.search_service_ai_backend_title),
                subtitle = ctx.getString(R.string.search_service_ai_backend_desc),
                matchBlob = blob(
                    ctx.getString(R.string.search_service_ai_backend_title),
                    ctx.getString(R.string.search_service_ai_backend_desc),
                    "server api network"
                ),
                action = SearchAction.LaunchActivity(AIChatConfigActivity::class.java)
            )
        )
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun activityStats(ctx: Context): List<SearchableItem> {
        val cat = ctx.getString(R.string.search_category_activity)
        val p = prefs(ctx)
        val v = p.getInt(KEY_STAT_VIDEOS, 0)
        val vo = p.getInt(KEY_STAT_VOICES, 0)
        val ch = p.getInt(KEY_STAT_CHATS, 0)
        return listOf(
            SearchableItem(
                id = "act_videos",
                categoryLabel = cat,
                title = ctx.getString(R.string.search_activity_videos_title, v),
                subtitle = ctx.getString(R.string.your_activity),
                matchBlob = blob(
                    ctx.getString(R.string.videos),
                    ctx.getString(R.string.your_activity),
                    "video stats count usage activity $v"
                ),
                action = SearchAction.LaunchActivity(AccountActivity::class.java)
            ),
            SearchableItem(
                id = "act_voices",
                categoryLabel = cat,
                title = ctx.getString(R.string.search_activity_voices_title, vo),
                subtitle = ctx.getString(R.string.your_activity),
                matchBlob = blob(
                    ctx.getString(R.string.voice_msgs),
                    ctx.getString(R.string.your_activity),
                    "voice stats count usage activity $vo"
                ),
                action = SearchAction.LaunchActivity(AccountActivity::class.java)
            ),
            SearchableItem(
                id = "act_chats",
                categoryLabel = cat,
                title = ctx.getString(R.string.search_activity_chats_title, ch),
                subtitle = ctx.getString(R.string.your_activity),
                matchBlob = blob(
                    ctx.getString(R.string.ai_chats),
                    ctx.getString(R.string.your_activity),
                    "ai chat stats count usage activity $ch"
                ),
                action = SearchAction.LaunchActivity(AIChatConfigActivity::class.java)
            )
        )
    }

    private fun subscriptionRow(ctx: Context): List<SearchableItem> {
        val cat = ctx.getString(R.string.search_category_subscription)
        val premium = prefs(ctx).getBoolean(KEY_IS_PREMIUM, false)
        val plan = if (premium) ctx.getString(R.string.premium) else ctx.getString(R.string.free_plan)
        val subtitle = ctx.getString(R.string.search_subscription_manage_hint)
        return listOf(
            SearchableItem(
                id = "sub_plan",
                categoryLabel = cat,
                title = ctx.getString(R.string.current_plan) + ": " + plan,
                subtitle = subtitle,
                matchBlob = blob(
                    plan,
                    ctx.getString(R.string.subscription),
                    ctx.getString(R.string.current_plan),
                    "premium free upgrade billing pay plan"
                ),
                action = SearchAction.LaunchActivity(AccountActivity::class.java)
            )
        )
    }

    private fun clipboardRows(ctx: Context): List<SearchableItem> {
        val cat = ctx.getString(R.string.search_category_clipboard)
        val raw = ctx.getSharedPreferences(CLIPBOARD_PREFS, Context.MODE_PRIVATE)
            .getString(CLIPBOARD_HISTORY_KEY, null) ?: return emptyList()
        val items = raw.split(CLIPBOARD_DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }
        return items.mapIndexed { index, text ->
            val short = if (text.length > 80) text.take(77) + "…" else text
            SearchableItem(
                id = "clip_$index",
                categoryLabel = cat,
                title = short,
                subtitle = ctx.getString(R.string.search_clipboard_tap_copy),
                matchBlob = blob(text, "clipboard history copy paste clip"),
                action = SearchAction.CopyToClipboard(text)
            )
        }
    }
}
