import { useState, useRef, useEffect, useCallback } from "react";
import { ArrowLeft, Upload, Mic, Square } from "lucide-react";
import { usePlatformInfo } from "../hooks/usePlatformInfo";

interface Props {
  onRecorded: (blob: Blob) => void;
  onBack: () => void;
  autoStart?: boolean;
  stopSignal?: number;
}

export default function VoiceRecorder({
  onRecorded,
  onBack,
  autoStart = false,
  stopSignal = 0,
}: Props) {
  const platform = usePlatformInfo();
  const [isRecording, setIsRecording] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const mediaRecRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const timerRef = useRef<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const saveOnStopRef = useRef(false);

  const startRecording = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream, {
        mimeType: MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
          ? "audio/webm;codecs=opus"
          : "audio/webm",
      });
      chunksRef.current = [];
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      recorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop());
        if (!saveOnStopRef.current) return;
        const blob = new Blob(chunksRef.current, { type: "audio/webm" });
        if (blob.size > 0) onRecorded(blob);
      };
      recorder.start(250);
      mediaRecRef.current = recorder;
      saveOnStopRef.current = false;
      setIsRecording(true);
      setElapsed(0);
      timerRef.current = window.setInterval(
        () => setElapsed((s) => s + 1),
        1000
      );
    } catch {
      alert("Microphone access denied. Please allow microphone access.");
    }
  }, [onRecorded]);

  const stopRecording = useCallback((save = true) => {
    saveOnStopRef.current = save;
    if (mediaRecRef.current?.state === "recording") {
      mediaRecRef.current.stop();
    }
    if (timerRef.current) clearInterval(timerRef.current);
    setIsRecording(false);
  }, []);

  // Auto-start when opened via global Ctrl+Space hotkey
  useEffect(() => {
    if (autoStart) {
      startRecording();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoStart]);

  // Stop when global Ctrl+Space is pressed again while recording
  useEffect(() => {
    if (stopSignal > 0 && isRecording) {
      stopRecording();
    }
  }, [stopSignal, isRecording, stopRecording]);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
      if (mediaRecRef.current?.state === "recording") {
        stopRecording(false);
      }
    };
  }, [stopRecording]);

  const handleUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) onRecorded(file);
  };

  const formatTime = (s: number) => {
    const m = Math.floor(s / 60);
    const sec = s % 60;
    return `${m.toString().padStart(2, "0")}:${sec.toString().padStart(2, "0")}`;
  };

  return (
    <div className="fade-in" style={{ display: "flex", flexDirection: "column", gap: 16, paddingTop: 8 }}>
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <button className="icon-btn" onClick={onBack} style={{ width: 32, height: 32 }}>
          <ArrowLeft size={16} />
        </button>
        <h3 style={{ flex: 1, fontSize: 18, fontWeight: 600 }}>Voice Input</h3>
        <span style={{ color: "var(--text-muted)", fontSize: 14 }}>{formatTime(elapsed)}</span>
        <button
          className="icon-btn"
          onClick={() => fileInputRef.current?.click()}
          style={{ width: 36, height: 36 }}
        >
          <Upload size={16} />
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept="audio/*"
          onChange={handleUpload}
          style={{ display: "none" }}
        />
      </div>

      {/* Mic button with rings */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          height: 220,
          position: "relative",
        }}
      >
        {isRecording && (
          <>
            <div
              className="pulse-ring"
              style={{ width: 160, height: 160, top: "50%", left: "50%", marginTop: -80, marginLeft: -80 }}
            />
            <div
              className="pulse-ring"
              style={{
                width: 120,
                height: 120,
                top: "50%",
                left: "50%",
                marginTop: -60,
                marginLeft: -60,
                animationDelay: "0.3s",
              }}
            />
          </>
        )}

        <button
          onClick={() => (isRecording ? stopRecording() : startRecording())}
          style={{
            width: 80,
            height: 80,
            borderRadius: "50%",
            border: "none",
            background: isRecording
              ? "linear-gradient(135deg, #ef4444, #dc2626)"
              : "linear-gradient(135deg, var(--accent), var(--accent-hover))",
            color: "#fff",
            cursor: "pointer",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            position: "relative",
            zIndex: 1,
            boxShadow: isRecording
              ? "0 0 30px rgba(239, 68, 68, 0.4)"
              : "0 0 30px var(--accent-glow)",
            transition: "all 0.3s ease",
          }}
        >
          {isRecording ? <Square size={28} fill="#fff" /> : <Mic size={32} />}
        </button>
      </div>

      {/* Status text */}
      <p
        style={{
          textAlign: "center",
          color: "var(--text-secondary)",
          fontSize: 14,
        }}
      >
        {isRecording
          ? `Recording... tap or press ${platform.voiceHotkey} to stop`
          : `Tap to record or press ${platform.voiceHotkey}`}
      </p>

      <p style={{ textAlign: "center", color: "var(--text-muted)", fontSize: 11 }}>
        Or upload an audio file using the upload button
      </p>
    </div>
  );
}
