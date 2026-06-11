import { serve } from "https://deno.land/std@0.190.0/http/server.ts";
import Stripe from "https://esm.sh/stripe@14.21.0";
import { corsHeaders, secureEdgeRequest } from "../_shared/security.ts";

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }

  const auth = await secureEdgeRequest(req, "customer-portal");
  if (auth instanceof Response) return auth;

  try {
    const user = auth.user;
    if (!user?.email) {
      return new Response(
        JSON.stringify({ error: "Email not available on account" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const stripeKey = Deno.env.get("STRIPE_SECRET_KEY");
    if (!stripeKey) throw new Error("STRIPE_SECRET_KEY is not set");

    const stripe = new Stripe(stripeKey, { apiVersion: "2023-10-16" });
    const customers = await stripe.customers.list({ email: user.email, limit: 1 });
    if (customers.data.length === 0) {
      return new Response(
        JSON.stringify({ error: "No billing account found. Please subscribe first." }),
        { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const customerId = customers.data[0].id;

    const allowedOrigins = (Deno.env.get("ALLOWED_ORIGINS") || "").split(",").map((s: string) => s.trim()).filter(Boolean);
    const reqOrigin = req.headers.get("origin") || "";
    const safeOrigin = allowedOrigins.includes(reqOrigin) ? reqOrigin : allowedOrigins[0] || reqOrigin;

    const portalSession = await stripe.billingPortal.sessions.create({
      customer: customerId,
      return_url: `${safeOrigin}/`,
    });

    return new Response(JSON.stringify({ url: portalSession.url }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 200,
    });
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    console.error("[customer-portal] ERROR:", errorMessage);
    return new Response(
      JSON.stringify({ error: "Portal access failed. Please try again." }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  }
});
