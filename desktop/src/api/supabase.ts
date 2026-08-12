import { createClient } from "@supabase/supabase-js";

// Same Supabase project as the Android/web app.
// Set VITE_SUPABASE_ANON_KEY in a .env file at desktop/ root, or hardcode below.
const SUPABASE_URL = "https://rkfveqzktfmgegtsoxlf.supabase.co";
const SUPABASE_ANON_KEY = import.meta.env.VITE_SUPABASE_ANON_KEY || "";

if (!SUPABASE_ANON_KEY) {
  console.warn(
    "[DeltaVoice] VITE_SUPABASE_ANON_KEY not set. " +
    "Create desktop/.env with VITE_SUPABASE_ANON_KEY=your_key"
  );
}

export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: {
    persistSession: true,
    storageKey: "deltavoice_desktop_session",
    autoRefreshToken: true,
    detectSessionInUrl: false,
  },
});

export { SUPABASE_URL, SUPABASE_ANON_KEY };
