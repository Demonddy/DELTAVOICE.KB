import { createClient, type User } from "https://esm.sh/@supabase/supabase-js@2.45.0";

/** Production CORS allowlist — override via ALLOWED_ORIGINS env (comma-separated). */
const DEFAULT_ALLOWED_ORIGINS = [
  "https://myapp.com",
  "https://www.myapp.com",
];

const ALLOWED_ORIGINS_RAW =
  Deno.env.get("ALLOWED_ORIGINS") || DEFAULT_ALLOWED_ORIGINS.join(",");
const ALLOWED_ORIGINS = ALLOWED_ORIGINS_RAW === "*"
  ? null
  : ALLOWED_ORIGINS_RAW.split(",").map((s: string) => s.trim()).filter(Boolean);

export function getAllowedOriginsList(): string[] {
  return ALLOWED_ORIGINS ?? [];
}

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

/** Structured server-side logging — never log user content at info level. */
export const logger = {
  error(tag: string, message: string, detail?: unknown): void {
    if (detail !== undefined) {
      console.error(`[${tag}] ${message}`, detail);
    } else {
      console.error(`[${tag}] ${message}`);
    }
  },
  warn(tag: string, message: string, detail?: unknown): void {
    if (detail !== undefined) {
      console.warn(`[${tag}] ${message}`, detail);
    } else {
      console.warn(`[${tag}] ${message}`);
    }
  },
};

/** Return generic JSON error to client; log details server-side only. */
export function jsonResponse(
  body: Record<string, unknown>,
  status: number,
  req?: Request,
): Response {
  const headers = req ? corsHeadersForRequest(req) : corsHeaders;
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...headers, "Content-Type": "application/json" },
  });
}

export function handleServerError(
  tag: string,
  error: unknown,
  req: Request,
  userMessage = "An unexpected error occurred. Please try again.",
): Response {
  const detail = error instanceof Error ? error.message : String(error);
  logger.error(tag, detail, error);
  return jsonResponse({ error: userMessage }, 500, req);
}

/**
 * Validate redirect origin against ALLOWED_ORIGINS allowlist.
 * Rejects requests when origin is missing or not on the list.
 */
export function validateRedirectOrigin(req: Request): string | Response {
  if (!ALLOWED_ORIGINS || ALLOWED_ORIGINS.length === 0) {
    logger.error("security", "ALLOWED_ORIGINS is not configured");
    return jsonResponse(
      { error: "Server misconfigured.", code: "SERVER_MISCONFIGURED" },
      500,
      req,
    );
  }

  const reqOrigin = req.headers.get("origin") || "";
  if (!reqOrigin || !ALLOWED_ORIGINS.includes(reqOrigin)) {
    return jsonResponse(
      { error: "Invalid request origin.", code: "INVALID_ORIGIN" },
      403,
      req,
    );
  }

  return reqOrigin;
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
  | "customer-portal"
  | "admin-dashboard";

export type AuthContext = {
  user: User;
  isPremium: boolean;
  subscriptionTier: string | null;
  role: string;
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
  "admin-dashboard": { free: 30, premium: 30, windowMs: 3_600_000 },
};

/** Premium-only backend features. */
export const PREMIUM_FEATURES = new Set<ApiFeature>([
  "create-voice-clone",
  "free-voice-translate",
]);

/** Admin-only backend features — require profiles.role === 'admin'. */
export const ADMIN_FEATURES = new Set<ApiFeature>(["admin-dashboard"]);

const PASSWORD_RESET_MAX = 3;
const PASSWORD_RESET_WINDOW_MS = 3_600_000;

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

async function loadUserRole(
  supabase: ReturnType<typeof serviceClient>,
  userId: string,
): Promise<string> {
  const { data } = await supabase
    .from("profiles")
    .select("role")
    .eq("id", userId)
    .maybeSingle();
  return data?.role ?? "user";
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

/** Verify Bearer JWT and reject anonymous tokens. Uses parameterized Supabase queries only. */
export async function authenticateRequest(
  req: Request,
): Promise<AuthContext | Response> {
  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return jsonResponse(
      { error: "Authentication required", code: "AUTH_REQUIRED" },
      401,
      req,
    );
  }

  const token = authHeader.slice("Bearer ".length).trim();
  if (!token) {
    return jsonResponse(
      { error: "Authentication required", code: "AUTH_REQUIRED" },
      401,
      req,
    );
  }

  const role = decodeJwtRole(token);
  if (role === "anon" || role === "service_role") {
    return jsonResponse(
      {
        error: "Sign in required. Anonymous access is not allowed.",
        code: "AUTH_REQUIRED",
      },
      401,
      req,
    );
  }

  let supabase: ReturnType<typeof serviceClient>;
  try {
    supabase = serviceClient();
  } catch (error) {
    logger.error("security", "Service client misconfigured", error);
    return jsonResponse(
      { error: "Server misconfigured.", code: "SERVER_MISCONFIGURED" },
      500,
      req,
    );
  }

  const { data, error } = await supabase.auth.getUser(token);
  if (error || !data.user) {
    return jsonResponse(
      {
        error: "Invalid or expired session. Please sign in again.",
        code: "AUTH_INVALID",
      },
      401,
      req,
    );
  }

  const [subscription, profileRole] = await Promise.all([
    loadSubscription(supabase, data.user),
    loadUserRole(supabase, data.user.id),
  ]);

  return { user: data.user, ...subscription, role: profileRole };
}

/** Require profiles.role === 'admin' before executing admin-only handlers. */
export function requireAdminRole(
  ctx: AuthContext,
  req: Request,
): Response | null {
  if (ctx.role !== "admin") {
    return jsonResponse(
      { error: "Admin access required.", code: "ADMIN_REQUIRED" },
      403,
      req,
    );
  }
  return null;
}

/** Enforce per-user rate limits persisted in api_usage (parameterized upsert). */
export async function enforceRateLimit(
  ctx: AuthContext,
  feature: ApiFeature,
  req?: Request,
): Promise<Response | null> {
  if (PREMIUM_FEATURES.has(feature) && !ctx.isPremium) {
    return jsonResponse(
      {
        error: "Premium subscription required for this feature.",
        code: "PREMIUM_REQUIRED",
      },
      403,
      req,
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
      req,
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
    logger.error("security", "rate limit read failed", readError);
    return null;
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
      req,
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
    logger.error("security", "rate limit write failed", writeError);
    return null;
  }

  return null;
}

/**
 * Rate limit password reset: max 3 requests per email per hour.
 * Uses parameterized queries against password_reset_rate_limits table.
 */
export async function enforcePasswordResetRateLimit(
  email: string,
  req: Request,
): Promise<Response | null> {
  const normalizedEmail = email.trim().toLowerCase();
  if (!normalizedEmail) {
    return jsonResponse(
      { error: "Email is required.", code: "INVALID_INPUT" },
      400,
      req,
    );
  }

  const supabase = serviceClient();
  const windowStart = new Date(
    Math.floor(Date.now() / PASSWORD_RESET_WINDOW_MS) * PASSWORD_RESET_WINDOW_MS,
  ).toISOString();

  const { data: row, error: readError } = await supabase
    .from("password_reset_rate_limits")
    .select("request_count")
    .eq("email", normalizedEmail)
    .eq("window_start", windowStart)
    .maybeSingle();

  if (readError) {
    logger.error("password-reset", "rate limit read failed", readError);
    return jsonResponse(
      { error: "Unable to process request. Please try again later." },
      500,
      req,
    );
  }

  const current = row?.request_count ?? 0;
  if (current >= PASSWORD_RESET_MAX) {
    return jsonResponse(
      {
        error: "Too many password reset attempts. Please try again in one hour.",
        code: "RATE_LIMIT_EXCEEDED",
      },
      429,
      req,
    );
  }

  const { error: writeError } = await supabase
    .from("password_reset_rate_limits")
    .upsert(
      {
        email: normalizedEmail,
        window_start: windowStart,
        request_count: current + 1,
        updated_at: new Date().toISOString(),
      },
      { onConflict: "email,window_start" },
    );

  if (writeError) {
    logger.error("password-reset", "rate limit write failed", writeError);
    return jsonResponse(
      { error: "Unable to process request. Please try again later." },
      500,
      req,
    );
  }

  return null;
}

/** Authenticate + optional admin check + rate-limit for edge handlers. */
export async function secureEdgeRequest(
  req: Request,
  feature: ApiFeature,
): Promise<AuthContext | Response> {
  const auth = await authenticateRequest(req);
  if (auth instanceof Response) return auth;

  if (ADMIN_FEATURES.has(feature)) {
    const adminDenied = requireAdminRole(auth, req);
    if (adminDenied) return adminDenied;
  }

  const rateLimited = await enforceRateLimit(auth, feature, req);
  if (rateLimited) return rateLimited;
  return auth;
}
