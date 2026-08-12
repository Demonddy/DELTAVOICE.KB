import {
  MoreVertical,
  Camera,
  Home,
  MessageSquare,
  Keyboard,
  Clipboard,
  ArrowLeft,
} from "lucide-react";

interface Props {
  onSelect: (panel: string) => void;
  onBack: () => void;
}

const ICONS = [
  { id: "more-options", icon: MoreVertical, label: "More" },
  { id: "video", icon: Camera, label: "Video" },
  { id: "idle", icon: Home, label: "Home" },
  { id: "ai-chat", icon: MessageSquare, label: "AI Chat" },
  { id: "ai-writing", icon: Keyboard, label: "KB+" },
  { id: "clipboard", icon: Clipboard, label: "Clipboard" },
];

export default function Toolbar({ onSelect, onBack }: Props) {
  return (
    <div className="fade-in" style={{ paddingTop: 12 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 16 }}>
        <button className="icon-btn" onClick={onBack} style={{ width: 32, height: 32 }}>
          <ArrowLeft size={16} />
        </button>
        <h3 style={{ fontSize: 16, fontWeight: 600, flex: 1 }}>AI Toolbar</h3>
        <span style={{ fontSize: 11, color: "var(--text-muted)" }}>
          Ctrl+Space×2
        </span>
      </div>

      <div
        className="glass-panel"
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-around",
          padding: "16px 12px",
          borderRadius: 20,
        }}
      >
        {ICONS.map((item) => (
          <button
            key={item.id}
            onClick={() => onSelect(item.id)}
            style={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: 6,
              background: "none",
              border: "none",
              cursor: "pointer",
            }}
          >
            <div className="icon-btn">
              <item.icon size={20} />
            </div>
            <span style={{ fontSize: 10, color: "var(--text-secondary)" }}>
              {item.label}
            </span>
          </button>
        ))}
      </div>

      <p
        style={{
          textAlign: "center",
          color: "var(--text-muted)",
          fontSize: 11,
          marginTop: 16,
        }}
      >
        Select a tool or press Ctrl+Space to record voice
      </p>
    </div>
  );
}
