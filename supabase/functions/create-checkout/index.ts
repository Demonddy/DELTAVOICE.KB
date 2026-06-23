import { serve } from "https://deno.land/std@0.190.0/http/server.ts";
import Stripe from "https://esm.sh/stripe@14.21.0";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";
import {
  corsHeadersForRequest,
  handleServerError,
  jsonResponse,
  logger,
  secureEdgeRequest,
  validateRedirectOrigin,
} from "../_shared/security.ts";

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeadersForRequest(req) });
  }

  const auth = await secureEdgeRequest(req, "create-checkout");
  if (auth instanceof Response) return auth;

  try {
    const user = auth.user;
    if (!user?.email) {
      return jsonResponse({ error: "Email not available on account" }, 400, req);
    }

    const { plan } = await req.json();
    if (!plan) {
      return jsonResponse({ error: "Plan is required" }, 400, req);
    }

    const PRICE_IDS: Record<string, string> = {
      basic: "price_1TZWekEjaicFR9zNUm8UuO4A",
      premium: "price_1TZWenEjaicFR9zNlo9zmw75",
      enterprise: "price_1TZWetEjaicFR9zNDjFQCXcR",
    };

    const priceId = PRICE_IDS[plan];
    if (!priceId) {
      return jsonResponse({ error: "Invalid plan selected" }, 400, req);
    }

    const stripeKey = Deno.env.get("STRIPE_SECRET_KEY");
    if (!stripeKey) throw new Error("STRIPE_SECRET_KEY is not set");

    const safeOrigin = validateRedirectOrigin(req, { allowNativeClient: true });
    if (safeOrigin instanceof Response) return safeOrigin;

    const stripe = new Stripe(stripeKey, { apiVersion: "2023-10-16" });
    const customers = await stripe.customers.list({ email: user.email, limit: 1 });
    const customerId = customers.data.length > 0 ? customers.data[0].id : undefined;

    const session = await stripe.checkout.sessions.create({
      customer: customerId,
      customer_email: customerId ? undefined : user.email,
      line_items: [{ price: priceId, quantity: 1 }],
      mode: "subscription",
      success_url: `${safeOrigin}/subscription-success`,
      cancel_url: `${safeOrigin}/`,
    });

    return jsonResponse({ url: session.url }, 200, req);
  } catch (error) {
    return handleServerError(
      "create-checkout",
      error,
      req,
      "Checkout creation failed. Please try again.",
    );
  }
});
