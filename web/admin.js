/**
 * DeltaVoice Admin Dashboard – Secure Client Logic
 *
 * Security features:
 *  1. XSS Protection: All DOM insertions use textContent (never innerHTML with user data)
 *  2. SQL Injection: N/A client-side (backend uses parameterized Supabase queries)
 *  3. Brute-force login: Client-side lockout after 5 failed attempts (30s cooldown)
 *  4. CSRF: Supabase JWT bearer tokens (no cookies) – inherently CSRF-safe
 *  5. Session security: Token auto-refresh, secure storage, automatic expiry
 *  6. Input sanitization: Email & password validated before submission
 *  7. No sensitive data in URL: All auth via POST body + Authorization header
 *  8. Rate limiting: Backend enforces per-user rate limits
 *  9. Admin role check: Backend verifies profiles.role === 'admin'
 * 10. Content Security Policy: Set via meta tag in HTML
 */
(function () {
  "use strict";

  // ── Configuration ──────────────────────────────────
  const SUPABASE_URL = window.DeltaVoiceConfig?.SUPABASE_URL || "";
  const SUPABASE_ANON_KEY = window.DeltaVoiceConfig?.SUPABASE_ANON_KEY || "";

  const MAX_LOGIN_ATTEMPTS = 5;
  const LOCKOUT_DURATION_MS = 30_000; // 30 seconds
  const SESSION_KEY = "deltavoice_admin_session";
  const LOCKOUT_KEY = "deltavoice_admin_lockout";
  const REFRESH_MARGIN_MS = 60_000;
  const AUTO_REFRESH_INTERVAL_MS = 300_000; // 5 minutes

  // ── Utility: XSS-safe text setter ──────────────────
  function safeSetText(elementId, text) {
    const el = document.getElementById(elementId);
    if (el) el.textContent = String(text ?? "");
  }

  // ── Utility: HTML entity escaping (for any edge case) ─
  function escapeHtml(str) {
    const div = document.createElement("div");
    div.textContent = String(str);
    return div.innerHTML;
  }

  // ── Utility: Input validation ──────────────────────
  function isValidEmail(email) {
    if (!email || typeof email !== "string") return false;
    // Simple but effective email pattern – backend does final validation
    return /^[^\s@<>"']+@[^\s@<>"']+\.[^\s@<>"']{2,}$/.test(email.trim());
  }

  function sanitizeInput(input) {
    if (typeof input !== "string") return "";
    // Strip control characters and trim
    return input.replace(/[\x00-\x1f\x7f]/g, "").trim();
  }

  // ── Session Management ─────────────────────────────
  function loadSession() {
    try {
      const raw = sessionStorage.getItem(SESSION_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  function saveSession(session) {
    if (!session?.access_token) {
      sessionStorage.removeItem(SESSION_KEY);
      return;
    }
    // Store only what's needed – never store passwords
    const safe = {
      access_token: session.access_token,
      refresh_token: session.refresh_token,
      expires_at: session.expires_at,
      user: session.user
        ? {
            id: session.user.id,
            email: session.user.email,
            role: session.user.role,
          }
        : null,
    };
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(safe));
  }

  function clearSession() {
    sessionStorage.removeItem(SESSION_KEY);
  }

  function tokenExpiresAt(token) {
    try {
      const payload = JSON.parse(
        atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/"))
      );
      return (payload.exp || 0) * 1000;
    } catch {
      return 0;
    }
  }

  function isTokenExpired(token) {
    if (!token) return true;
    return Date.now() >= tokenExpiresAt(token) - REFRESH_MARGIN_MS;
  }

  // ── Brute-Force Lockout ────────────────────────────
  function getLockoutState() {
    try {
      const raw = sessionStorage.getItem(LOCKOUT_KEY);
      return raw ? JSON.parse(raw) : { attempts: 0, lockedUntil: 0 };
    } catch {
      return { attempts: 0, lockedUntil: 0 };
    }
  }

  function saveLockoutState(state) {
    sessionStorage.setItem(LOCKOUT_KEY, JSON.stringify(state));
  }

  function isLockedOut() {
    const state = getLockoutState();
    if (state.lockedUntil && Date.now() < state.lockedUntil) {
      return state.lockedUntil;
    }
    // Reset if lockout has expired
    if (state.lockedUntil && Date.now() >= state.lockedUntil) {
      saveLockoutState({ attempts: 0, lockedUntil: 0 });
    }
    return false;
  }

  function recordFailedAttempt() {
    const state = getLockoutState();
    state.attempts += 1;
    if (state.attempts >= MAX_LOGIN_ATTEMPTS) {
      state.lockedUntil = Date.now() + LOCKOUT_DURATION_MS;
    }
    saveLockoutState(state);
    return state;
  }

  function resetAttempts() {
    saveLockoutState({ attempts: 0, lockedUntil: 0 });
  }

  // ── Auth API ───────────────────────────────────────
  async function apiSignIn(email, password) {
    const res = await fetch(
      `${SUPABASE_URL}/auth/v1/token?grant_type=password`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          apikey: SUPABASE_ANON_KEY,
        },
        body: JSON.stringify({ email, password }),
      }
    );
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.error_description || data.msg || "Sign in failed");
    }
    return data;
  }

  async function apiRefreshToken(refreshToken) {
    const res = await fetch(
      `${SUPABASE_URL}/auth/v1/token?grant_type=refresh_token`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          apikey: SUPABASE_ANON_KEY,
        },
        body: JSON.stringify({ refresh_token: refreshToken }),
      }
    );
    if (!res.ok) return null;
    return await res.json();
  }

  async function getValidToken() {
    const session = loadSession();
    if (!session?.access_token) return null;

    if (!isTokenExpired(session.access_token)) {
      return session.access_token;
    }

    if (session.refresh_token) {
      const refreshed = await apiRefreshToken(session.refresh_token);
      if (refreshed?.access_token) {
        saveSession(refreshed);
        return refreshed.access_token;
      }
    }

    clearSession();
    return null;
  }

  async function fetchDashboardData(token) {
    const res = await fetch(
      `${SUPABASE_URL}/functions/v1/admin-dashboard`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
          apikey: SUPABASE_ANON_KEY,
        },
        body: JSON.stringify({}),
      }
    );
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.error || `HTTP ${res.status}`);
    }
    return data;
  }

  // ── UI Controllers ─────────────────────────────────
  const $ = (id) => document.getElementById(id);

  let lockoutInterval = null;
  let autoRefreshTimer = null;

  function showLogin() {
    const loginPage = $("loginPage");
    const dashboardPage = $("dashboardPage");
    if (loginPage) loginPage.style.display = "";
    if (dashboardPage) dashboardPage.classList.remove("active");
    if (autoRefreshTimer) clearInterval(autoRefreshTimer);
  }

  function showDashboard() {
    const loginPage = $("loginPage");
    const dashboardPage = $("dashboardPage");
    if (loginPage) loginPage.style.display = "none";
    if (dashboardPage) dashboardPage.classList.add("active");
  }

  function showLoginError(message) {
    const errorEl = $("loginError");
    const errorText = $("loginErrorText");
    if (errorEl && errorText) {
      // Use textContent – never innerHTML with user-derived data
      errorText.textContent = String(message);
      errorEl.classList.add("visible");
    }
  }

  function hideLoginError() {
    const errorEl = $("loginError");
    if (errorEl) errorEl.classList.remove("visible");
  }

  function updateAttemptsDisplay() {
    const state = getLockoutState();
    const el = $("loginAttempts");
    if (!el) return;
    if (state.attempts > 0 && state.attempts < MAX_LOGIN_ATTEMPTS) {
      el.textContent = `${MAX_LOGIN_ATTEMPTS - state.attempts} attempts remaining`;
      el.classList.add("visible");
    } else {
      el.classList.remove("visible");
    }
  }

  function startLockoutCountdown(lockedUntil) {
    const banner = $("lockoutBanner");
    const timer = $("lockoutTimer");
    const loginBtn = $("loginBtn");

    if (!banner || !timer) return;

    banner.classList.add("visible");
    if (loginBtn) loginBtn.disabled = true;

    if (lockoutInterval) clearInterval(lockoutInterval);

    lockoutInterval = setInterval(() => {
      const remaining = Math.max(0, lockedUntil - Date.now());
      const seconds = Math.ceil(remaining / 1000);
      timer.textContent = `${seconds}s`;

      if (remaining <= 0) {
        clearInterval(lockoutInterval);
        lockoutInterval = null;
        banner.classList.remove("visible");
        if (loginBtn) loginBtn.disabled = false;
        resetAttempts();
        updateAttemptsDisplay();
      }
    }, 250);
  }

  function setAdminInfo(email) {
    safeSetText("adminEmail", email || "admin");
    const avatarEl = $("adminAvatar");
    if (avatarEl && email) {
      avatarEl.textContent = email.charAt(0).toUpperCase();
    }
  }

  function formatNumber(n) {
    if (typeof n !== "number" || isNaN(n)) return "—";
    return n.toLocaleString("en-US");
  }

  function updateStats(data) {
    const userCount = $("statUsers");
    const subCount = $("statSubscribers");
    const apiCount = $("statApiUsage");

    if (userCount) {
      userCount.textContent = formatNumber(data.user_count);
      userCount.classList.remove("loading");
    }
    if (subCount) {
      subCount.textContent = formatNumber(data.subscriber_count);
      subCount.classList.remove("loading");
    }
    if (apiCount) {
      apiCount.textContent = formatNumber(data.api_usage_records);
      apiCount.classList.remove("loading");
    }

    // Update refresh timestamp
    const refreshEl = $("lastRefresh");
    if (refreshEl) {
      refreshEl.textContent = `Last updated: ${new Date().toLocaleTimeString()}`;
    }
  }

  function setStatsLoading() {
    ["statUsers", "statSubscribers", "statApiUsage"].forEach((id) => {
      const el = $(id);
      if (el) {
        el.textContent = "";
        el.classList.add("loading");
      }
    });
  }

  async function loadDashboard() {
    const token = await getValidToken();
    if (!token) {
      clearSession();
      showLogin();
      return;
    }

    setStatsLoading();

    try {
      const data = await fetchDashboardData(token);
      updateStats(data);
    } catch (err) {
      const msg = err.message || "Failed to load data";
      if (
        msg.includes("Admin") ||
        msg.includes("AUTH") ||
        msg.includes("401") ||
        msg.includes("403")
      ) {
        clearSession();
        showLogin();
        showLoginError("Access denied. Admin privileges required.");
        return;
      }
      // Show error in stats
      safeSetText("statUsers", "—");
      safeSetText("statSubscribers", "—");
      safeSetText("statApiUsage", "—");
      ["statUsers", "statSubscribers", "statApiUsage"].forEach((id) => {
        const el = $(id);
        if (el) el.classList.remove("loading");
      });
    }
  }

  // ── Login Handler ──────────────────────────────────
  async function handleLogin(e) {
    e.preventDefault();
    hideLoginError();

    // Check lockout
    const lockedUntil = isLockedOut();
    if (lockedUntil) {
      startLockoutCountdown(lockedUntil);
      return;
    }

    const emailInput = $("loginEmail");
    const passwordInput = $("loginPassword");
    const loginBtn = $("loginBtn");

    if (!emailInput || !passwordInput || !loginBtn) return;

    const email = sanitizeInput(emailInput.value);
    const password = passwordInput.value; // Don't sanitize passwords – they can have special chars

    // Validate email format (prevents basic injection patterns)
    if (!isValidEmail(email)) {
      showLoginError("Please enter a valid email address.");
      return;
    }

    if (!password || password.length < 6) {
      showLoginError("Password must be at least 6 characters.");
      return;
    }

    // Prevent extremely long inputs (DoS vector)
    if (email.length > 254 || password.length > 128) {
      showLoginError("Input too long.");
      return;
    }

    loginBtn.disabled = true;
    loginBtn.classList.add("loading");

    try {
      const session = await apiSignIn(email, password);
      saveSession(session);

      // Verify admin access by trying to fetch dashboard data
      const token = session.access_token;
      const data = await fetchDashboardData(token);

      // Success – reset lockout and show dashboard
      resetAttempts();
      updateAttemptsDisplay();
      setAdminInfo(email);
      showDashboard();
      updateStats(data);

      // Clear password from memory
      passwordInput.value = "";

      // Start auto-refresh
      autoRefreshTimer = setInterval(loadDashboard, AUTO_REFRESH_INTERVAL_MS);
    } catch (err) {
      // Clear session if login succeeded but admin check failed
      clearSession();
      passwordInput.value = "";

      const state = recordFailedAttempt();
      updateAttemptsDisplay();

      if (state.lockedUntil) {
        startLockoutCountdown(state.lockedUntil);
        showLoginError(
          "Too many failed attempts. Account temporarily locked."
        );
      } else {
        const msg = err.message || "Sign in failed";
        if (msg.includes("Admin") || msg.includes("403")) {
          showLoginError("Access denied. This account does not have admin privileges.");
        } else if (msg.includes("Invalid") || msg.includes("credentials")) {
          showLoginError("Invalid email or password.");
        } else {
          showLoginError("Sign in failed. Please check your credentials.");
        }
      }
    } finally {
      loginBtn.disabled = false;
      loginBtn.classList.remove("loading");
      // Re-check lockout state for button
      if (isLockedOut()) {
        loginBtn.disabled = true;
      }
    }
  }

  // ── Logout Handler ─────────────────────────────────
  function handleLogout() {
    clearSession();
    if (autoRefreshTimer) clearInterval(autoRefreshTimer);
    showLogin();
    // Reset stats display
    setStatsLoading();
  }

  // ── Refresh Handler ────────────────────────────────
  async function handleRefresh() {
    const btn = $("refreshBtn");
    if (btn) {
      btn.classList.add("spinning");
      btn.disabled = true;
    }

    await loadDashboard();

    setTimeout(() => {
      if (btn) {
        btn.classList.remove("spinning");
        btn.disabled = false;
      }
    }, 600);
  }

  // ── Init ───────────────────────────────────────────
  function init() {
    // Wire up login form
    const loginForm = $("loginForm");
    if (loginForm) {
      loginForm.addEventListener("submit", handleLogin);
    }

    // Wire up logout
    const logoutBtn = $("logoutBtn");
    if (logoutBtn) {
      logoutBtn.addEventListener("click", handleLogout);
    }

    // Wire up refresh
    const refreshBtn = $("refreshBtn");
    if (refreshBtn) {
      refreshBtn.addEventListener("click", handleRefresh);
    }

    // Check for existing lockout
    const lockedUntil = isLockedOut();
    if (lockedUntil) {
      startLockoutCountdown(lockedUntil);
    }
    updateAttemptsDisplay();

    // Check for existing valid session
    (async () => {
      const token = await getValidToken();
      if (token) {
        try {
          const data = await fetchDashboardData(token);
          const session = loadSession();
          setAdminInfo(session?.user?.email);
          showDashboard();
          updateStats(data);
          autoRefreshTimer = setInterval(
            loadDashboard,
            AUTO_REFRESH_INTERVAL_MS
          );
        } catch {
          clearSession();
          showLogin();
        }
      } else {
        showLogin();
      }
    })();
  }

  // Start when DOM is ready
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
