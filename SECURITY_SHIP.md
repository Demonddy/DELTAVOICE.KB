# Security Ship Checklist

Production security controls added for DeltaVoice.

## What is enforced now

### Authentication (JWT)
- All Supabase Edge Functions (including billing: `create-checkout`, `customer-portal`, `check-subscription`) require a **signed-in user JWT** via `secureEdgeRequest()`.
- Anonymous and service-role tokens are rejected server-side.
- Convex HTTP routes (`/ai-chat`, `/complete-voice-workflow`, `/video-workflow`) verify Supabase user JWT via Auth API.
- Android app uses **Supabase Auth** (`auth-kt`) with real sign-in/sign-up in Account screen.
- Web app uses `web/auth.js` with automatic **token refresh** before expiry.

### Rate limiting
- Supabase functions track usage in `api_usage` table (per user, per feature, rolling window).
- Billing functions (`check-subscription`, `create-checkout`, `customer-portal`) are rate-limited.
- Convex uses in-memory per-user limits (per deployment instance).
- Free vs Premium tiers have different limits.

### Authorization
- Premium-only: `create-voice-clone`, `free-voice-translate`, `complete-voice-workflow` voice-only mode (clone), Convex `/video-workflow`.
- Subscription status read from `subscribers` table (Stripe sync via `check-subscription` + **Stripe webhook**).

### Input validation
- Audio payload max: ~10 MB (base64 length 14M) on both Convex and Supabase voice endpoints.
- Video payload max: ~100 MB (base64 length 140M) on Convex video endpoint.
- OpenAI model parameter whitelisted server-side (`gpt-4o-mini`, `gpt-3.5-turbo`); client `model` param ignored if not in whitelist.
- AI chat `system` messages stripped from client input; server always injects its own system prompt.

### CORS
- Origin controlled via `ALLOWED_ORIGINS` env var (comma-separated) in both Supabase and Convex.
- Defaults to `*` only if `ALLOWED_ORIGINS` is not set; set it before production.

### Secrets management
- Supabase anon key, Convex URL removed from source code.
- Android: keys injected via `BuildConfig` from `local.properties` / CI.
- Web: keys injected via `window.DeltaVoiceConfig` at deploy time.
- AI API keys stored in **EncryptedSharedPreferences** (Android 6+, graceful fallback on older).

### Stripe webhook
- `stripe-webhook` edge function verifies Stripe signature and handles `customer.subscription.updated`, `customer.subscription.deleted`, `invoice.payment_failed`.
- Subscription status updated server-side without relying on client sync.

### Other fixes
- `get-deepgram-key` disabled (was leaking API keys).
- `subscribers` RLS: removed world-writable INSERT/UPDATE policies.
- Storage `translated-videos`: removed public write/delete; authenticated users scoped to own folder.
- Debug telemetry (`AgentDebugLog`, `DebugSession44`) guarded behind `BuildConfig.DEBUG`.
- Release builds use R8 minification (`minifyEnabled true`, `shrinkResources true`).
- Network security: cleartext disabled, user CAs only trusted in debug builds.
- Certificate pinning template configured (replace pins before production).
- Web security headers: HSTS, CSP, Referrer-Policy, Permissions-Policy added.
- Android HTTP error handling: 401/403/429/413 mapped to user-friendly messages via `HttpStatusException`.

## Required before production deploy

1. **Rotate Supabase anon key** in dashboard and set in `local.properties` / CI:
   ```
   SUPABASE_ANON_KEY=your_new_key_here
   SUPABASE_URL=https://rkfveqzktfmgegtsoxlf.supabase.co
   CONVEX_SITE_URL=https://kindred-curlew-363.eu-west-1.convex.site
   ```
2. **Apply migration** `20260219120000_auth_rate_limits_and_rls_fix.sql` to Supabase.
3. **Redeploy all edge functions** and Convex HTTP routes.
4. **Set Convex env vars**: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, `ALLOWED_ORIGINS`.
5. **Set Supabase edge function secrets**: `STRIPE_WEBHOOK_SECRET`, `ALLOWED_ORIGINS`.
6. **Register Stripe webhook** endpoint: `https://<supabase-url>/functions/v1/stripe-webhook`.
7. **Enable email confirmation** in Supabase Auth settings.
8. **Generate certificate pins** and replace `REPLACE_WITH_ACTUAL_PIN` in `network_security_config.xml`.
9. **Set `window.DeltaVoiceConfig`** in web deployment with production Supabase URL and anon key.

## Client behavior

- Users must sign in before voice/AI/video cloud features work.
- Unauthenticated requests receive `401 AUTH_REQUIRED`.
- Rate limit exceeded returns `429 RATE_LIMIT_EXCEEDED`.
- Premium features without subscription return `403 PREMIUM_REQUIRED`.
- Oversized payloads return `413` with a user-friendly message.
