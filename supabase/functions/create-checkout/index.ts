import { serve } from "https://deno.land/std@0.190.0/http/server.ts";
import Stripe from "https://esm.sh/stripe@14.21.0";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";
import { corsHeaders, secureEdgeRequest } from "../_shared/security.ts";

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }

  const auth = await secureEdgeRequest(req, "create-checkout");
  if (auth instanceof Response) return auth;

  try {
    const user = auth.user;
    if (!user?.email) {
      return new Response(
        JSON.stringify({ error: "Email not available on account" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const { plan } = await req.json();
    if (!plan) {
      return new Response(
        JSON.stringify({ error: "Plan is required" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const PRICE_IDS: Record<string, string> = {
      basic: "price_1TZWekEjaicFR9zNUm8UuO4A",
      premium: "price_1TZWenEjaicFR9zNlo9zmw75",
      enterprise: "price_1TZWetEjaicFR9zNDjFQCXcR",
    };

    const priceId = PRICE_IDS[plan];
    if (!priceId) {
      return new Response(
        JSON.stringify({ error: "Invalid plan selected" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const stripeKey = Deno.env.get("STRIPE_SECRET_KEY");
    if (!stripeKey) throw new Error("STRIPE_SECRET_KEY is not set");

    const stripe = new Stripe(stripeKey, { apiVersion: "2023-10-16" });
    const customers = await stripe.customers.list({ email: user.email, limit: 1 });
    const customerId = customers.data.length > 0 ? customers.data[0].id : undefined;

    const allowedOrigins = (Deno.env.get("ALLOWED_ORIGINS") || "").split(",").map((s: string) => s.trim()).filter(Boolean);
    const reqOrigin = req.headers.get("origin") || "";
    const safeOrigin = allowedOrigins.includes(reqOrigin) ? reqOrigin : allowedOrigins[0] || reqOrigin;

    const session = await stripe.checkout.sessions.create({
      customer: customerId,
      customer_email: customerId ? undefined : user.email,
      line_items: [{ price: priceId, quantity: 1 }],
      mode: "subscription",
      success_url: `${safeOrigin}/subscription-success`,
      cancel_url: `${safeOrigin}/`,
    });

    return new Response(JSON.stringify({ url: session.url }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 200,
    });
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    console.error("[create-checkout] ERROR:", errorMessage);
    return new Response(
      JSON.stringify({ error: "Checkout creation failed. Please try again." }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  }
});
