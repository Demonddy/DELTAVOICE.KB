import { serve } from "https://deno.land/std@0.190.0/http/server.ts";
import Stripe from "https://esm.sh/stripe@14.21.0";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";
import {
  corsHeadersForRequest,
  handleServerError,
  jsonResponse,
  logger,
  secureEdgeRequest,
} from "../_shared/security.ts";

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeadersForRequest(req) });
  }

  const auth = await secureEdgeRequest(req, "check-subscription");
  if (auth instanceof Response) return auth;

  const supabaseClient = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
    { auth: { persistSession: false } },
  );

  try {
    const user = auth.user;
    if (!user?.email) {
      return jsonResponse({ error: "Email not available on account" }, 400, req);
    }

    const stripeKey = Deno.env.get("STRIPE_SECRET_KEY");
    if (!stripeKey) throw new Error("STRIPE_SECRET_KEY is not set");

    const stripe = new Stripe(stripeKey, { apiVersion: "2023-10-16" });
    const customers = await stripe.customers.list({ email: user.email, limit: 1 });

    if (customers.data.length === 0) {
      await supabaseClient.from("subscribers").upsert(
        {
          email: user.email,
          user_id: user.id,
          stripe_customer_id: null,
          subscribed: false,
          subscription_tier: null,
          subscription_end: null,
          updated_at: new Date().toISOString(),
        },
        { onConflict: "email" },
      );
      return jsonResponse({ subscribed: false }, 200, req);
    }

    const customerId = customers.data[0].id;
    const subscriptions = await stripe.subscriptions.list({
      customer: customerId,
      status: "active",
      limit: 1,
    });

    const hasActiveSub = subscriptions.data.length > 0;
    let subscriptionTier: string | null = null;
    let subscriptionEnd: string | null = null;

    const PRICE_TO_TIER: Record<string, string> = {
      "price_1TZWekEjaicFR9zNUm8UuO4A": "Basic",
      "price_1TZWenEjaicFR9zNlo9zmw75": "Premium",
      "price_1TZWetEjaicFR9zNDjFQCXcR": "Enterprise",
    };

    if (hasActiveSub) {
      const subscription = subscriptions.data[0];
      subscriptionEnd = new Date(subscription.current_period_end * 1000).toISOString();
      const priceId = subscription.items.data[0].price.id;
      subscriptionTier = PRICE_TO_TIER[priceId] || "Unknown";
    }

    await supabaseClient.from("subscribers").upsert(
      {
        email: user.email,
        user_id: user.id,
        stripe_customer_id: customerId,
        subscribed: hasActiveSub,
        subscription_tier: subscriptionTier,
        subscription_end: subscriptionEnd,
        updated_at: new Date().toISOString(),
      },
      { onConflict: "email" },
    );

    return jsonResponse(
      {
        subscribed: hasActiveSub,
        subscription_tier: subscriptionTier,
        subscription_end: subscriptionEnd,
      },
      200,
      req,
    );
  } catch (error) {
    return handleServerError(
      "check-subscription",
      error,
      req,
      "Subscription check failed. Please try again.",
    );
  }
});
