/* global DELTAVOICE_CONVEX_URL */
(function () {
  const LANGUAGES = [
    ["English", "en"],
    ["Spanish", "es"],
    ["French", "fr"],
    ["German", "de"],
    ["Italian", "it"],
    ["Portuguese", "pt"],
    ["Russian", "ru"],
    ["Japanese", "ja"],
    ["Korean", "ko"],
    ["Chinese", "zh"],
    ["Arabic", "ar"],
    ["Hindi", "hi"],
  ];
  const VOICES = [
    ["Adam", "adam"],
    ["Aria", "aria"],
    ["Sarah", "sarah"],
    ["Liam", "liam"],
    ["Charlotte", "charlotte"],
    ["Alice", "alice"],
    ["Roger", "roger"],
    ["Laura", "laura"],
  ];

  const el = (id) => document.getElementById(id);
  const baseUrl = () =>
    (typeof window !== "undefined" && window.DELTAVOICE_CONVEX_URL) ||
    "https://kindred-curlew-363.eu-west-1.convex.site";

  let workflowType = "full";
  let audioBlob = null;
  let audioFormat = "webm";
  let mediaRecorder = null;
  let recordChunks = [];
  let isRecording = false;
  let recordTimer = null;
  let recordStart = 0;
  let audioEl = null;
  let processedItems = [];
  let resultPreviewAudio = null;
  let resultPreviewUrl = null;
  let resultPreviewIndex = -1;

  function fillSelect(select, pairs) {
    select.innerHTML = "";
    pairs.forEach(([label, val]) => {
      const o = document.createElement("option");
      o.value = val;
      o.textContent = label;
      select.appendChild(o);
    });
  }

  function setMode(mode) {
    workflowType = mode;
    document.querySelectorAll(".vp-card").forEach((c) => {
      c.classList.toggle("selected", c.dataset.mode === mode);
    });
  }

  function showRecordingSection(show) {
    el("recordingSection").classList.toggle("visible", show);
  }

  function formatTime(sec) {
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return m + ":" + (s < 10 ? "0" : "") + s;
  }

  function drawWavePlaceholder(canvas, phase) {
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    const w = canvas.width;
    const h = canvas.height;
    ctx.clearRect(0, 0, w, h);
    ctx.fillStyle = "rgba(139, 92, 246, 0.85)";
    const bars = 24;
    const gap = 3;
    const bw = (w - gap * (bars - 1)) / bars;
    for (let i = 0; i < bars; i++) {
      const t = Math.sin(phase + i * 0.35) * 0.5 + 0.5;
      const bh = Math.max(4, t * h * 0.9);
      ctx.fillRect(i * (bw + gap), h - bh, bw, bh);
    }
  }

  let waveAnim = null;
  function startWaveAnim() {
    const canvas = el("waveCanvas");
    if (!canvas) return;
    canvas.classList.add("visible");
    el("statusText").classList.add("vp-hidden");
    let p = 0;
    waveAnim = setInterval(() => {
      p += 0.15;
      drawWavePlaceholder(canvas, p);
    }, 64);
  }
  function stopWaveAnim() {
    if (waveAnim) clearInterval(waveAnim);
    waveAnim = null;
    const canvas = el("waveCanvas");
    if (canvas) {
      canvas.classList.remove("visible");
      const ctx = canvas.getContext("2d");
      ctx.clearRect(0, 0, canvas.width, canvas.height);
    }
    el("statusText").classList.remove("vp-hidden");
  }

  function updateTimer() {
    if (!isRecording) return;
    const elapsed = (Date.now() - recordStart) / 1000;
    el("durationText").textContent = formatTime(elapsed);
    recordTimer = requestAnimationFrame(updateTimer);
  }
  function startTimerLoop() {
    cancelAnimationFrame(recordTimer);
    recordTimer = requestAnimationFrame(updateTimer);
  }

  async function startRecording() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      recordChunks = [];
      const mime = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
        ? "audio/webm;codecs=opus"
        : "audio/webm";
      mediaRecorder = new MediaRecorder(stream, { mimeType: mime });
      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size) recordChunks.push(e.data);
      };
      mediaRecorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop());
        audioBlob = new Blob(recordChunks, { type: mime });
        audioFormat = "webm";
        showRecordingSection(true);
        el("statusText").textContent = "Recording ready";
        stopWaveAnim();
        el("playBtn").disabled = false;
        el("processBtn").disabled = false;
        if (audioEl) {
          URL.revokeObjectURL(audioEl.src);
          audioEl = null;
        }
      };
      mediaRecorder.start(100);
      isRecording = true;
      showRecordingSection(true);
      el("statusText").textContent = "";
      el("statusText").classList.add("vp-hidden");
      el("waveCanvas").classList.add("visible");
      startWaveAnim();
      el("recordBtn").textContent = "Stop recording";
      el("playBtn").disabled = true;
      el("processBtn").disabled = true;
      recordStart = Date.now();
      el("durationText").textContent = "0:00";
      startTimerLoop();
    } catch (e) {
      alert("Microphone access denied or unavailable: " + (e && e.message));
    }
  }

  function stopRecording() {
    if (mediaRecorder && isRecording) {
      isRecording = false;
      if (recordTimer) cancelAnimationFrame(recordTimer);
      recordTimer = null;
      mediaRecorder.stop();
      mediaRecorder = null;
      el("recordBtn").textContent = "Record";
      stopWaveAnim();
      el("statusText").classList.remove("vp-hidden");
    }
  }

  function togglePlay() {
    if (!audioBlob) return;
    if (!audioEl) {
      audioEl = new Audio(URL.createObjectURL(audioBlob));
      audioEl.onended = () => {
        el("playBtn").textContent = "▶";
      };
    }
    if (audioEl.paused) {
      audioEl.play();
      el("playBtn").textContent = "❚❚";
    } else {
      audioEl.pause();
      el("playBtn").textContent = "▶";
    }
  }

  const ICON_PLAY =
    '<svg class="vp-icon-svg" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M8 5v14l11-7z"/></svg>';
  const ICON_PAUSE =
    '<svg class="vp-icon-svg" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>';
  const ICON_EDIT =
    '<svg class="vp-icon-svg" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1.003 1.003 0 0 0 0-1.41l-2.34-2.34a1.003 1.003 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>';
  const ICON_DOWNLOAD =
    '<svg class="vp-icon-svg" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>';
  const ICON_SHARE =
    '<svg class="vp-icon-svg" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z"/></svg>';

  function safeFileBase(name) {
    const s = (name || "deltavoice")
      .replace(/[^\w\-\s\u00C0-\u024F]+/g, "")
      .replace(/\s+/g, "_")
      .trim()
      .slice(0, 80);
    return s || "deltavoice";
  }

  function setVoicePreviewButtonIcon(idx, playing) {
    const btn = el("resultsList").querySelector('[data-preview-index="' + idx + '"]');
    if (!btn) return;
    btn.innerHTML = playing ? ICON_PAUSE : ICON_PLAY;
    btn.setAttribute("aria-pressed", playing ? "true" : "false");
  }

  function stopResultPreview() {
    const oldIdx = resultPreviewIndex;
    if (resultPreviewAudio) {
      resultPreviewAudio.pause();
      resultPreviewAudio.onended = null;
      resultPreviewAudio = null;
    }
    if (resultPreviewUrl) {
      URL.revokeObjectURL(resultPreviewUrl);
      resultPreviewUrl = null;
    }
    resultPreviewIndex = -1;
    if (oldIdx >= 0) setVoicePreviewButtonIcon(oldIdx, false);
  }

  function bindResultPreview(btn, idx) {
    btn.addEventListener("click", function () {
      const it = processedItems[idx];
      if (!it || !it.blob) return;
      if (resultPreviewIndex === idx && resultPreviewAudio && !resultPreviewAudio.paused) {
        resultPreviewAudio.pause();
        setVoicePreviewButtonIcon(idx, false);
        return;
      }
      if (resultPreviewIndex === idx && resultPreviewAudio && resultPreviewAudio.paused) {
        resultPreviewAudio.play();
        setVoicePreviewButtonIcon(idx, true);
        return;
      }
      stopResultPreview();
      resultPreviewUrl = URL.createObjectURL(it.blob);
      resultPreviewAudio = new Audio(resultPreviewUrl);
      resultPreviewIndex = idx;
      resultPreviewAudio.onended = function () {
        setVoicePreviewButtonIcon(idx, false);
        resultPreviewAudio = null;
        if (resultPreviewUrl) {
          URL.revokeObjectURL(resultPreviewUrl);
          resultPreviewUrl = null;
        }
        resultPreviewIndex = -1;
      };
      resultPreviewAudio.play();
      setVoicePreviewButtonIcon(idx, true);
    });
  }

  function fileToBase64(file) {
    return new Promise((resolve, reject) => {
      const r = new FileReader();
      r.onload = () => {
        const s = r.result;
        const i = s.indexOf("base64,");
        resolve(i >= 0 ? s.slice(i + 7) : s);
      };
      r.onerror = reject;
      r.readAsDataURL(file);
    });
  }

  async function processVoice() {
    let base64;
    let format = audioFormat;
    if (audioBlob) {
      if (audioBlob instanceof File) {
        base64 = await fileToBase64(audioBlob);
        format = (audioBlob.name.split(".").pop() || "webm").toLowerCase();
      } else {
        const ab = await audioBlob.arrayBuffer();
        const bytes = new Uint8Array(ab);
        let bin = "";
        for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
        base64 = btoa(bin);
      }
    } else {
      const input = el("fileInput");
      if (!input.files || !input.files[0]) {
        alert("Record or upload audio first.");
        return;
      }
      const f = input.files[0];
      base64 = await fileToBase64(f);
      format = (f.name.split(".").pop() || "webm").toLowerCase();
    }

    const lang = el("langSelect").value;
    const voice = el("voiceSelect").value;
    const wf =
      workflowType === "full"
        ? "complete"
        : workflowType === "voice_only"
          ? "voice-only"
          : "text-only";

    el("processBtn").disabled = true;
    el("statusText").textContent = "Processing…";

    try {
      await window.DeltaVoiceAuth.ensureSignedIn();
      const authHeaders = window.DeltaVoiceAuth.authHeaders();
      const res = await fetch(baseUrl() + "/complete-voice-workflow", {
        method: "POST",
        headers: { "Content-Type": "application/json", ...authHeaders },
        body: JSON.stringify({
          audioBase64: base64,
          targetLanguage: lang,
          voiceStyle: voice,
          workflowType: wf,
          format,
        }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.error || data.details || res.statusText);
      }

      if (wf === "text-only") {
        const text = data.translatedText || data.originalText || "";
        el("textOutput").classList.remove("vp-hidden");
        el("textOutput").value = text;
        try {
          await navigator.clipboard.writeText(text);
        } catch (_) {}
        el("statusText").textContent = "Text copied to clipboard.";
        processedItems.push({ type: "text", label: "Transcript", text });
      } else {
        const b64 = data.convertedAudioBase64;
        if (b64) {
          const bin = atob(b64);
          const arr = new Uint8Array(bin.length);
          for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
          const mp3 = new Blob([arr], { type: "audio/mpeg" });
          const label = VOICES.find((v) => v[1] === voice)?.[0] + " #" + (processedItems.length + 1);
          processedItems.push({ type: "audio", label, blob: mp3 });
          renderResults();
          el("statusText").textContent = "Ready! Download or share below.";
        } else if (data.ttsFallback && (data.translatedText || data.originalText)) {
          el("textOutput").classList.remove("vp-hidden");
          el("textOutput").value = data.translatedText || data.originalText;
          el("statusText").textContent = "TTS unavailable; text shown.";
        } else {
          throw new Error("No audio in response");
        }
      }
    } catch (err) {
      alert(err.message || String(err));
      el("statusText").textContent = "Recording ready";
    } finally {
      el("processBtn").disabled = false;
    }
  }

  function renderResults() {
    stopResultPreview();
    const list = el("resultsList");
    list.innerHTML = "";
    processedItems.forEach((item, idx) => {
      if (item.type !== "audio") return;
      const row = document.createElement("div");
      row.className = "vp-result-row";
      const label = document.createElement("span");
      label.className = "vp-result-row__label";
      label.textContent = item.label;
      const actions = document.createElement("div");
      actions.className = "vp-result-row__actions";
      function mkBtn(cls, attr, val, inner, ariaLbl) {
        const b = document.createElement("button");
        b.type = "button";
        b.className = cls;
        b.setAttribute(attr, val);
        b.setAttribute("aria-label", ariaLbl);
        b.title = ariaLbl;
        b.innerHTML = inner;
        return b;
      }
      actions.appendChild(
        mkBtn("vp-icon-btn vp-icon-btn--ghost", "data-preview-index", String(idx), ICON_PLAY, "Preview")
      );
      actions.appendChild(
        mkBtn("vp-icon-btn vp-icon-btn--ghost", "data-rename-index", String(idx), ICON_EDIT, "Rename")
      );
      actions.appendChild(
        mkBtn("vp-icon-btn vp-icon-btn--ghost", "data-dl-index", String(idx), ICON_DOWNLOAD, "Download")
      );
      actions.appendChild(
        mkBtn("vp-icon-btn vp-icon-btn--ghost", "data-share-index", String(idx), ICON_SHARE, "Share")
      );
      row.appendChild(label);
      row.appendChild(actions);
      list.appendChild(row);
    });
    list.querySelectorAll("[data-preview-index]").forEach(function (btn) {
      bindResultPreview(btn, parseInt(btn.getAttribute("data-preview-index"), 10));
    });
    list.querySelectorAll("[data-rename-index]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        const i = parseInt(btn.getAttribute("data-rename-index"), 10);
        const it = processedItems[i];
        if (!it || it.type !== "audio") return;
        const next = window.prompt("Name for this clip", it.label);
        if (next === null) return;
        const trimmed = next.trim();
        if (!trimmed) return;
        it.label = trimmed;
        renderResults();
      });
    });
    list.querySelectorAll("[data-dl-index]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        const i = parseInt(btn.getAttribute("data-dl-index"), 10);
        const it = processedItems[i];
        if (!it || !it.blob) return;
        const base = safeFileBase(it.label);
        const a = document.createElement("a");
        a.href = URL.createObjectURL(it.blob);
        a.download = base + ".mp3";
        a.click();
        URL.revokeObjectURL(a.href);
      });
    });
    list.querySelectorAll("[data-share-index]").forEach(function (btn) {
      btn.addEventListener("click", async function () {
        const i = parseInt(btn.getAttribute("data-share-index"), 10);
        const it = processedItems[i];
        if (!it || !it.blob) return;
        const base = safeFileBase(it.label);
        const file = new File([it.blob], base + ".mp3", { type: "audio/mpeg" });
        if (navigator.share && navigator.canShare && navigator.canShare({ files: [file] })) {
          try {
            await navigator.share({ files: [file], title: it.label });
          } catch (e) {
            if (e.name !== "AbortError") alert(e.message);
          }
        } else {
          alert("Sharing not supported; use Download.");
        }
      });
    });
    el("resultsSection").classList.remove("vp-hidden");
  }

  function onUpload(e) {
    const f = e.target.files && e.target.files[0];
    if (!f) return;
    audioBlob = f;
    audioFormat = (f.name.split(".").pop() || "webm").toLowerCase();
    showRecordingSection(true);
    el("statusText").textContent = "Audio uploaded";
    el("statusText").classList.remove("vp-hidden");
    stopWaveAnim();
    el("playBtn").disabled = false;
    el("processBtn").disabled = false;
    if (audioEl) {
      URL.revokeObjectURL(audioEl.src);
      audioEl = null;
    }
  }

  function init() {
    fillSelect(el("langSelect"), LANGUAGES);
    fillSelect(el("voiceSelect"), VOICES);

    document.querySelectorAll(".vp-card").forEach((c) => {
      c.addEventListener("click", () => setMode(c.dataset.mode));
    });
    setMode("full");

    el("uploadBtn").addEventListener("click", () => el("fileInput").click());
    el("fileInput").addEventListener("change", onUpload);

    el("recordBtn").addEventListener("click", () => {
      if (isRecording) stopRecording();
      else startRecording();
    });
    el("playBtn").addEventListener("click", togglePlay);
    el("processBtn").addEventListener("click", processVoice);

    const canvas = el("waveCanvas");
    if (canvas) {
      const rect = canvas.parentElement.getBoundingClientRect();
      canvas.width = Math.min(400, rect.width || 320);
      canvas.height = 36;
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
