import { serve } from "https://deno.land/std@0.190.0/http/server.ts";
import Stripe from "https://esm.sh/stripe@14.21.0";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";
import { logger } from "../_shared/security.ts";

const RELEVANT_EVENTS = new Set([
  "customer.subscription.updated",
  "customer.subscription.deleted",
  "invoice.payment_failed",
  "checkout.session.completed",
]);

function supabaseAdmin() {
  return createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
    { auth: { persistSession: false } },
  );
}

const PRICE_TO_TIER: Record<string, string> = {
  "price_1TZWekEjaicFR9zNUm8UuO4A": "Basic",
  "price_1TZWenEjaicFR9zNlo9zmw75": "Premium",
  "price_1TZWetEjaicFR9zNDjFQCXcR": "Enterprise",
};

function resolveTier(priceId: string): string {
  return PRICE_TO_TIER[priceId] || "Unknown";
}

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  const stripeKey = Deno.env.get("STRIPE_SECRET_KEY");
  const webhookSecret = Deno.env.get("STRIPE_WEBHOOK_SECRET");
  if (!stripeKey || !webhookSecret) {
    logger.error("stripe-webhook", "Missing STRIPE_SECRET_KEY or STRIPE_WEBHOOK_SECRET");
    return new Response("Server misconfigured", { status: 500 });
  }

  const signature = req.headers.get("stripe-signature");
  if (!signature) {
    return new Response("Bad request", { status: 400 });
  }

  const body = await req.text();
  const stripe = new Stripe(stripeKey, { apiVersion: "2023-10-16" });

  let event: Stripe.Event;
  try {
    event = stripe.webhooks.constructEvent(body, signature, webhookSecret);
  } catch (err) {
    logger.error("stripe-webhook", "Signature verification failed", err);
    return new Response("Invalid signature", { status: 400 });
  }

  if (!RELEVANT_EVENTS.has(event.type)) {
    return new Response(JSON.stringify({ received: true }), { status: 200 });
  }

  const supabase = supabaseAdmin();

  try {
    if (event.type === "customer.subscription.updated" || event.type === "customer.subscription.deleted") {
      const subscription = event.data.object as Stripe.Subscription;
      const customerId = typeof subscription.customer === "string"
        ? subscription.customer
        : subscription.customer.id;

      const customer = await stripe.customers.retrieve(customerId) as Stripe.Customer;
      if (!customer || customer.deleted) {
        logger.warn("stripe-webhook", "Customer deleted or not found");
        return new Response(JSON.stringify({ received: true }), { status: 200 });
      }

      const email = customer.email;
      if (!email) {
        logger.warn("stripe-webhook", "Customer has no email");
        return new Response(JSON.stringify({ received: true }), { status: 200 });
      }

      const isActive = subscription.status === "active" || subscription.status === "trialing";
      let subscriptionTier: string | null = null;
      let subscriptionEnd: string | null = null;

      if (isActive && subscription.items.data.length > 0) {
        const priceId = subscription.items.data[0].price.id;
        subscriptionTier = resolveTier(priceId);
        subscriptionEnd = new Date(subscription.current_period_end * 1000).toISOString();
      }

      const { error } = await supabase.from("subscribers").upsert(
        {
          email,
          stripe_customer_id: customerId,
          subscribed: isActive,
          subscription_tier: isActive ? subscriptionTier : null,
          subscription_end: isActive ? subscriptionEnd : null,
          updated_at: new Date().toISOString(),
        },
        { onConflict: "email" },
      );

      if (error) {
        logger.error("stripe-webhook", "DB upsert failed", error);
      }
    }

    if (event.type === "checkout.session.completed") {
      const session = event.data.object as Stripe.Checkout.Session;
      if (session.mode === "subscription" && session.subscription && session.customer_email) {
        const subId = typeof session.subscription === "string"
          ? session.subscription
          : session.subscription.id;
        const subscription = await stripe.subscriptions.retrieve(subId);
        const customerId = typeof subscription.customer === "string"
          ? subscription.customer
          : subscription.customer.id;
        const isActive = subscription.status === "active" || subscription.status === "trialing";
        let subscriptionTier: string | null = null;
        let subscriptionEnd: string | null = null;

        if (isActive && subscription.items.data.length > 0) {
          subscriptionTier = resolveTier(subscription.items.data[0].price.id);
          subscriptionEnd = new Date(subscription.current_period_end * 1000).toISOString();
        }

        const { error } = await supabase.from("subscribers").upsert(
          {
            email: session.customer_email,
            stripe_customer_id: customerId,
            subscribed: isActive,
            subscription_tier: isActive ? subscriptionTier : null,
            subscription_end: isActive ? subscriptionEnd : null,
            updated_at: new Date().toISOString(),
          },
          { onConflict: "email" },
        );

        if (error) {
          logger.error("stripe-webhook", "DB upsert (checkout) failed", error);
        }
      }
    }

    if (event.type === "invoice.payment_failed") {
      const invoice = event.data.object as Stripe.Invoice;
      const customerId = typeof invoice.customer === "string"
        ? invoice.customer
        : invoice.customer?.id;

      if (customerId) {
        logger.warn("stripe-webhook", "Payment failed for customer");
      }
    }
  } catch (err) {
    logger.error("stripe-webhook", "Processing error", err);
    return new Response("Webhook processing failed", { status: 500 });
  }

  return new Response(JSON.stringify({ received: true }), { status: 200 });
});
