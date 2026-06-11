import { createClient, type User } from "https://esm.sh/@supabase/supabase-js@2.45.0";

const ALLOWED_ORIGINS_RAW = Deno.env.get("ALLOWED_ORIGINS") || "*";
const ALLOWED_ORIGINS = ALLOWED_ORIGINS_RAW === "*"
  ? null
  : ALLOWED_ORIGINS_RAW.split(",").map((s: string) => s.trim()).filter(Boolean);

export function getCorsOrigin(req?: Request): string {
  if (!ALLOWED_ORIGINS) return "*";
  const origin = req?.headers.get("origin") || "";
  return ALLOWED_ORIGINS.includes(origin) ? origin : ALLOWED_ORIGINS[0] || "";
}

export const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": ALLOWED_ORIGINS ? "" : "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

export function corsHeadersForRequest(req: Request): Record<string, string> {
  return {
    ...corsHeaders,
    "Access-Control-Allow-Origin": getCorsOrigin(req),
  };
}

export type ApiFeature =
  | "ai-chat"
  | "writing-tool"
  | "translate-text"
  | "free-translate-text"
  | "free-voice-translate"
  | "voice-to-text"
  | "voice-conversion"
  | "create-voice-clone"
  | "complete-voice-workflow"
  | "check-subscription"
  | "create-checkout"
  | "customer-portal";

export type AuthContext = {
  user: User;
  isPremium: boolean;
  subscriptionTier: string | null;
};

type RateLimitConfig = { free: number; premium: number; windowMs: number };

export const RATE_LIMITS: Record<ApiFeature, RateLimitConfig> = {
  "ai-chat": { free: 30, premium: 300, windowMs: 3_600_000 },
  "writing-tool": { free: 40, premium: 400, windowMs: 3_600_000 },
  "translate-text": { free: 50, premium: 500, windowMs: 3_600_000 },
  "free-translate-text": { free: 50, premium: 500, windowMs: 3_600_000 },
  "free-voice-translate": { free: 10, premium: 100, windowMs: 3_600_000 },
  "voice-to-text": { free: 20, premium: 200, windowMs: 3_600_000 },
  "voice-conversion": { free: 15, premium: 150, windowMs: 3_600_000 },
  "create-voice-clone": { free: 0, premium: 20, windowMs: 86_400_000 },
  "complete-voice-workflow": { free: 10, premium: 100, windowMs: 3_600_000 },
  "check-subscription": { free: 10, premium: 30, windowMs: 3_600_000 },
  "create-checkout": { free: 5, premium: 10, windowMs: 3_600_000 },
  "customer-portal": { free: 5, premium: 10, windowMs: 3_600_000 },
};

/** Premium-only backend features. */
export const PREMIUM_FEATURES = new Set<ApiFeature>([
  "create-voice-clone",
  "free-voice-translate",
]);

function jsonResponse(body: Record<string, unknown>, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function decodeJwtRole(token: string): string | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const payload = JSON.parse(
      atob(parts[1].replace(/-/g, "+").replace(/_/g, "/")),
    );
    return typeof payload.role === "string" ? payload.role : null;
  } catch {
    return null;
  }
}

function serviceClient() {
  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!url || !key) {
    throw new Error("Supabase service credentials are not configured");
  }
  return createClient(url, key, { auth: { persistSession: false } });
}

async function loadSubscription(
  supabase: ReturnType<typeof serviceClient>,
  user: User,
): Promise<{ isPremium: boolean; subscriptionTier: string | null }> {
  const { data } = await supabase
    .from("subscribers")
    .select("subscribed, subscription_tier")
    .eq("user_id", user.id)
    .maybeSingle();

  if (data) {
    return {
      isPremium: data.subscribed === true,
      subscriptionTier: data.subscription_tier ?? null,
    };
  }

  if (user.email) {
    const { data: byEmail } = await supabase
      .from("subscribers")
      .select("subscribed, subscription_tier")
      .eq("email", user.email)
      .maybeSingle();
    if (byEmail) {
      return {
        isPremium: byEmail.subscribed === true,
        subscriptionTier: byEmail.subscription_tier ?? null,
      };
    }
  }

  return { isPremium: false, subscriptionTier: null };
}

/** Verify Bearer JWT and reject anonymous tokens. */
export async function authenticateRequest(
  req: Request,
): Promise<AuthContext | Response> {
  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return jsonResponse(
      { error: "Authentication required", code: "AUTH_REQUIRED" },
      401,
    );
  }

  const token = authHeader.slice("Bearer ".length).trim();
  if (!token) {
    return jsonResponse(
      { error: "Authentication required", code: "AUTH_REQUIRED" },
      401,
    );
  }

  const role = decodeJwtRole(token);
  if (role === "anon" || role === "service_role") {
    return jsonResponse(
      { error: "Sign in required. Anonymous access is not allowed.", code: "AUTH_REQUIRED" },
      401,
    );
  }

  let supabase: ReturnType<typeof serviceClient>;
  try {
    supabase = serviceClient();
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return jsonResponse({ error: message, code: "SERVER_MISCONFIGURED" }, 500);
  }

  const { data, error } = await supabase.auth.getUser(token);
  if (error || !data.user) {
    return jsonResponse(
      {
        error: "Invalid or expired session. Please sign in again.",
        code: "AUTH_INVALID",
      },
      401,
    );
  }

  const subscription = await loadSubscription(supabase, data.user);
  return { user: data.user, ...subscription };
}

/** Enforce per-user rate limits persisted in api_usage. */
export async function enforceRateLimit(
  ctx: AuthContext,
  feature: ApiFeature,
): Promise<Response | null> {
  if (PREMIUM_FEATURES.has(feature) && !ctx.isPremium) {
    return jsonResponse(
      {
        error: "Premium subscription required for this feature.",
        code: "PREMIUM_REQUIRED",
      },
      403,
    );
  }

  const limits = RATE_LIMITS[feature];
  const maxRequests = ctx.isPremium ? limits.premium : limits.free;
  if (maxRequests <= 0) {
    return jsonResponse(
      {
        error: "Premium subscription required for this feature.",
        code: "PREMIUM_REQUIRED",
      },
      403,
    );
  }

  const supabase = serviceClient();
  const windowStart = new Date(
    Math.floor(Date.now() / limits.windowMs) * limits.windowMs,
  ).toISOString();

  const { data: row, error: readError } = await supabase
    .from("api_usage")
    .select("request_count")
    .eq("user_id", ctx.user.id)
    .eq("feature", feature)
    .eq("window_start", windowStart)
    .maybeSingle();

  if (readError) {
    console.error("[security] rate limit read failed", readError);
    return jsonResponse(
      { error: "Rate limit check failed", code: "RATE_LIMIT_ERROR" },
      503,
    );
  }

  const current = row?.request_count ?? 0;
  if (current >= maxRequests) {
    return jsonResponse(
      {
        error: "Rate limit exceeded. Try again later or upgrade to Premium.",
        code: "RATE_LIMIT_EXCEEDED",
        limit: maxRequests,
        window_seconds: Math.floor(limits.windowMs / 1000),
      },
      429,
    );
  }

  const { error: writeError } = await supabase.from("api_usage").upsert(
    {
      user_id: ctx.user.id,
      feature,
      window_start: windowStart,
      request_count: current + 1,
      updated_at: new Date().toISOString(),
    },
    { onConflict: "user_id,feature,window_start" },
  );

  if (writeError) {
    console.error("[security] rate limit write failed", writeError);
    return jsonResponse(
      { error: "Rate limit update failed", code: "RATE_LIMIT_ERROR" },
      503,
    );
  }

  return null;
}

/** Authenticate + rate-limit in one call for edge handlers. */
export async function secureEdgeRequest(
  req: Request,
  feature: ApiFeature,
): Promise<AuthContext | Response> {
  const auth = await authenticateRequest(req);
  if (auth instanceof Response) return auth;
  const rateLimited = await enforceRateLimit(auth, feature);
  if (rateLimited) return rateLimited;
  return auth;
}
