import { useState, useEffect, useCallback } from "react";
import { listen } from "@tauri-apps/api/event";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import LoginPage from "./auth/LoginPage";
import VoiceStep2 from "./components/VoiceStep2";
import RecordingBar from "./components/RecordingBar";
import Toolbar from "./components/Toolbar";
import AiChat from "./components/AiChat";
import AiWritingTools from "./components/AiWritingTools";
import VideoPanel from "./components/VideoPanel";
import MoreOptions from "./components/MoreOptions";
import ClipboardPanel from "./components/ClipboardPanel";
import TitleBar from "./components/TitleBar";
import {
  enterRecordingWindowMode,
  exitRecordingWindowMode,
  hideAfterRecordingCancel,
} from "./utils/recordingWindow";
import { Minimize2 } from "lucide-react";

type View =
  | "idle"
  | "step2"
  | "toolbar"
  | "ai-chat"
  | "ai-writing"
  | "video"
  | "more-options"
  | "clipboard";

function AppInner() {
  const { session, loading } = useAuth();
  const [view, setView] = useState<View>("idle");
  const [recordedBlob, setRecordedBlob] = useState<Blob | null>(null);
  const [recordingActive, setRecordingActive] = useState(false);
  const [autoStartRecording, setAutoStartRecording] = useState(false);
  const [stopRecordingSignal, setStopRecordingSignal] = useState(0);

  const handleVoiceToggle = useCallback(() => {
    if (!session) return;

    if (recordingActive) {
      setStopRecordingSignal((n) => n + 1);
      return;
    }
    setView((prev) => (prev === "step2" ? "idle" : prev));
    setAutoStartRecording(true);
    setRecordingActive(true);
  }, [session, recordingActive]);

  const handleToolbarToggle = useCallback(() => {
    if (!session) return;
    if (recordingActive) {
      setRecordingActive(false);
      setAutoStartRecording(false);
      exitRecordingWindowMode().catch(console.error);
    }
    setView((prev) => (prev === "toolbar" ? "idle" : "toolbar"));
  }, [session, recordingActive]);

  useEffect(() => {
    const unlisten1 = listen("voice-record-toggle", () => handleVoiceToggle());
    const unlisten2 = listen("toolbar-toggle", () => handleToolbarToggle());

    return () => {
      unlisten1.then((fn) => fn());
      unlisten2.then((fn) => fn());
    };
  }, [handleVoiceToggle, handleToolbarToggle]);

  useEffect(() => {
    if (!session || !recordingActive) return;
    enterRecordingWindowMode().catch(console.error);
  }, [session, recordingActive]);

  const endRecording = useCallback(async () => {
    setRecordingActive(false);
    setAutoStartRecording(false);
    await exitRecordingWindowMode().catch(console.error);
  }, []);

  const cancelRecording = useCallback(async () => {
    setRecordingActive(false);
    setAutoStartRecording(false);
    await exitRecordingWindowMode().catch(console.error);
    await hideAfterRecordingCancel().catch(console.error);
  }, []);

  if (loading) {
    return (
      <div
        style={{
          height: "100%",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "var(--bg-primary)",
        }}
      >
        <div className="shimmer" style={{ width: 200, height: 40 }} />
      </div>
    );
  }

  if (!session) {
    return (
      <div
        style={{
          height: "100%",
          display: "flex",
          flexDirection: "column",
          background: "var(--bg-primary)",
          borderRadius: 12,
          overflow: "hidden",
        }}
      >
        <TitleBar />
        <LoginPage />
      </div>
    );
  }

  const goBack = () => setView("idle");

  if (recordingActive) {
    return (
      <div className="recording-only-shell">
        <RecordingBar
          autoStart={autoStartRecording}
          stopSignal={stopRecordingSignal}
          onRecorded={async (blob) => {
            setRecordedBlob(blob);
            await endRecording();
            setView("step2");
          }}
          onCancel={cancelRecording}
        />
      </div>
    );
  }

  return (
    <div
      style={{
        height: "100%",
        display: "flex",
        flexDirection: "column",
        background: "var(--bg-primary)",
        borderRadius: 12,
        overflow: "hidden",
        position: "relative",
      }}
    >
      <TitleBar />

      <div
        style={{
          flex: 1,
          overflow: "auto",
          padding: "0 16px 16px",
        }}
      >
        {view === "idle" && (
          <IdleView
            onRecord={() => {
              setAutoStartRecording(true);
              setRecordingActive(true);
            }}
            onToolbar={() => setView("toolbar")}
          />
        )}

        {view === "step2" && recordedBlob && (
          <VoiceStep2 blob={recordedBlob} onBack={() => setView("idle")} onDone={goBack} />
        )}

        {view === "toolbar" && (
          <Toolbar
            onSelect={(panel) => setView(panel as View)}
            onBack={goBack}
          />
        )}

        {view === "ai-chat" && <AiChat onBack={() => setView("toolbar")} />}
        {view === "ai-writing" && (
          <AiWritingTools onBack={() => setView("toolbar")} />
        )}
        {view === "video" && <VideoPanel onBack={() => setView("toolbar")} />}
        {view === "more-options" && (
          <MoreOptions onBack={() => setView("toolbar")} />
        )}
        {view === "clipboard" && (
          <ClipboardPanel onBack={() => setView("toolbar")} />
        )}
      </div>
    </div>
  );
}

function IdleView({
  onRecord,
  onToolbar,
}: {
  onRecord: () => void;
  onToolbar: () => void;
}) {
  return (
    <div
      className="fade-in"
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "100%",
        gap: 24,
        textAlign: "center",
      }}
    >
      <div
        style={{
          width: 80,
          height: 80,
          borderRadius: "50%",
          background:
            "linear-gradient(135deg, var(--accent), var(--accent-hover))",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          boxShadow: "0 0 40px var(--accent-glow)",
        }}
      >
        <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/>
          <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
          <line x1="12" x2="12" y1="19" y2="22"/>
        </svg>
      </div>

      <div>
        <h2
          style={{ fontSize: 20, fontWeight: 600, color: "var(--text-primary)", marginBottom: 6 }}
        >
          DeltaVoice Desktop
        </h2>
        <p style={{ color: "var(--text-secondary)", fontSize: 13, lineHeight: 1.5 }}>
          Press{" "}
          <kbd
            style={{
              padding: "2px 8px",
              borderRadius: 6,
              background: "rgba(124, 82, 255, 0.2)",
              color: "var(--accent)",
              fontSize: 12,
              fontWeight: 600,
            }}
          >
            Ctrl+Space
          </kbd>{" "}
          to record voice
        </p>
        <p style={{ color: "var(--text-muted)", fontSize: 12, marginTop: 4 }}>
          Double press Ctrl+Space for AI toolbar
        </p>
        <p style={{ color: "var(--text-muted)", fontSize: 11, marginTop: 8 }}>
          Recording shows as a small floating bar — keep using other apps
        </p>
      </div>

      <div style={{ display: "flex", gap: 12, marginTop: 8 }}>
        <button className="btn-primary" onClick={onRecord} style={{ width: 160 }}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/>
            <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
            <line x1="12" x2="12" y1="19" y2="22"/>
          </svg>
          Record
        </button>
        <button
          className="btn-primary"
          onClick={onToolbar}
          style={{
            width: 160,
            background: "rgba(124, 82, 255, 0.15)",
            border: "1px solid rgba(124, 82, 255, 0.3)",
          }}
        >
          <Minimize2 size={18} />
          Toolbar
        </button>
      </div>

      <p style={{ color: "var(--text-muted)", fontSize: 11, marginTop: 16 }}>
        Works system-wide in any app: WhatsApp, Word, Gmail, VS Code...
      </p>
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <AppInner />
    </AuthProvider>
  );
}
