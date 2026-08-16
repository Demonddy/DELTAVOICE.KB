import { useState } from "react";
import { useAuth } from "./AuthContext";
import { SUPABASE_ANON_KEY } from "../api/supabase";
import { Mic } from "lucide-react";
import { usePlatformInfo } from "../hooks/usePlatformInfo";
import { HotkeyLabel } from "../components/HotkeyLabel";

export default function LoginPage() {
  const platform = usePlatformInfo();
  const { signIn, signUp } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isSignUp, setIsSignUp] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setSuccess("");
    setLoading(true);
    try {
      if (isSignUp) {
        const result = await signUp(email, password);
        if (result === "confirm_email") {
          setSuccess(
            "Account created! Check your email inbox and click the confirmation link, then sign in here."
          );
        }
      } else {
        await signIn(email, password);
      }
    } catch (err: any) {
      const message = err.message || "Authentication failed";
      if (message.toLowerCase().includes("invalid login credentials")) {
        setError(
          "Invalid email or password. If you just signed up, confirm your email first."
        );
      } else if (message.toLowerCase().includes("email not confirmed")) {
        setError("Please confirm your email before signing in.");
      } else {
        setError(message);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        flex: 1,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: 32,
        background: "var(--bg-primary)",
        overflow: "auto",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 8 }}>
        <div
          style={{
            width: 48,
            height: 48,
            borderRadius: "50%",
            background: "linear-gradient(135deg, var(--accent), var(--accent-hover))",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <Mic size={24} color="#fff" />
        </div>
        <h1 style={{ fontSize: 28, fontWeight: 700, color: "var(--text-primary)" }}>
          DeltaVoice
        </h1>
      </div>

      <p style={{ color: "var(--text-secondary)", fontSize: 14, marginBottom: 32 }}>
        System-wide voice dictation & AI tools
      </p>

      <form
        onSubmit={handleSubmit}
        className="glass-panel"
        style={{
          width: "100%",
          maxWidth: 340,
          padding: 24,
          display: "flex",
          flexDirection: "column",
          gap: 14,
        }}
      >
        <h2
          style={{
            fontSize: 18,
            fontWeight: 600,
            color: "var(--text-primary)",
            textAlign: "center",
          }}
        >
          {isSignUp ? "Create Account" : "Sign In"}
        </h2>

        {error && (
          <div
            style={{
              padding: "8px 12px",
              borderRadius: 8,
              background: "rgba(239, 68, 68, 0.15)",
              color: "var(--danger)",
              fontSize: 13,
            }}
          >
            {error}
          </div>
        )}

        {success && (
          <div
            style={{
              padding: "8px 12px",
              borderRadius: 8,
              background: "rgba(16, 185, 129, 0.15)",
              color: "var(--success)",
              fontSize: 13,
              lineHeight: 1.4,
            }}
          >
            {success}
          </div>
        )}

        {!SUPABASE_ANON_KEY && (
          <div
            style={{
              padding: "8px 12px",
              borderRadius: 8,
              background: "rgba(245, 158, 11, 0.15)",
              color: "var(--warning)",
              fontSize: 13,
              lineHeight: 1.4,
            }}
          >
            Supabase is not configured. Restart the app after adding{" "}
            <code>VITE_SUPABASE_ANON_KEY</code> to <code>desktop/.env</code>.
          </div>
        )}

        <input
          type="email"
          placeholder="Email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          style={{
            height: 44,
            borderRadius: 10,
            border: "1px solid var(--border-glass)",
            background: "rgba(26, 31, 46, 0.6)",
            color: "var(--text-primary)",
            padding: "0 14px",
            fontSize: 14,
            outline: "none",
          }}
        />

        <input
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          minLength={6}
          style={{
            height: 44,
            borderRadius: 10,
            border: "1px solid var(--border-glass)",
            background: "rgba(26, 31, 46, 0.6)",
            color: "var(--text-primary)",
            padding: "0 14px",
            fontSize: 14,
            outline: "none",
          }}
        />

        <button
          type="submit"
          className="btn-primary"
          disabled={loading}
          style={{ marginTop: 4 }}
        >
          {loading ? "..." : isSignUp ? "Sign Up" : "Sign In"}
        </button>

        <button
          type="button"
          onClick={() => {
            setIsSignUp(!isSignUp);
            setError("");
            setSuccess("");
          }}
          style={{
            background: "none",
            border: "none",
            color: "var(--accent)",
            fontSize: 13,
            cursor: "pointer",
            textAlign: "center",
          }}
        >
          {isSignUp
            ? "Already have an account? Sign In"
            : "Don't have an account? Sign Up"}
        </button>
      </form>

      <p
        style={{
          color: "var(--text-muted)",
          fontSize: 11,
          marginTop: 24,
          textAlign: "center",
        }}
      >
        Press <HotkeyLabel label={platform.voiceHotkey} /> anywhere to start voice recording
      </p>
    </div>
  );
}
