import {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
  type ReactNode,
} from "react";
import { supabase } from "../api/supabase";
import type { Session, User } from "@supabase/supabase-js";

interface AuthState {
  session: Session | null;
  user: User | null;
  isPremium: boolean;
  loading: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (email: string, password: string) => Promise<"signed_in" | "confirm_email">;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [isPremium, setIsPremium] = useState(false);
  const [loading, setLoading] = useState(true);

  const checkPremium = useCallback(async (userId: string) => {
    try {
      const { data } = await supabase
        .from("subscribers")
        .select("subscribed")
        .eq("user_id", userId)
        .limit(1)
        .single();
      setIsPremium(data?.subscribed === true);
    } catch {
      setIsPremium(false);
    }
  }, []);

  useEffect(() => {
    supabase.auth.getSession().then(({ data: { session } }) => {
      setSession(session);
      setUser(session?.user ?? null);
      if (session?.user) checkPremium(session.user.id);
      setLoading(false);
    });

    const { data: { subscription } } = supabase.auth.onAuthStateChange(
      (_event, session) => {
        setSession(session);
        setUser(session?.user ?? null);
        if (session?.user) checkPremium(session.user.id);
        else setIsPremium(false);
      }
    );

    return () => subscription.unsubscribe();
  }, [checkPremium]);

  const signIn = async (email: string, password: string) => {
    const { error } = await supabase.auth.signInWithPassword({
      email: email.trim(),
      password,
    });
    if (error) throw error;
  };

  const signUp = async (email: string, password: string) => {
    const { data, error } = await supabase.auth.signUp({
      email: email.trim(),
      password,
    });
    if (error) throw error;

    if (data.session) {
      setSession(data.session);
      setUser(data.session.user);
      if (data.session.user) await checkPremium(data.session.user.id);
      return "signed_in";
    }

    // Supabase may require email confirmation before creating a session.
    if (data.user && !data.user.confirmed_at) {
      return "confirm_email";
    }

    // User already exists (Supabase returns empty identities for security).
    if (data.user?.identities?.length === 0) {
      throw new Error(
        "An account with this email already exists. Try signing in instead."
      );
    }

    return "confirm_email";
  };

  const signOut = async () => {
    await supabase.auth.signOut();
    setIsPremium(false);
  };

  return (
    <AuthContext.Provider
      value={{ session, user, isPremium, loading, signIn, signUp, signOut }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
