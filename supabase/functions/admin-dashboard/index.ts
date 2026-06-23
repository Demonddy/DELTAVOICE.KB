import { serve } from "https://deno.land/std@0.190.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";
import {
  corsHeadersForRequest,
  handleServerError,
  jsonResponse,
  secureEdgeRequest,
} from "../_shared/security.ts";

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeadersForRequest(req) });
  }

  const auth = await secureEdgeRequest(req, "admin-dashboard");
  if (auth instanceof Response) return auth;

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
      { auth: { persistSession: false } },
    );

    const [profiles, subscribers, usage] = await Promise.all([
      supabase.from("profiles").select("id", { count: "exact", head: true }),
      supabase.from("subscribers").select("id", { count: "exact", head: true }),
      supabase.from("api_usage").select("id", { count: "exact", head: true }),
    ]);

    return jsonResponse(
      {
        user_count: profiles.count ?? 0,
        subscriber_count: subscribers.count ?? 0,
        api_usage_records: usage.count ?? 0,
      },
      200,
      req,
    );
  } catch (error) {
    return handleServerError(
      "admin-dashboard",
      error,
      req,
      "Unable to load dashboard data.",
    );
  }
});
