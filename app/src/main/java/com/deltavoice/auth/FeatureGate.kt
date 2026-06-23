package com.deltavoice.auth

import android.content.Context
import android.widget.Toast
import com.deltavoice.R
import com.deltavoice.api.CompleteVoiceWorkflowService

object FeatureGate {

    suspend fun ensureAuthenticated(context: Context): Result<Unit> {
        AuthManager.refreshSessionIfNeeded()
        return if (AuthManager.isLoggedIn() && AuthManager.getAccessToken() != null) {
            Result.success(Unit)
        } else {
            Result.failure(AuthManager.AuthRequiredException())
        }
    }

    suspend fun ensurePremium(context: Context): Result<Unit> {
        val auth = ensureAuthenticated(context)
        if (auth.isFailure) return auth
        AuthManager.syncSubscriptionStatus()
        return if (AuthManager.isPremium()) {
            Result.success(Unit)
        } else {
            Result.failure(AuthManager.PremiumRequiredException())
        }
    }

    fun showAuthError(context: Context, error: Throwable) {
        val message = when (error) {
            is AuthManager.AuthRequiredException ->
                context.getString(R.string.please_sign_in_first)
            is AuthManager.PremiumRequiredException ->
                context.getString(R.string.upgrade_premium_unlock_theme)
            is CompleteVoiceWorkflowService.HttpStatusException -> when (error.statusCode) {
                401 -> context.getString(R.string.please_sign_in_first)
                403 -> context.getString(R.string.upgrade_premium_unlock_theme)
                429 -> "Rate limit reached. Please wait a moment and try again."
                413 -> "File too large. Please use a shorter recording."
                else -> "Something went wrong. Please try again."
            }
            else -> "Something went wrong. Please try again."
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
