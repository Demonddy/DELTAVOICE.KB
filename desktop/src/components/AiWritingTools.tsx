import { useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { getCurrentWindow } from "@tauri-apps/api/window";
import {
  ArrowLeft,
  Check as CheckIcon,
  MessageSquare,
  Languages,
  Sparkles,
  Palette,
  RefreshCw,
  PenTool,
  ArrowUpRight,
  AlignLeft,
  BookOpen,
  ArrowDownRight,
  Mail,
  Send,
  Copy,
  ClipboardPaste,
} from "lucide-react";
import { callWritingTool, LANGUAGES } from "../api/convex";

interface Props {
  onBack: () => void;
}

const TOOLS = [
  { id: "grammar", icon: CheckIcon, label: "Grammar" },
  { id: "reply", icon: MessageSquare, label: "Reply" },
  { id: "translate", icon: Languages, label: "Translate" },
  { id: "enhance", icon: Sparkles, label: "Enhance" },
  { id: "tone", icon: Palette, label: "Tone" },
  { id: "paraphrase", icon: RefreshCw, label: "Paraphrase" },
  { id: "continue", icon: PenTool, label: "Continue" },
  { id: "longer", icon: ArrowUpRight, label: "Longer" },
  { id: "summarize", icon: AlignLeft, label: "Summarize" },
  { id: "synonymous", icon: BookOpen, label: "Synonyms" },
  { id: "shorter", icon: ArrowDownRight, label: "Shorter" },
  { id: "email", icon: Mail, label: "Email" },
];

const TONES = ["Professional", "Friendly", "Formal", "Casual", "Encouraging"];

export default function AiWritingTools({ onBack }: Props) {
  const [selectedTool, setSelectedTool] = useState<string | null>(null);
  const [inputText, setInputText] = useState("");
  const [resultText, setResultText] = useState("");
  const [tone, setTone] = useState("Professional");
  const [language, setLanguage] = useState("en");
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState("");

  const handleProcess = async () => {
    if (!selectedTool || !inputText.trim()) return;
    setProcessing(true);
    setError("");
    setResultText("");
    try {
      const options: any = {};
      if (selectedTool === "tone") options.tone = tone;
      if (selectedTool === "translate") options.targetLanguage = language;

      const res = await callWritingTool(inputText, selectedTool, options);
      setResultText(res.result || "No result returned");
    } catch (err: any) {
      setError(err.message || "Processing failed");
    } finally {
      setProcessing(false);
    }
  };

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

  if (!selectedTool) {
    return (
      <div className="fade-in" style={{ paddingTop: 8 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
          <button className="icon-btn" onClick={onBack} style={{ width: 32, height: 32 }}>
            <ArrowLeft size={16} />
          </button>
          <h3 style={{ flex: 1, fontSize: 16, fontWeight: 600 }}>AI Writing Tools</h3>
        </div>

        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(3, 1fr)",
            gap: 8,
          }}
        >
          {TOOLS.map((tool) => (
            <button
              key={tool.id}
              onClick={() => setSelectedTool(tool.id)}
              className="mode-card"
              style={{ minHeight: 72 }}
            >
              <tool.icon size={18} color="var(--accent)" />
              <span className="card-title">{tool.label}</span>
            </button>
          ))}
        </div>
      </div>
    );
  }

  const toolLabel = TOOLS.find((t) => t.id === selectedTool)?.label || selectedTool;

  return (
    <div className="fade-in" style={{ display: "flex", flexDirection: "column", gap: 12, paddingTop: 8 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <button
          className="icon-btn"
          onClick={() => {
            setSelectedTool(null);
            setResultText("");
            setError("");
          }}
          style={{ width: 32, height: 32 }}
        >
          <ArrowLeft size={16} />
        </button>
        <h3 style={{ flex: 1, fontSize: 16, fontWeight: 600 }}>{toolLabel}</h3>
      </div>

      <textarea
        value={inputText}
        onChange={(e) => setInputText(e.target.value)}
        placeholder={`Paste or type text to ${toolLabel.toLowerCase()}...`}
        style={{
          minHeight: 100,
          borderRadius: 12,
          border: "1px solid var(--border-glass)",
          background: "var(--bg-surface)",
          color: "var(--text-primary)",
          padding: 12,
          fontSize: 13,
          resize: "vertical",
          outline: "none",
          lineHeight: 1.5,
          fontFamily: "inherit",
        }}
      />

      {selectedTool === "tone" && (
        <select className="pill-select" value={tone} onChange={(e) => setTone(e.target.value)}>
          {TONES.map((t) => (
            <option key={t} value={t.toLowerCase()}>
              {t}
            </option>
          ))}
        </select>
      )}

      {selectedTool === "translate" && (
        <select className="pill-select" value={language} onChange={(e) => setLanguage(e.target.value)}>
          {LANGUAGES.map((l) => (
            <option key={l.code} value={l.code}>
              {l.name}
            </option>
          ))}
        </select>
      )}

      {error && (
        <div style={{ padding: "8px 12px", borderRadius: 8, background: "rgba(239,68,68,0.15)", color: "var(--danger)", fontSize: 13 }}>
          {error}
        </div>
      )}

      <button className="btn-primary" onClick={handleProcess} disabled={processing || !inputText.trim()}>
        {processing ? (
          <>
            <div className="shimmer" style={{ width: 18, height: 18, borderRadius: "50%" }} />
            Processing...
          </>
        ) : (
          <>
            <Sparkles size={18} />
            Apply {toolLabel}
          </>
        )}
      </button>

      {resultText && (
        <div className="glass-panel fade-in" style={{ padding: 12, borderRadius: 14 }}>
          <p style={{ fontSize: 11, color: "var(--text-muted)", marginBottom: 6 }}>Result</p>
          <p style={{ fontSize: 13, color: "var(--text-primary)", lineHeight: 1.5, whiteSpace: "pre-wrap" }}>
            {resultText}
          </p>
          <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
            <button
              className="btn-primary"
              onClick={() => handleInsert(resultText)}
              style={{ flex: 1, height: 38, fontSize: 13 }}
            >
              <ClipboardPaste size={14} />
              Insert at Cursor
            </button>
            <button
              className="icon-btn"
              onClick={() => navigator.clipboard.writeText(resultText)}
              style={{ width: 38, height: 38 }}
            >
              <Copy size={14} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
