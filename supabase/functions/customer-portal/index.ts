import { serve } from "https://deno.land/std@0.190.0/http/server.ts";
import Stripe from "https://esm.sh/stripe@14.21.0";
import {
  corsHeadersForRequest,
  handleServerError,
  jsonResponse,
  secureEdgeRequest,
  validateRedirectOrigin,
} from "../_shared/security.ts";

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeadersForRequest(req) });
  }

  const auth = await secureEdgeRequest(req, "customer-portal");
  if (auth instanceof Response) return auth;

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
      return jsonResponse(
        { error: "No billing account found. Please subscribe first." },
        404,
        req,
      );
    }

    const safeOrigin = validateRedirectOrigin(req, { allowNativeClient: true });
    if (safeOrigin instanceof Response) return safeOrigin;

    const portalSession = await stripe.billingPortal.sessions.create({
      customer: customers.data[0].id,
      return_url: `${safeOrigin}/`,
    });

    return jsonResponse({ url: portalSession.url }, 200, req);
  } catch (error) {
    return handleServerError(
      "customer-portal",
      error,
      req,
      "Portal access failed. Please try again.",
    );
  }
});
