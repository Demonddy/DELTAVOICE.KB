import { useState, useRef, useEffect } from "react";
import {
  ArrowLeft,
  Upload,
  Camera,
  Square,
  Play,
  Pause,
  Sparkles,
  Send,
} from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { callVideoWorkflow, LANGUAGES, VOICES } from "../api/convex";

interface Props {
  onBack: () => void;
}

async function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => resolve((reader.result as string).split(",")[1]);
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

export default function VideoPanel({ onBack }: Props) {
  const [isRecording, setIsRecording] = useState(false);
  const [videoBlob, setVideoBlob] = useState<Blob | null>(null);
  const [videoUrl, setVideoUrl] = useState("");
  const [language, setLanguage] = useState("en");
  const [voice, setVoice] = useState("aria");
  const [processing, setProcessing] = useState(false);
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState("");
  const videoRef = useRef<HTMLVideoElement>(null);
  const mediaRecRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const streamRef = useRef<MediaStream | null>(null);
  const previewRef = useRef<HTMLVideoElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: true,
        audio: true,
      });
      streamRef.current = stream;
      if (previewRef.current) {
        previewRef.current.srcObject = stream;
        previewRef.current.play();
      }
      const recorder = new MediaRecorder(stream, { mimeType: "video/webm" });
      chunksRef.current = [];
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      recorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop());
        const blob = new Blob(chunksRef.current, { type: "video/webm" });
        setVideoBlob(blob);
        setVideoUrl(URL.createObjectURL(blob));
      };
      recorder.start(500);
      mediaRecRef.current = recorder;
      setIsRecording(true);
    } catch {
      setError("Camera/mic access denied");
    }
  };

  const stopRecording = () => {
    mediaRecRef.current?.stop();
    setIsRecording(false);
  };

  const handleUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setVideoBlob(file);
      setVideoUrl(URL.createObjectURL(file));
    }
  };

  const handleProcess = async () => {
    if (!videoBlob) return;
    setProcessing(true);
    setError("");
    try {
      const base64 = await blobToBase64(videoBlob);
      const res = await callVideoWorkflow({
        videoBase64: base64,
        targetLanguage: language,
        voiceStyle: voice,
        videoFormat: "webm",
      });
      setResult(res);
    } catch (err: any) {
      setError(err.message || "Video processing failed");
    } finally {
      setProcessing(false);
    }
  };

  const handleInsertText = async (text: string) => {
    try {
      const appWindow = getCurrentWindow();
      await appWindow.hide();
      await new Promise((r) => setTimeout(r, 200));
      await invoke("insert_text_at_cursor", { text });
    } catch {
      navigator.clipboard.writeText(text);
    }
  };

  useEffect(() => {
    return () => {
      streamRef.current?.getTracks().forEach((t) => t.stop());
      if (videoUrl) URL.revokeObjectURL(videoUrl);
    };
  }, [videoUrl]);

  return (
    <div className="fade-in" style={{ display: "flex", flexDirection: "column", gap: 12, paddingTop: 8 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <button className="icon-btn" onClick={onBack} style={{ width: 32, height: 32 }}>
          <ArrowLeft size={16} />
        </button>
        <h3 style={{ flex: 1, fontSize: 16, fontWeight: 600 }}>Video</h3>
        <button className="icon-btn" onClick={() => fileInputRef.current?.click()} style={{ width: 32, height: 32 }}>
          <Upload size={14} />
        </button>
        <input ref={fileInputRef} type="file" accept="video/*" onChange={handleUpload} style={{ display: "none" }} />
      </div>

      {/* Camera preview / recorded video */}
      <div
        style={{
          width: "100%",
          height: 200,
          borderRadius: 14,
          overflow: "hidden",
          background: "var(--bg-surface)",
          position: "relative",
        }}
      >
        {!videoUrl ? (
          <video
            ref={previewRef}
            style={{ width: "100%", height: "100%", objectFit: "cover" }}
            muted
            playsInline
          />
        ) : (
          <video
            ref={videoRef}
            src={videoUrl}
            style={{ width: "100%", height: "100%", objectFit: "cover" }}
            controls
          />
        )}
        {!videoUrl && (
          <div style={{ position: "absolute", bottom: 12, left: 0, right: 0, display: "flex", justifyContent: "center" }}>
            <button
              className="icon-btn"
              onClick={isRecording ? stopRecording : startRecording}
              style={{
                width: 56,
                height: 56,
                background: isRecording
                  ? "linear-gradient(135deg, #ef4444, #dc2626)"
                  : "linear-gradient(135deg, var(--accent), var(--accent-hover))",
              }}
            >
              {isRecording ? <Square size={24} fill="#fff" /> : <Camera size={24} />}
            </button>
          </div>
        )}
      </div>

      {videoUrl && !result && (
        <>
          <div style={{ display: "flex", gap: 8 }}>
            <select className="pill-select" value={language} onChange={(e) => setLanguage(e.target.value)}>
              {LANGUAGES.map((l) => (
                <option key={l.code} value={l.code}>{l.name}</option>
              ))}
            </select>
            <select className="pill-select" value={voice} onChange={(e) => setVoice(e.target.value)}>
              {VOICES.map((v) => (
                <option key={v} value={v.toLowerCase()}>{v}</option>
              ))}
            </select>
          </div>

          {error && (
            <div style={{ padding: "8px 12px", borderRadius: 8, background: "rgba(239,68,68,0.15)", color: "var(--danger)", fontSize: 13 }}>
              {error}
            </div>
          )}

          <button className="btn-primary" onClick={handleProcess} disabled={processing}>
            {processing ? "Processing..." : <><Sparkles size={18} /> Process Video</>}
          </button>
        </>
      )}

      {result && (
        <div className="fade-in" style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {result.translatedText && (
            <div className="glass-panel" style={{ padding: 12, borderRadius: 14 }}>
              <p style={{ fontSize: 11, color: "var(--text-muted)", marginBottom: 4 }}>Translated</p>
              <p style={{ fontSize: 13, color: "var(--text-primary)", lineHeight: 1.4 }}>
                {result.translatedText}
              </p>
            </div>
          )}
          {result.translatedText && (
            <button className="btn-primary" onClick={() => handleInsertText(result.translatedText)}>
              <Send size={18} /> Insert Text at Cursor
            </button>
          )}
          <button
            onClick={() => { setResult(null); setVideoBlob(null); setVideoUrl(""); }}
            style={{
              background: "none",
              border: "1px solid var(--border-glass)",
              borderRadius: 10,
              height: 38,
              color: "var(--text-secondary)",
              cursor: "pointer",
              fontSize: 13,
            }}
          >
            Record Another
          </button>
        </div>
      )}
    </div>
  );
}
