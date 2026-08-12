import { useState, useRef, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { ArrowLeft, Send, Copy, Check, ClipboardPaste } from "lucide-react";
import { callAiChat, type AiChatMessage } from "../api/convex";

interface Props {
  onBack: () => void;
}

interface Message extends AiChatMessage {
  id: number;
}

let msgId = 0;

export default function AiChat({ onBack }: Props) {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: msgId++,
      role: "assistant",
      content:
        "Hello! I'm your AI assistant. Ask me anything, or I can help you write, translate, and more.",
    },
  ]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [copiedId, setCopiedId] = useState<number | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages]);

  const handleSend = async () => {
    const text = input.trim();
    if (!text || sending) return;

    const userMsg: Message = { id: msgId++, role: "user", content: text };
    setMessages((prev) => [...prev, userMsg]);
    setInput("");
    setSending(true);

    try {
      const history = [...messages, userMsg].map(({ role, content }) => ({
        role,
        content,
      }));
      const res = await callAiChat(history);
      setMessages((prev) => [
        ...prev,
        { id: msgId++, role: "assistant", content: res.content },
      ]);
    } catch (err: any) {
      setMessages((prev) => [
        ...prev,
        {
          id: msgId++,
          role: "assistant",
          content: `Error: ${err.message || "Failed to get response"}`,
        },
      ]);
    } finally {
      setSending(false);
    }
  };

  const handleCopy = (id: number, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 1500);
  };

  const handleInsert = async (text: string) => {
    try {
      const appWindow = getCurrentWindow();
      await appWindow.hide();
      await new Promise((r) => setTimeout(r, 200));
      await invoke("insert_text_at_cursor", { text });
    } catch {
      // fallback: just copy
      navigator.clipboard.writeText(text);
    }
  };

  return (
    <div
      className="fade-in"
      style={{
        display: "flex",
        flexDirection: "column",
        height: "calc(100vh - 56px)",
        paddingTop: 8,
      }}
    >
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8, flexShrink: 0 }}>
        <button className="icon-btn" onClick={onBack} style={{ width: 32, height: 32 }}>
          <ArrowLeft size={16} />
        </button>
        <h3 style={{ flex: 1, fontSize: 16, fontWeight: 600 }}>AI Chat</h3>
      </div>

      {/* Messages */}
      <div
        ref={scrollRef}
        style={{
          flex: 1,
          overflow: "auto",
          display: "flex",
          flexDirection: "column",
          gap: 10,
          paddingBottom: 8,
        }}
      >
        {messages.map((msg) => (
          <div
            key={msg.id}
            style={{
              display: "flex",
              justifyContent: msg.role === "user" ? "flex-end" : "flex-start",
            }}
          >
            <div
              style={{
                maxWidth: "85%",
                padding: "10px 14px",
                borderRadius: 14,
                background:
                  msg.role === "user"
                    ? "linear-gradient(135deg, var(--accent), var(--accent-hover))"
                    : "var(--bg-surface)",
                color: "var(--text-primary)",
                fontSize: 13,
                lineHeight: 1.5,
                position: "relative",
              }}
            >
              <p style={{ whiteSpace: "pre-wrap" }}>{msg.content}</p>
              {msg.role === "assistant" && (
                <div style={{ display: "flex", gap: 4, marginTop: 6 }}>
                  <button
                    onClick={() => handleCopy(msg.id, msg.content)}
                    style={{
                      background: "none",
                      border: "none",
                      color: "var(--text-muted)",
                      cursor: "pointer",
                      padding: 2,
                      display: "flex",
                      alignItems: "center",
                      gap: 3,
                      fontSize: 10,
                    }}
                  >
                    {copiedId === msg.id ? <Check size={10} /> : <Copy size={10} />}
                    {copiedId === msg.id ? "Copied" : "Copy"}
                  </button>
                  <button
                    onClick={() => handleInsert(msg.content)}
                    style={{
                      background: "none",
                      border: "none",
                      color: "var(--text-muted)",
                      cursor: "pointer",
                      padding: 2,
                      display: "flex",
                      alignItems: "center",
                      gap: 3,
                      fontSize: 10,
                    }}
                  >
                    <ClipboardPaste size={10} />
                    Insert
                  </button>
                </div>
              )}
            </div>
          </div>
        ))}
        {sending && (
          <div style={{ display: "flex", justifyContent: "flex-start" }}>
            <div
              className="shimmer"
              style={{
                width: 120,
                height: 36,
                borderRadius: 14,
              }}
            />
          </div>
        )}
      </div>

      {/* Input */}
      <div
        style={{
          display: "flex",
          gap: 8,
          padding: "8px 0",
          flexShrink: 0,
        }}
      >
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleSend()}
          placeholder="Ask anything..."
          style={{
            flex: 1,
            height: 42,
            borderRadius: 12,
            border: "1px solid var(--border-glass)",
            background: "var(--bg-surface)",
            color: "var(--text-primary)",
            padding: "0 14px",
            fontSize: 13,
            outline: "none",
          }}
        />
        <button
          className="icon-btn active"
          onClick={handleSend}
          disabled={sending || !input.trim()}
          style={{ width: 42, height: 42, opacity: input.trim() ? 1 : 0.4 }}
        >
          <Send size={18} />
        </button>
      </div>
    </div>
  );
}
