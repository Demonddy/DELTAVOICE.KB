import { useState, useRef, useEffect, useCallback } from "react";
import { Pause, Play, Trash2, Check } from "lucide-react";

interface Props {
  autoStart?: boolean;
  stopSignal?: number;
  onRecorded: (blob: Blob) => void;
  onCancel: () => void;
}

export default function RecordingBar({
  autoStart = false,
  stopSignal = 0,
  onRecorded,
  onCancel,
}: Props) {
  const [isRecording, setIsRecording] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [waveHeights, setWaveHeights] = useState<number[]>(() =>
    Array.from({ length: 24 }, () => 4)
  );

  const mediaRecRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const timerRef = useRef<number | null>(null);
  const saveOnStopRef = useRef(false);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const animRef = useRef<number | null>(null);

  const cleanupAudio = useCallback(() => {
    if (animRef.current) cancelAnimationFrame(animRef.current);
    animRef.current = null;
    analyserRef.current = null;
    if (audioCtxRef.current) {
      audioCtxRef.current.close().catch(() => {});
      audioCtxRef.current = null;
    }
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
  }, []);

  const startWaveform = useCallback((stream: MediaStream) => {
    const ctx = new AudioContext();
    const analyser = ctx.createAnalyser();
    analyser.fftSize = 64;
    analyser.smoothingTimeConstant = 0.75;
    const source = ctx.createMediaStreamSource(stream);
    source.connect(analyser);
    audioCtxRef.current = ctx;
    analyserRef.current = analyser;

    const data = new Uint8Array(analyser.frequencyBinCount);
    const tick = () => {
      if (!analyserRef.current) return;
      analyserRef.current.getByteFrequencyData(data);
      const bars = 24;
      const step = Math.floor(data.length / bars);
      const heights = Array.from({ length: bars }, (_, i) => {
        const slice = data.slice(i * step, (i + 1) * step);
        const avg = slice.reduce((a, b) => a + b, 0) / slice.length;
        return Math.max(4, Math.min(28, (avg / 255) * 28));
      });
      setWaveHeights(heights);
      animRef.current = requestAnimationFrame(tick);
    };
    animRef.current = requestAnimationFrame(tick);
  }, []);

  const startRecording = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;
      startWaveform(stream);

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
        cleanupAudio();
        if (!saveOnStopRef.current) return;
        const blob = new Blob(chunksRef.current, { type: "audio/webm" });
        if (blob.size > 0) onRecorded(blob);
      };
      recorder.start(250);
      mediaRecRef.current = recorder;
      saveOnStopRef.current = false;
      setIsRecording(true);
      setIsPaused(false);
      setElapsed(0);
      timerRef.current = window.setInterval(
        () => setElapsed((s) => s + 1),
        1000
      );
    } catch {
      alert("Microphone access denied. Please allow microphone access.");
      onCancel();
    }
  }, [cleanupAudio, onCancel, onRecorded, startWaveform]);

  const stopRecording = useCallback(
    (save = true) => {
      saveOnStopRef.current = save;
      const rec = mediaRecRef.current;
      if (rec && (rec.state === "recording" || rec.state === "paused")) {
        rec.stop();
      }
      if (timerRef.current) clearInterval(timerRef.current);
      timerRef.current = null;
      mediaRecRef.current = null;
      setIsRecording(false);
      setIsPaused(false);
      if (!save) cleanupAudio();
    },
    [cleanupAudio]
  );

  const pauseRecording = useCallback(() => {
    const rec = mediaRecRef.current;
    if (rec?.state === "recording" && typeof rec.pause === "function") {
      rec.pause();
      setIsPaused(true);
      if (timerRef.current) clearInterval(timerRef.current);
      if (animRef.current) cancelAnimationFrame(animRef.current);
    }
  }, []);

  const resumeRecording = useCallback(() => {
    const rec = mediaRecRef.current;
    if (rec?.state === "paused" && typeof rec.resume === "function") {
      rec.resume();
      setIsPaused(false);
      timerRef.current = window.setInterval(
        () => setElapsed((s) => s + 1),
        1000
      );
      if (analyserRef.current) {
        const data = new Uint8Array(analyserRef.current.frequencyBinCount);
        const tick = () => {
          if (!analyserRef.current) return;
          analyserRef.current.getByteFrequencyData(data);
          const bars = 24;
          const step = Math.floor(data.length / bars);
          const heights = Array.from({ length: bars }, (_, i) => {
            const slice = data.slice(i * step, (i + 1) * step);
            const avg = slice.reduce((a, b) => a + b, 0) / slice.length;
            return Math.max(4, Math.min(28, (avg / 255) * 28));
          });
          setWaveHeights(heights);
          animRef.current = requestAnimationFrame(tick);
        };
        animRef.current = requestAnimationFrame(tick);
      }
    }
  }, []);

  const deleteRecording = useCallback(() => {
    stopRecording(false);
    onCancel();
  }, [onCancel, stopRecording]);

  const finishRecording = useCallback(() => {
    stopRecording(true);
  }, [stopRecording]);

  useEffect(() => {
    if (autoStart) startRecording();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoStart]);

  useEffect(() => {
    if (stopSignal > 0 && isRecording) finishRecording();
  }, [stopSignal, isRecording, finishRecording]);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
      const rec = mediaRecRef.current;
      if (rec && (rec.state === "recording" || rec.state === "paused")) {
        saveOnStopRef.current = false;
        rec.stop();
      }
      cleanupAudio();
    };
  }, [cleanupAudio]);

  const formatTime = (s: number) => {
    const m = Math.floor(s / 60);
    const sec = s % 60;
    return `${m}:${sec.toString().padStart(2, "0")}`;
  };

  return (
    <div className="recording-bar fade-in">
      <div className="recording-bar-inner glass-panel">
        <div className="recording-bar-wave">
          {waveHeights.map((h, i) => (
            <span
              key={i}
              className={`recording-bar-bar${isPaused ? " paused" : ""}`}
              style={{ height: isPaused ? 4 : h }}
            />
          ))}
        </div>

        <span className="recording-bar-time">{formatTime(elapsed)}</span>

        <div className="recording-bar-actions">
          {isPaused ? (
            <button
              type="button"
              className="recording-bar-btn"
              onClick={resumeRecording}
              title="Resume"
            >
              <Play size={16} fill="currentColor" />
            </button>
          ) : (
            <button
              type="button"
              className="recording-bar-btn"
              onClick={pauseRecording}
              disabled={!isRecording}
              title="Pause"
            >
              <Pause size={16} />
            </button>
          )}

          <button
            type="button"
            className="recording-bar-btn danger"
            onClick={deleteRecording}
            title="Delete recording"
          >
            <Trash2 size={16} />
          </button>

          <button
            type="button"
            className="recording-bar-btn done"
            onClick={finishRecording}
            title="Finish & process"
          >
            <Check size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}
