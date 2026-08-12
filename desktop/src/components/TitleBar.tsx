import { getCurrentWindow } from "@tauri-apps/api/window";
import { Minus, X } from "lucide-react";
import { useAuth } from "../auth/AuthContext";

export default function TitleBar() {
  const { user, signOut } = useAuth();
  const appWindow = getCurrentWindow();

  const handleMinimize = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    await appWindow.minimize();
  };

  const handleClose = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    await appWindow.hide();
  };

  const handleSignOut = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    await signOut();
  };

  return (
    <div
      style={{
        height: 40,
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "0 12px",
        background: "rgba(17, 21, 33, 0.9)",
        borderBottom: "1px solid var(--border-glass)",
        flexShrink: 0,
      }}
    >
      <div
        data-tauri-drag-region
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          flex: 1,
          height: "100%",
          minWidth: 0,
        }}
      >
        <div
          style={{
            width: 20,
            height: 20,
            borderRadius: "50%",
            background: "linear-gradient(135deg, var(--accent), var(--accent-hover))",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            flexShrink: 0,
          }}
        >
          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/>
          </svg>
        </div>
        <span
          data-tauri-drag-region
          style={{
            fontSize: 12,
            fontWeight: 600,
            color: "var(--text-secondary)",
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          DeltaVoice
        </span>
      </div>

      <div className="titlebar-controls no-drag">
        {user && (
          <button type="button" className="titlebar-btn text" onClick={handleSignOut}>
            Sign Out
          </button>
        )}
        <button
          type="button"
          className="titlebar-btn"
          onClick={handleMinimize}
          title="Minimize"
        >
          <Minus size={14} />
        </button>
        <button
          type="button"
          className="titlebar-btn close"
          onClick={handleClose}
          title="Hide to tray"
        >
          <X size={14} />
        </button>
      </div>
    </div>
  );
}
