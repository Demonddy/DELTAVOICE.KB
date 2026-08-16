import { useState, useEffect, useCallback } from "react";
import { listen } from "@tauri-apps/api/event";
import { open } from "@tauri-apps/plugin-shell";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import LoginPage from "./auth/LoginPage";
import VoiceStep2 from "./components/VoiceStep2";
import RecordingBar from "./components/RecordingBar";
import ToolbarBar from "./components/ToolbarBar";
import AiChat from "./components/AiChat";
import AiWritingTools from "./components/AiWritingTools";
import VideoPanel from "./components/VideoPanel";
import MoreOptions from "./components/MoreOptions";
import ClipboardPanel from "./components/ClipboardPanel";
import TitleBar from "./components/TitleBar";
import {
  enterFloatingBarMode,
  exitFloatingBarMode,
  hideFloatingBar,
  resizeFloatingBar,
  showMainWindow,
} from "./utils/recordingWindow";
import { Minimize2 } from "lucide-react";
import { WEBSITE_URL } from "./config";
import { usePlatformInfo } from "./hooks/usePlatformInfo";
import { HotkeyLabel } from "./components/HotkeyLabel";

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
      exitFloatingBarMode().catch(console.error);
    }
    setView((prev) => {
      if (prev === "toolbar") {
        exitFloatingBarMode()
          .then(() => hideFloatingBar())
          .catch(console.error);
        return "idle";
      }
      return "toolbar";
    });
  }, [session, recordingActive]);

  const closeToolbar = useCallback(async () => {
    setView("idle");
    await exitFloatingBarMode().catch(console.error);
    await hideFloatingBar().catch(console.error);
  }, []);

  const openFullWindow = useCallback(async () => {
    await exitFloatingBarMode().catch(console.error);
    await showMainWindow().catch(console.error);
  }, []);

  const openToolbarPanel = useCallback(
    async (panel: string) => {
      await openFullWindow();
      setView(panel as View);
    },
    [openFullWindow]
  );

  const openWebsite = useCallback(() => {
    open(WEBSITE_URL).catch(console.error);
  }, []);

  useEffect(() => {
    const unlisten1 = listen("voice-record-toggle", () => handleVoiceToggle());
    const unlisten2 = listen("toolbar-toggle", () => handleToolbarToggle());

    return () => {
      unlisten1.then((fn) => fn());
      unlisten2.then((fn) => fn());
    };
  }, [handleVoiceToggle, handleToolbarToggle]);

  useEffect(() => {
    if (!session || recordingActive) return;
    if (view === "toolbar") {
      enterFloatingBarMode("toolbar").catch(console.error);
    } else if (view === "step2") {
      enterFloatingBarMode("step2").catch(console.error);
    }
  }, [session, recordingActive, view]);

  useEffect(() => {
    if (!session || !recordingActive) return;
    enterFloatingBarMode("recording").catch(console.error);
  }, [session, recordingActive]);

  const finishRecording = useCallback(async (blob: Blob) => {
    setRecordedBlob(blob);
    setRecordingActive(false);
    setAutoStartRecording(false);
    setView("step2");
    await resizeFloatingBar("step2").catch(console.error);
  }, []);

  const cancelRecording = useCallback(async () => {
    setRecordingActive(false);
    setAutoStartRecording(false);
    await exitFloatingBarMode().catch(console.error);
    await hideFloatingBar().catch(console.error);
  }, []);

  const goBackFromStep2 = useCallback(async () => {
    await exitFloatingBarMode().catch(console.error);
    await hideFloatingBar().catch(console.error);
    setView("idle");
    setRecordedBlob(null);
  }, []);

  const finishStep2 = useCallback(async () => {
    await exitFloatingBarMode().catch(console.error);
    await hideFloatingBar().catch(console.error);
    setView("idle");
    setRecordedBlob(null);
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

  if (recordingActive) {
    return (
      <div className="floating-bar-shell">
        <RecordingBar
          autoStart={autoStartRecording}
          stopSignal={stopRecordingSignal}
          onRecorded={finishRecording}
          onCancel={cancelRecording}
        />
      </div>
    );
  }

  if (view === "step2" && recordedBlob) {
    return (
      <div className="floating-bar-shell step2-shell">
        <VoiceStep2
          blob={recordedBlob}
          onBack={goBackFromStep2}
          onDone={finishStep2}
        />
      </div>
    );
  }

  if (view === "toolbar") {
    return (
      <div className="floating-bar-shell">
        <ToolbarBar
          onSelect={openToolbarPanel}
          onOpenWebsite={openWebsite}
          onClose={closeToolbar}
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
  const platform = usePlatformInfo();

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
          Press <HotkeyLabel label={platform.voiceHotkey} /> to record voice
        </p>
        <p style={{ color: "var(--text-muted)", fontSize: 12, marginTop: 4 }}>
          Double press <HotkeyLabel label={platform.voiceHotkey} style={{ fontSize: 11 }} /> for AI toolbar
        </p>
        {platform.os === "macos" && (
          <p style={{ color: "var(--text-muted)", fontSize: 11, marginTop: 4 }}>
            Paste uses {platform.pasteShortcut} in other apps
          </p>
        )}
        <p style={{ color: "var(--text-muted)", fontSize: 11, marginTop: 8 }}>
          Recording and AI tools appear as small floating bars at the bottom
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
