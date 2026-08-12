import { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { readText } from "@tauri-apps/plugin-clipboard-manager";
import { ArrowLeft, ClipboardPaste, Trash2, Copy } from "lucide-react";

interface Props {
  onBack: () => void;
}

interface ClipItem {
  id: number;
  text: string;
  timestamp: number;
}

let clipId = 0;

export default function ClipboardPanel({ onBack }: Props) {
  const [items, setItems] = useState<ClipItem[]>([]);

  useEffect(() => {
    readText()
      .then((text) => {
        if (text?.trim()) {
          setItems([{ id: clipId++, text: text.trim(), timestamp: Date.now() }]);
        }
      })
      .catch(() => {});
  }, []);

  const handleInsert = async (text: string) => {
    try {
      const appWindow = getCurrentWindow();
      await appWindow.hide();
      await new Promise((r) => setTimeout(r, 200));
      await invoke("insert_text_at_cursor", { text });
    } catch {
      navigator.clipboard.writeText(text);
    }
  };

  const handleDelete = (id: number) => {
    setItems((prev) => prev.filter((i) => i.id !== id));
  };

  const handleRefresh = async () => {
    try {
      const text = await readText();
      if (text?.trim()) {
        const exists = items.some((i) => i.text === text.trim());
        if (!exists) {
          setItems((prev) => [
            { id: clipId++, text: text.trim(), timestamp: Date.now() },
            ...prev,
          ]);
        }
      }
    } catch {}
  };

  return (
    <div className="fade-in" style={{ paddingTop: 8 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
        <button className="icon-btn" onClick={onBack} style={{ width: 32, height: 32 }}>
          <ArrowLeft size={16} />
        </button>
        <h3 style={{ flex: 1, fontSize: 16, fontWeight: 600 }}>Clipboard</h3>
        <button
          className="icon-btn"
          onClick={handleRefresh}
          style={{ width: 32, height: 32 }}
        >
          <Copy size={14} />
        </button>
      </div>

      {items.length === 0 ? (
        <p
          style={{
            textAlign: "center",
            color: "var(--text-muted)",
            fontSize: 13,
            padding: 40,
          }}
        >
          No clipboard items yet. Copy some text and refresh.
        </p>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {items.map((item) => (
            <div
              key={item.id}
              className="glass-panel"
              style={{
                padding: "10px 12px",
                borderRadius: 12,
                display: "flex",
                alignItems: "flex-start",
                gap: 8,
              }}
            >
              <p
                style={{
                  flex: 1,
                  fontSize: 13,
                  color: "var(--text-primary)",
                  lineHeight: 1.4,
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  display: "-webkit-box",
                  WebkitLineClamp: 3,
                  WebkitBoxOrient: "vertical",
                }}
              >
                {item.text}
              </p>
              <div style={{ display: "flex", gap: 4, flexShrink: 0 }}>
                <button
                  className="icon-btn"
                  onClick={() => handleInsert(item.text)}
                  style={{ width: 28, height: 28 }}
                >
                  <ClipboardPaste size={12} />
                </button>
                <button
                  className="icon-btn"
                  onClick={() => handleDelete(item.id)}
                  style={{ width: 28, height: 28 }}
                >
                  <Trash2 size={12} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
