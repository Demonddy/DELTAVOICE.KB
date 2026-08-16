import {
  MoreVertical,
  Camera,
  Home,
  MessageSquare,
  Keyboard,
  Clipboard,
  X,
} from "lucide-react";

interface Props {
  onSelect: (panel: string) => void;
  onOpenWebsite: () => void;
  onClose: () => void;
}

const ICONS = [
  { id: "more-options", icon: MoreVertical, label: "More" },
  { id: "video", icon: Camera, label: "Video" },
  { id: "home", icon: Home, label: "Website", action: "website" as const },
  { id: "ai-chat", icon: MessageSquare, label: "AI Chat" },
  { id: "ai-writing", icon: Keyboard, label: "KB+" },
  { id: "clipboard", icon: Clipboard, label: "Clipboard" },
];

export default function ToolbarBar({ onSelect, onOpenWebsite, onClose }: Props) {
  return (
    <div className="toolbar-bar fade-in">
      <div className="toolbar-bar-inner glass-panel">
        <div className="toolbar-bar-icons">
          {ICONS.map((item) => (
            <button
              key={item.id}
              type="button"
              className="toolbar-bar-icon-btn"
              onClick={() =>
                "action" in item && item.action === "website"
                  ? onOpenWebsite()
                  : onSelect(item.id)
              }
              title={item.label}
            >
              <item.icon size={18} />
            </button>
          ))}
        </div>
        <button
          type="button"
          className="toolbar-bar-close"
          onClick={onClose}
          title="Close toolbar"
        >
          <X size={16} />
        </button>
      </div>
    </div>
  );
}
