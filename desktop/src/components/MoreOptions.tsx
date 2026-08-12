import { useState } from "react";
import { ArrowLeft, Calculator as CalcIcon, BookOpen } from "lucide-react";

interface Props {
  onBack: () => void;
}

export default function MoreOptions({ onBack }: Props) {
  const [panel, setPanel] = useState<"menu" | "calc" | "dict">("menu");

  if (panel === "calc") return <CalculatorPanel onBack={() => setPanel("menu")} />;
  if (panel === "dict") return <DictionaryPanel onBack={() => setPanel("menu")} />;

  return (
    <div className="fade-in" style={{ paddingTop: 8 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 16 }}>
        <button className="icon-btn" onClick={onBack} style={{ width: 32, height: 32 }}>
          <ArrowLeft size={16} />
        </button>
        <h3 style={{ flex: 1, fontSize: 16, fontWeight: 600 }}>More Options</h3>
      </div>

      <div style={{ display: "flex", gap: 16, justifyContent: "center" }}>
        <button onClick={() => setPanel("calc")} className="mode-card" style={{ width: 120, minHeight: 100 }}>
          <CalcIcon size={28} color="var(--accent)" />
          <span className="card-title">Calculator</span>
        </button>
        <button onClick={() => setPanel("dict")} className="mode-card" style={{ width: 120, minHeight: 100 }}>
          <BookOpen size={28} color="var(--accent)" />
          <span className="card-title">Dictionary</span>
        </button>
      </div>
    </div>
  );
}

function CalculatorPanel({ onBack }: { onBack: () => void }) {
  const [display, setDisplay] = useState("0");
  const [prev, setPrev] = useState<number | null>(null);
  const [op, setOp] = useState<string | null>(null);
  const [fresh, setFresh] = useState(true);

  const press = (val: string) => {
    if (val === "C") {
      setDisplay("0");
      setPrev(null);
      setOp(null);
      setFresh(true);
      return;
    }
    if (val === "=") {
      if (prev !== null && op) {
        const cur = parseFloat(display);
        let result = 0;
        if (op === "+") result = prev + cur;
        else if (op === "-") result = prev - cur;
        else if (op === "×") result = prev * cur;
        else if (op === "÷") result = cur !== 0 ? prev / cur : 0;
        setDisplay(String(parseFloat(result.toFixed(10))));
        setPrev(null);
        setOp(null);
        setFresh(true);
      }
      return;
    }
    if (["+", "-", "×", "÷"].includes(val)) {
      setPrev(parseFloat(display));
      setOp(val);
      setFresh(true);
      return;
    }
    if (fresh) {
      setDisplay(val === "." ? "0." : val);
      setFresh(false);
    } else {
      if (val === "." && display.includes(".")) return;
      setDisplay(display + val);
    }
  };

  const buttons = [
    "C", "÷", "×", "-",
    "7", "8", "9", "+",
    "4", "5", "6", "=",
    "1", "2", "3", ".",
    "0",
  ];

  return (
    <div className="fade-in" style={{ paddingTop: 8 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
        <button className="icon-btn" onClick={onBack} style={{ width: 32, height: 32 }}>
          <ArrowLeft size={16} />
        </button>
        <h3 style={{ flex: 1, fontSize: 16, fontWeight: 600 }}>Calculator</h3>
      </div>

      <div
        className="glass-panel"
        style={{ padding: "16px 12px", borderRadius: 16, textAlign: "right", marginBottom: 12, fontSize: 28, fontWeight: 600, minHeight: 60, display: "flex", alignItems: "center", justifyContent: "flex-end" }}
      >
        {display}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 6 }}>
        {buttons.map((b) => (
          <button
            key={b}
            onClick={() => press(b)}
            style={{
              height: 48,
              borderRadius: 10,
              border: "none",
              background: ["+", "-", "×", "÷", "="].includes(b)
                ? "var(--accent)"
                : b === "C"
                ? "rgba(239, 68, 68, 0.3)"
                : "var(--bg-surface)",
              color: "var(--text-primary)",
              fontSize: 18,
              fontWeight: 600,
              cursor: "pointer",
              gridColumn: b === "0" ? "span 2" : undefined,
            }}
          >
            {b}
          </button>
        ))}
      </div>
    </div>
  );
}

function DictionaryPanel({ onBack }: { onBack: () => void }) {
  const [word, setWord] = useState("");
  const [result, setResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  const lookup = async () => {
    if (!word.trim()) return;
    setLoading(true);
    setResult(null);
    try {
      const res = await fetch(
        `https://api.dictionaryapi.dev/api/v2/entries/en/${encodeURIComponent(word.trim())}`
      );
      if (res.ok) {
        const data = await res.json();
        setResult(data[0]);
      } else {
        setResult({ error: "Word not found" });
      }
    } catch {
      setResult({ error: "Lookup failed" });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fade-in" style={{ paddingTop: 8 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
        <button className="icon-btn" onClick={onBack} style={{ width: 32, height: 32 }}>
          <ArrowLeft size={16} />
        </button>
        <h3 style={{ flex: 1, fontSize: 16, fontWeight: 600 }}>Dictionary</h3>
      </div>

      <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
        <input
          type="text"
          value={word}
          onChange={(e) => setWord(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && lookup()}
          placeholder="Type a word..."
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
        <button className="btn-primary" onClick={lookup} disabled={loading} style={{ width: 80, height: 42 }}>
          {loading ? "..." : "Search"}
        </button>
      </div>

      {result?.error && (
        <p style={{ color: "var(--text-muted)", fontSize: 13, textAlign: "center" }}>
          {result.error}
        </p>
      )}

      {result?.meanings && (
        <div className="glass-panel" style={{ padding: 14, borderRadius: 14, maxHeight: 300, overflow: "auto" }}>
          <h4 style={{ fontSize: 18, fontWeight: 600, marginBottom: 4 }}>{result.word}</h4>
          {result.phonetic && (
            <p style={{ color: "var(--text-secondary)", fontSize: 13, marginBottom: 10 }}>
              {result.phonetic}
            </p>
          )}
          {result.meanings?.map((m: any, i: number) => (
            <div key={i} style={{ marginBottom: 10 }}>
              <p style={{ color: "var(--accent)", fontSize: 12, fontWeight: 600, marginBottom: 4 }}>
                {m.partOfSpeech}
              </p>
              {m.definitions?.slice(0, 3).map((d: any, j: number) => (
                <p key={j} style={{ fontSize: 13, color: "var(--text-primary)", marginBottom: 4, lineHeight: 1.4 }}>
                  {j + 1}. {d.definition}
                </p>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
