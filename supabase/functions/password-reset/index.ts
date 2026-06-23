import { serve } from "https://deno.land/std@0.190.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";
import {
  corsHeadersForRequest,
  enforcePasswordResetRateLimit,
  handleServerError,
  jsonResponse,
  logger,
  rejectDisallowedOrigin,
  validateRedirectOrigin,
} from "../_shared/security.ts";

serve(async (req) => {
  const originDenied = rejectDisallowedOrigin(req);
  if (originDenied) return originDenied;

  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeadersForRequest(req) });
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "Method not allowed." }, 405, req);
  }

  try {
    const body = await req.json();
    const email = typeof body.email === "string" ? body.email.trim() : "";

    if (!email) {
      return jsonResponse(
        { error: "Email is required.", code: "INVALID_INPUT" },
        400,
        req,
      );
    }

    const rateLimited = await enforcePasswordResetRateLimit(email, req);
    if (rateLimited) return rateLimited;

    const redirectUrl =
      typeof body.redirectUrl === "string" ? body.redirectUrl.trim() : "";
    const safeOrigin = validateRedirectOrigin(req, {
      redirectUrl: redirectUrl || undefined,
    });
    if (safeOrigin instanceof Response) return safeOrigin;


    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    if (!supabaseUrl || !serviceKey) {
      logger.error("password-reset", "Supabase credentials not configured");
      return jsonResponse(
        { error: "Server misconfigured.", code: "SERVER_MISCONFIGURED" },
        500,
        req,
      );
    }

    const supabase = createClient(supabaseUrl, serviceKey, {
      auth: { persistSession: false },
    });

    const { error } = await supabase.auth.resetPasswordForEmail(email, {
      redirectTo: `${safeOrigin}/reset-password`,
    });

    if (error) {
      logger.error("password-reset", "Supabase reset failed", error);
      return jsonResponse(
        { error: "Unable to send reset email. Please try again later." },
        500,
        req,
      );
    }

    // Generic success — do not reveal whether the email exists
    return jsonResponse(
      {
        message:
          "If an account exists for that email, a password reset link has been sent.",
      },
      200,
      req,
    );
  } catch (error) {
    return handleServerError(
      "password-reset",
      error,
      req,
      "Unable to process password reset. Please try again later.",
    );
  }
});
