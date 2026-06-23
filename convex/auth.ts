/**
 * Convex HTTP auth: verify Supabase user JWT, enforce rate limits and premium gates.
 */

export type ConvexFeature = "ai-chat" | "complete-voice-workflow" | "video-workflow";

type AuthContext = {
  userId: string;
  email: string | null;
  isPremium: boolean;
  subscriptionTier: string | null;
};

type RateLimitConfig = { free: number; premium: number; windowMs: number };

const RATE_LIMITS: Record<ConvexFeature, RateLimitConfig> = {
  "ai-chat": { free: 30, premium: 300, windowMs: 3_600_000 },
  "complete-voice-workflow": { free: 10, premium: 100, windowMs: 3_600_000 },
  "video-workflow": { free: 0, premium: 50, windowMs: 3_600_000 },
};

const PREMIUM_FEATURES = new Set<ConvexFeature>(["video-workflow"]);

const rateLimitCache = new Map<string, { count: number; windowStart: number }>();

function jsonResponse(body: Record<string, unknown>, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function decodeJwtRole(token: string): string | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const payload = JSON.parse(atob(parts[1].replace(/-/g, "+").replace(/_/g, "/")));
    return typeof payload.role === "string" ? payload.role : null;
  } catch {
    return null;
  }
}

async function verifySupabaseUser(token: string): Promise<AuthContext | Response> {
  const supabaseUrl = process.env.SUPABASE_URL;
  const supabaseAnonKey =
    process.env.SUPABASE_ANON_KEY || process.env.SUPABASE_PUBLISHABLE_KEY;

  if (!supabaseUrl || !supabaseAnonKey) {
    return jsonResponse(
      {
        error: "Auth is not configured on the server.",
        code: "SERVER_MISCONFIGURED",
      },
      500,
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
    );
  }

  const userRes = await fetch(`${supabaseUrl.replace(/\/$/, "")}/auth/v1/user`, {
    headers: {
      Authorization: `Bearer ${token}`,
      apikey: supabaseAnonKey,
    },
  });

  if (!userRes.ok) {
    return jsonResponse(
      {
        error: "Invalid or expired session. Please sign in again.",
        code: "AUTH_INVALID",
      },
      401,
    );
  }

  const user = (await userRes.json()) as { id?: string; email?: string };
  if (!user.id) {
    return jsonResponse(
      { error: "Invalid user session.", code: "AUTH_INVALID" },
      401,
    );
  }

  let isPremium = false;
  let subscriptionTier: string | null = null;

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (serviceKey) {
    const subRes = await fetch(
      `${supabaseUrl.replace(/\/$/, "")}/rest/v1/subscribers?user_id=eq.${user.id}&select=subscribed,subscription_tier&limit=1`,
      {
        headers: {
          Authorization: `Bearer ${serviceKey}`,
          apikey: serviceKey,
        },
      },
    );
    if (subRes.ok) {
      const rows = (await subRes.json()) as Array<{
        subscribed?: boolean;
        subscription_tier?: string | null;
      }>;
      if (rows[0]) {
        isPremium = rows[0].subscribed === true;
        subscriptionTier = rows[0].subscription_tier ?? null;
      }
    }
  }

  return {
    userId: user.id,
    email: user.email ?? null,
    isPremium,
    subscriptionTier,
  };
}

function enforceInMemoryRateLimit(
  ctx: AuthContext,
  feature: ConvexFeature,
): Response | null {
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

  const windowStart = Math.floor(Date.now() / limits.windowMs) * limits.windowMs;
  const key = `${ctx.userId}:${feature}:${windowStart}`;
  const entry = rateLimitCache.get(key) ?? { count: 0, windowStart };
  if (entry.count >= maxRequests) {
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
  rateLimitCache.set(key, { count: entry.count + 1, windowStart });
  return null;
}

/** Authenticate Convex HTTP request using Supabase user JWT. */
export async function secureConvexRequest(
  request: Request,
  feature: ConvexFeature,
): Promise<AuthContext | Response> {
  // Bootstrap mode: allow ai-chat without Supabase (e.g. when Supabase project is unavailable).
  if (feature === "ai-chat" && process.env.ALLOW_PUBLIC_AI_CHAT === "true") {
    const publicCtx: AuthContext = {
      userId: "public",
      email: null,
      isPremium: false,
      subscriptionTier: null,
    };
    const rateLimited = enforceInMemoryRateLimit(publicCtx, feature);
    if (rateLimited) return rateLimited;
    return publicCtx;
  }

  const authHeader = request.headers.get("Authorization");
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

  const auth = await verifySupabaseUser(token);
  if (auth instanceof Response) return auth;

  const rateLimited = enforceInMemoryRateLimit(auth, feature);
  if (rateLimited) return rateLimited;

  return auth;
}
