import { useState, useRef, useEffect, useCallback } from "react";
import { invoke } from "@tauri-apps/api/core";
import { getCurrentWindow } from "@tauri-apps/api/window";
import {
  ArrowLeft,
  Zap,
  Mic,
  Type,
  Play,
  Pause,
  Send,
  Sparkles,
  Copy,
  Check,
  Square,
} from "lucide-react";
import {
  callVoiceWorkflow,
  callCreateVoiceClone,
  LANGUAGES,
  VOICES,
  type VoiceWorkflowResult,
} from "../api/convex";
import {
  loadSavedVoiceClone,
  saveVoiceClone,
  cloneVoiceStyle,
  type SavedVoiceClone,
} from "../utils/savedVoiceClone";

type Mode = "complete" | "voice-only" | "text-only";
type ClonePhase = "choose" | "record" | "saving" | "ready";

interface Props {
  blob: Blob;
  onBack: () => void;
  onDone: () => void;
}

const MIN_SAMPLE_SECONDS = 8;

async function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      const result = reader.result as string;
      resolve(result.split(",")[1]);
    };
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

function formatTime(seconds: number) {
  if (!Number.isFinite(seconds) || seconds < 0) return "0:00";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

export default function VoiceStep2({ blob, onBack, onDone }: Props) {
  const [mode, setMode] = useState<Mode>("complete");
  const [language, setLanguage] = useState("en");
  const [voice, setVoice] = useState("aria");
  const [processing, setProcessing] = useState(false);
  const [result, setResult] = useState<VoiceWorkflowResult | null>(null);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);

  const [previewPlaying, setPreviewPlaying] = useState(false);
  const [previewProgress, setPreviewProgress] = useState(0);
  const [previewDuration, setPreviewDuration] = useState(0);

  const [resultPlaying, setResultPlaying] = useState(false);
  const [resultProgress, setResultProgress] = useState(0);
  const [resultDuration, setResultDuration] = useState(0);

  const [savedClone, setSavedClone] = useState<SavedVoiceClone | null>(() => loadSavedVoiceClone());
  const [clonePhase, setClonePhase] = useState<ClonePhase>(
    loadSavedVoiceClone() ? "choose" : "record"
  );
  const [activeCloneId, setActiveCloneId] = useState("");
  const [sampleRecording, setSampleRecording] = useState(false);
  const [sampleElapsed, setSampleElapsed] = useState(0);

  const previewAudioRef = useRef<HTMLAudioElement | null>(null);
  const resultAudioRef = useRef<HTMLAudioElement | null>(null);
  const recordingUrlRef = useRef<string>("");
  const sampleRecorderRef = useRef<MediaRecorder | null>(null);
  const sampleChunksRef = useRef<Blob[]>([]);
  const sampleTimerRef = useRef<number | null>(null);
  const sampleStreamRef = useRef<MediaStream | null>(null);
  const sampleElapsedRef = useRef(0);
  const sampleStartedAtRef = useRef(0);

  const cloneReady = mode !== "voice-only" || Boolean(activeCloneId);
  const showTextResult = Boolean(
    result && (result.workflowType === "text-only" || result.ttsFallback)
  );

  useEffect(() => {
    recordingUrlRef.current = URL.createObjectURL(blob);
    return () => {
      stopPreview();
      stopResultAudio();
      stopSampleRecording(false);
      if (recordingUrlRef.current) URL.revokeObjectURL(recordingUrlRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [blob]);

  const stopPreview = useCallback(() => {
    const audio = previewAudioRef.current;
    if (audio) {
      audio.pause();
      audio.currentTime = 0;
    }
    setPreviewPlaying(false);
    setPreviewProgress(0);
  }, []);

  const stopResultAudio = useCallback(() => {
    const audio = resultAudioRef.current;
    if (audio) {
      audio.pause();
      audio.currentTime = 0;
    }
    setResultPlaying(false);
    setResultProgress(0);
  }, []);

  const stopSampleRecording = useCallback((save: boolean) => {
    if (sampleTimerRef.current) {
      window.clearInterval(sampleTimerRef.current);
      sampleTimerRef.current = null;
    }
    const recorder = sampleRecorderRef.current;
    if (recorder && recorder.state === "recording") {
      if (!save) sampleChunksRef.current = [];
      recorder.stop();
    }
    sampleStreamRef.current?.getTracks().forEach((t) => t.stop());
    sampleStreamRef.current = null;
    setSampleRecording(false);
  }, []);

  const selectMode = (next: Mode) => {
    setMode(next);
    setError("");
    if (next !== "voice-only") return;
    const existing = loadSavedVoiceClone();
    setSavedClone(existing);
    if (existing && activeCloneId === existing.voiceId) {
      setClonePhase("ready");
      return;
    }
    setActiveCloneId("");
    setClonePhase(existing ? "choose" : "record");
  };

  const useSavedClone = () => {
    const existing = savedClone || loadSavedVoiceClone();
    if (!existing) return;
    setSavedClone(existing);
    setActiveCloneId(existing.voiceId);
    setClonePhase("ready");
    setError("");
  };

  const startNewSample = () => {
    setActiveCloneId("");
    setClonePhase("record");
    setError("");
  };

  const startSampleRecording = async () => {
    stopPreview();
    stopResultAudio();
    setError("");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      sampleStreamRef.current = stream;
      const recorder = new MediaRecorder(stream, {
        mimeType: MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
          ? "audio/webm;codecs=opus"
          : "audio/webm",
      });
      sampleChunksRef.current = [];
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) sampleChunksRef.current.push(e.data);
      };
      recorder.onstop = async () => {
        stream.getTracks().forEach((t) => t.stop());
        const sampleBlob = new Blob(sampleChunksRef.current, { type: "audio/webm" });
        if (sampleBlob.size < 1000) return;
        const recordedSec = Math.floor((Date.now() - sampleStartedAtRef.current) / 1000);
        if (recordedSec < MIN_SAMPLE_SECONDS) {
          setError(`Record at least ${MIN_SAMPLE_SECONDS} seconds of clear speech.`);
          setClonePhase("record");
          return;
        }
        setClonePhase("saving");
        try {
          const audioBase64 = await blobToBase64(sampleBlob);
          const name = `My Voice ${new Date().toLocaleDateString()}`;
          const created = await callCreateVoiceClone({
            audioBase64,
            name,
            format: "webm",
          });
          const stored = {
            voiceId: created.voiceId,
            name: created.name,
            createdAt: Date.now(),
          };
          saveVoiceClone(stored);
          setSavedClone(stored);
          setActiveCloneId(created.voiceId);
          setClonePhase("ready");
        } catch (err: any) {
          setError(err.message || "Could not save your voice");
          setClonePhase("record");
        }
      };
      recorder.start(250);
      sampleRecorderRef.current = recorder;
      sampleElapsedRef.current = 0;
      sampleStartedAtRef.current = Date.now();
      setSampleElapsed(0);
      setSampleRecording(true);
      sampleTimerRef.current = window.setInterval(() => {
        sampleElapsedRef.current += 1;
        setSampleElapsed(sampleElapsedRef.current);
      }, 1000);
    } catch {
      setError("Microphone access denied. Allow the mic to save your voice.");
    }
  };

  const togglePreview = useCallback(() => {
    if (previewPlaying) {
      previewAudioRef.current?.pause();
      setPreviewPlaying(false);
      return;
    }

    stopResultAudio();

    let audio = previewAudioRef.current;
    if (!audio) {
      audio = new Audio(recordingUrlRef.current);
      previewAudioRef.current = audio;
      audio.onloadedmetadata = () => setPreviewDuration(audio!.duration || 0);
      audio.ontimeupdate = () => setPreviewProgress(audio!.currentTime);
      audio.onended = () => {
        setPreviewPlaying(false);
        setPreviewProgress(0);
      };
    }

    audio.play().then(() => setPreviewPlaying(true)).catch(() => {
      setError("Could not play recording preview");
    });
  }, [previewPlaying, stopResultAudio]);

  const toggleResultAudio = useCallback(() => {
    if (!result?.convertedAudioBase64) return;

    if (resultPlaying) {
      resultAudioRef.current?.pause();
      setResultPlaying(false);
      return;
    }

    stopPreview();

    let audio = resultAudioRef.current;
    if (!audio || audio.src !== `data:audio/mpeg;base64,${result.convertedAudioBase64}`) {
      if (resultAudioRef.current) {
        resultAudioRef.current.pause();
      }
      audio = new Audio(`data:audio/mpeg;base64,${result.convertedAudioBase64}`);
      resultAudioRef.current = audio;
      audio.onloadedmetadata = () => setResultDuration(audio!.duration || 0);
      audio.ontimeupdate = () => setResultProgress(audio!.currentTime);
      audio.onended = () => {
        setResultPlaying(false);
        setResultProgress(0);
      };
    }

    audio.play().then(() => setResultPlaying(true)).catch(() => {
      setError("Could not play result audio");
    });
  }, [result, resultPlaying, stopPreview]);

  const handleProcess = async () => {
    if (mode === "voice-only" && !activeCloneId) {
      setError("Save or choose your voice before translating.");
      return;
    }
    stopPreview();
    stopSampleRecording(false);
    setProcessing(true);
    setError("");
    setResult(null);
    try {
      const audioBase64 = await blobToBase64(blob);
      const res = await callVoiceWorkflow({
        audioBase64,
        targetLanguage: language,
        voiceStyle: mode === "voice-only" ? cloneVoiceStyle(activeCloneId) : voice,
        workflowType: mode,
        format: "webm",
      });
      setResult(res);
    } catch (err: any) {
      setError(err.message || "Processing failed");
    } finally {
      setProcessing(false);
    }
  };

  const handleInsertText = async (text: string) => {
    try {
      stopPreview();
      stopResultAudio();
      const appWindow = getCurrentWindow();
      await appWindow.hide();
      await new Promise((r) => setTimeout(r, 200));
      await invoke("insert_text_at_cursor", { text });
      onDone();
    } catch (err: any) {
      setError(`Insert failed: ${err.message || err}`);
    }
  };

  const handleCopy = async (text: string) => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const previewPct =
    previewDuration > 0 ? Math.min(100, (previewProgress / previewDuration) * 100) : 0;
  const resultPct =
    resultDuration > 0 ? Math.min(100, (resultProgress / resultDuration) * 100) : 0;

  const modes: { key: Mode; icon: typeof Zap; title: string; desc: string }[] = [
    {
      key: "complete",
      icon: Zap,
      title: "Change Language\n& Voice",
      desc: "Translate + new voice",
    },
    {
      key: "voice-only",
      icon: Mic,
      title: "Translate My\nSame Voice",
      desc: "Keep your voice",
    },
    {
      key: "text-only",
      icon: Type,
      title: "Transcript\n& Translate",
      desc: "Text only",
    },
  ];

  return (
    <div className="fade-in" style={{ display: "flex", flexDirection: "column", gap: 12, paddingTop: 8 }}>
      <div
        className="glass-panel"
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          padding: "8px 12px",
          borderRadius: 14,
        }}
      >
        <button
          type="button"
          className="icon-btn"
          onClick={togglePreview}
          disabled={processing}
          style={{ width: 36, height: 36, flexShrink: 0 }}
          title={previewPlaying ? "Pause preview" : "Play preview"}
        >
          {previewPlaying ? <Pause size={16} /> : <Play size={16} />}
        </button>

        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              height: 4,
              borderRadius: 2,
              background: "rgba(124, 82, 255, 0.2)",
              overflow: "hidden",
            }}
          >
            <div
              style={{
                width: `${previewPct}%`,
                height: "100%",
                borderRadius: 2,
                background: "var(--accent)",
                transition: previewPlaying ? "width 0.1s linear" : "none",
              }}
            />
          </div>
        </div>

        <span
          style={{
            fontSize: 12,
            color: "var(--text-muted)",
            fontVariantNumeric: "tabular-nums",
            minWidth: 36,
            textAlign: "right",
          }}
        >
          {formatTime(previewPlaying ? previewProgress : previewDuration)}
        </span>

        <button
          type="button"
          className="icon-btn"
          onClick={handleProcess}
          disabled={processing || !cloneReady}
          style={{
            width: 36,
            height: 36,
            flexShrink: 0,
            background: "rgba(16, 185, 129, 0.2)",
            border: "1px solid rgba(16, 185, 129, 0.35)",
          }}
          title="Send for processing"
        >
          <Send size={16} color="#10b981" />
        </button>

        <button
          type="button"
          className="icon-btn"
          onClick={() => {
            stopPreview();
            stopSampleRecording(false);
            onBack();
          }}
          style={{ width: 32, height: 32, flexShrink: 0 }}
          title="Back"
        >
          <ArrowLeft size={14} />
        </button>
      </div>

      {!result ? (
        <>
          <div>
            <h3 style={{ fontSize: 15, fontWeight: 600, color: "var(--text-primary)" }}>
              Choose Processing Mode
            </h3>
            <p style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 2 }}>
              Select how to process your recording
            </p>
          </div>

          <div style={{ display: "flex", gap: 8 }}>
            {modes.map((m) => (
              <button
                key={m.key}
                type="button"
                className={`mode-card ${mode === m.key ? "selected" : ""}`}
                onClick={() => selectMode(m.key)}
                disabled={processing}
              >
                <m.icon size={20} color={mode === m.key ? "#7c52ff" : "#e5e7eb"} />
                <span className="card-title" style={{ whiteSpace: "pre-line" }}>
                  {m.title}
                </span>
                <span className="card-desc">{m.desc}</span>
              </button>
            ))}
          </div>

          <div style={{ display: "flex", gap: 8 }}>
            <select
              className="pill-select"
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
              disabled={processing}
            >
              {LANGUAGES.map((l) => (
                <option key={l.code} value={l.code}>
                  {l.name}
                </option>
              ))}
            </select>

            {mode === "complete" && (
              <select
                className="pill-select"
                value={voice}
                onChange={(e) => setVoice(e.target.value)}
                disabled={processing}
              >
                {VOICES.map((v) => (
                  <option key={v} value={v.toLowerCase()}>
                    {v}
                  </option>
                ))}
              </select>
            )}
          </div>

          {mode === "voice-only" && (
            <div className={`clone-box ${clonePhase === "ready" ? "ready" : ""}`}>
              {clonePhase === "choose" && savedClone && (
                <>
                  <h4>Use your saved voice?</h4>
                  <p>
                    {savedClone.name} is ready. Use it for this translation, or record a new sample.
                  </p>
                  <div className="clone-box-actions">
                    <button type="button" className="primary" onClick={useSavedClone} disabled={processing}>
                      Use saved voice
                    </button>
                    <button type="button" onClick={startNewSample} disabled={processing}>
                      Add new
                    </button>
                  </div>
                </>
              )}

              {clonePhase === "record" && !sampleRecording && (
                <>
                  <h4>{savedClone ? "Record a new sample" : "Save your voice first"}</h4>
                  <p>
                    Record at least {MIN_SAMPLE_SECONDS} seconds of clear speech. This sample is saved and reused — it is not the clip you just recorded.
                  </p>
                  <div className="clone-box-actions">
                    <button
                      type="button"
                      className="primary"
                      onClick={startSampleRecording}
                      disabled={processing}
                    >
                      Record sample
                    </button>
                    {savedClone && (
                      <button type="button" onClick={useSavedClone} disabled={processing}>
                        Use saved
                      </button>
                    )}
                  </div>
                </>
              )}

              {clonePhase === "record" && sampleRecording && (
                <>
                  <h4>Recording sample… {formatTime(sampleElapsed)}</h4>
                  <p>Speak clearly in a quiet place. Stop when you have at least {MIN_SAMPLE_SECONDS} seconds.</p>
                  <div className="clone-box-actions">
                    <button
                      type="button"
                      className="primary"
                      onClick={() => stopSampleRecording(true)}
                    >
                      <Square size={12} />
                      Stop
                    </button>
                  </div>
                </>
              )}

              {clonePhase === "saving" && (
                <>
                  <h4>Saving your voice…</h4>
                  <p>Creating a reusable voice from your sample.</p>
                </>
              )}

              {clonePhase === "ready" && savedClone && (
                <>
                  <h4>Voice ready</h4>
                  <p>{savedClone.name} will be used to translate this recording.</p>
                  <div className="clone-box-actions">
                    <button type="button" onClick={startNewSample} disabled={processing}>
                      Change voice
                    </button>
                  </div>
                </>
              )}
            </div>
          )}

          {error && (
            <div
              style={{
                padding: "8px 12px",
                borderRadius: 8,
                background: "rgba(239, 68, 68, 0.15)",
                color: "var(--danger)",
                fontSize: 13,
              }}
            >
              {error}
            </div>
          )}

          <button
            type="button"
            className="btn-primary"
            onClick={handleProcess}
            disabled={processing || !cloneReady}
          >
            {processing ? (
              <>
                <div className="shimmer" style={{ width: 20, height: 20, borderRadius: "50%" }} />
                Processing...
              </>
            ) : (
              <>
                <Sparkles size={18} />
                Process with AI
              </>
            )}
          </button>

          {processing && (
            <div className="shimmer" style={{ height: 48, borderRadius: 12 }} />
          )}
        </>
      ) : (
        <div className="fade-in" style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          <h3 style={{ fontSize: 15, fontWeight: 600, color: "var(--success)" }}>
            {showTextResult ? "Processing Complete" : "Audio ready"}
          </h3>

          {showTextResult && result.originalText && (
            <div className="glass-panel" style={{ padding: 12, borderRadius: 14 }}>
              <p style={{ fontSize: 11, color: "var(--text-muted)", marginBottom: 4 }}>
                Original
              </p>
              <p style={{ fontSize: 13, color: "var(--text-secondary)", lineHeight: 1.4 }}>
                {result.originalText}
              </p>
            </div>
          )}

          {showTextResult && result.translatedText && (
            <div className="glass-panel" style={{ padding: 12, borderRadius: 14 }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 4 }}>
                <p style={{ fontSize: 11, color: "var(--text-muted)" }}>Translated</p>
                <button
                  type="button"
                  onClick={() => handleCopy(result.translatedText)}
                  style={{
                    background: "none",
                    border: "none",
                    color: "var(--accent)",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    gap: 4,
                    fontSize: 11,
                  }}
                >
                  {copied ? <Check size={12} /> : <Copy size={12} />}
                  {copied ? "Copied" : "Copy"}
                </button>
              </div>
              <p style={{ fontSize: 14, color: "var(--text-primary)", lineHeight: 1.4 }}>
                {result.translatedText}
              </p>
            </div>
          )}

          {result.convertedAudioBase64 && (
            <div
              className="glass-panel"
              style={{
                display: "flex",
                alignItems: "center",
                gap: 8,
                padding: "8px 12px",
                borderRadius: 14,
              }}
            >
              <button
                type="button"
                className="icon-btn"
                onClick={toggleResultAudio}
                style={{ width: 36, height: 36, flexShrink: 0 }}
                title={resultPlaying ? "Pause result audio" : "Play result audio"}
              >
                {resultPlaying ? <Pause size={16} /> : <Play size={16} />}
              </button>
              <div style={{ flex: 1 }}>
                <div
                  style={{
                    height: 4,
                    borderRadius: 2,
                    background: "rgba(16, 185, 129, 0.2)",
                    overflow: "hidden",
                  }}
                >
                  <div
                    style={{
                      width: `${resultPct}%`,
                      height: "100%",
                      borderRadius: 2,
                      background: "var(--success)",
                      transition: resultPlaying ? "width 0.1s linear" : "none",
                    }}
                  />
                </div>
              </div>
              <span style={{ fontSize: 12, color: "var(--text-muted)", fontVariantNumeric: "tabular-nums" }}>
                {formatTime(resultPlaying ? resultProgress : resultDuration)}
              </span>
            </div>
          )}

          {showTextResult && result.translatedText && (
            <button
              type="button"
              className="btn-primary"
              onClick={() => handleInsertText(result.translatedText)}
            >
              <Send size={18} />
              Insert Text at Cursor
            </button>
          )}

          {result.ttsFallback && (
            <p style={{ fontSize: 11, color: "var(--warning)", textAlign: "center" }}>
              Voice generation failed, showing text result only
            </p>
          )}

          <button
            type="button"
            onClick={() => {
              stopPreview();
              stopResultAudio();
              onDone();
            }}
            style={{
              background: "none",
              border: "1px solid var(--border-glass)",
              borderRadius: 10,
              height: 40,
              color: "var(--text-secondary)",
              cursor: "pointer",
              fontSize: 13,
            }}
          >
            Done
          </button>
        </div>
      )}
    </div>
  );
}
