import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { corsHeadersForRequest, jsonResponse } from "../_shared/security.ts";

/**
 * Disabled for production: never expose third-party API keys to clients.
 */
serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: { ...corsHeadersForRequest(req), "Content-Type": "application/json" } });
  }

  return new Response(
    JSON.stringify({
      error: "This endpoint is disabled for security reasons.",
      code: "ENDPOINT_DISABLED",
    }),
    {
      status: 403,
      headers: { ...corsHeadersForRequest(req), "Content-Type": "application/json" },
    },
  );
});
