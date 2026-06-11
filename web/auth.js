/**
 * Supabase Auth helper for web clients. Requires sign-in before API calls.
 */
(function () {
  const SUPABASE_URL = window.DeltaVoiceConfig?.SUPABASE_URL || "";
  const SUPABASE_ANON_KEY = window.DeltaVoiceConfig?.SUPABASE_ANON_KEY || "";

  if (!SUPABASE_URL || !SUPABASE_ANON_KEY) {
    console.error("[DeltaVoiceAuth] SUPABASE_URL and SUPABASE_ANON_KEY must be set in window.DeltaVoiceConfig");
  }

  const SESSION_KEY = "deltavoice_auth_session";
  const REFRESH_MARGIN_MS = 60_000;

  function loadSession() {
    try {
      const raw = localStorage.getItem(SESSION_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  function saveSession(session) {
    if (!session?.access_token) {
      localStorage.removeItem(SESSION_KEY);
      return;
    }
    localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  }

  function tokenExpiresAt(token) {
    try {
      const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
      return (payload.exp || 0) * 1000;
    } catch {
      return 0;
    }
  }

  function isTokenExpired(token) {
    if (!token) return true;
    return Date.now() >= tokenExpiresAt(token) - REFRESH_MARGIN_MS;
  }

  async function refreshToken() {
    const session = loadSession();
    if (!session?.refresh_token) return null;
    try {
      const res = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=refresh_token`, {
        method: "POST",
        headers: { "Content-Type": "application/json", apikey: SUPABASE_ANON_KEY },
        body: JSON.stringify({ refresh_token: session.refresh_token }),
      });
      if (!res.ok) { saveSession(null); return null; }
      const data = await res.json();
      saveSession(data);
      return data.access_token;
    } catch {
      return null;
    }
  }

  async function signIn(email, password) {
    const res = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        apikey: SUPABASE_ANON_KEY,
      },
      body: JSON.stringify({ email, password }),
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.error_description || data.msg || "Sign in failed");
    }
    saveSession(data);
    return data;
  }

  async function signUp(email, password) {
    const res = await fetch(`${SUPABASE_URL}/auth/v1/signup`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        apikey: SUPABASE_ANON_KEY,
      },
      body: JSON.stringify({ email, password }),
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.error_description || data.msg || "Sign up failed");
    }
    if (data.access_token) saveSession(data);
    return data;
  }

  function signOut() {
    saveSession(null);
  }

  async function getAccessToken() {
    const session = loadSession();
    const token = session?.access_token || null;
    if (token && !isTokenExpired(token)) return token;
    return await refreshToken();
  }

  function isLoggedIn() {
    const session = loadSession();
    return !!(session?.access_token);
  }

  async function authHeaders() {
    const token = await getAccessToken();
    if (!token) {
      throw new Error("Please sign in to continue.");
    }
    return {
      Authorization: `Bearer ${token}`,
      apikey: SUPABASE_ANON_KEY,
    };
  }

  async function ensureSignedIn() {
    const token = await getAccessToken();
    if (!token) {
      const email = prompt("Email");
      const password = prompt("Password");
      if (!email || !password) throw new Error("Sign in required.");
      await signIn(email, password);
    }
  }

  window.DeltaVoiceAuth = {
    signIn,
    signUp,
    signOut,
    getAccessToken,
    isLoggedIn,
    authHeaders,
    ensureSignedIn,
    refreshToken,
  };
})();
