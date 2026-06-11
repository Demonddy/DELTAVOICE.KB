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

  let videoBlob = null;
  let videoFormat = "mp4";
  let mediaRecorder = null;
  let recordChunks = [];
  let isRecording = false;
  let previewUrl = null;
  let processedPreviewUrl = null;
  const processedItems = [];

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

  let modalEl = null;
  let modalVideoUrl = null;
  let resultPreviewAudio = null;
  let resultPreviewUrl = null;
  let resultPreviewIndex = -1;

  function safeFileBase(name) {
    const s = (name || "deltavoice")
      .replace(/[^\w\-\s\u00C0-\u024F]+/g, "")
      .replace(/\s+/g, "_")
      .trim()
      .slice(0, 80);
    return s || "deltavoice";
  }

  function closeVideoPreviewModal() {
    if (!modalEl) return;
    modalEl.classList.remove("visible");
    const vid = modalEl.querySelector("video");
    if (vid) {
      vid.pause();
      vid.removeAttribute("src");
    }
    if (modalVideoUrl) {
      URL.revokeObjectURL(modalVideoUrl);
      modalVideoUrl = null;
    }
  }

  function ensureVideoPreviewModal() {
    if (modalEl) return modalEl;
    const backdrop = document.createElement("div");
    backdrop.className = "vp-media-modal";
    backdrop.setAttribute("role", "dialog");
    backdrop.setAttribute("aria-modal", "true");
    backdrop.setAttribute("aria-label", "Video preview");
    const panel = document.createElement("div");
    panel.className = "vp-media-modal__panel";
    const closeBtn = document.createElement("button");
    closeBtn.type = "button";
    closeBtn.className = "vp-media-modal__close";
    closeBtn.setAttribute("aria-label", "Close");
    closeBtn.innerHTML = "&times;";
    const vid = document.createElement("video");
    vid.className = "vp-media-modal__video";
    vid.controls = true;
    vid.playsInline = true;
    panel.appendChild(closeBtn);
    panel.appendChild(vid);
    backdrop.appendChild(panel);
    document.body.appendChild(backdrop);
    closeBtn.addEventListener("click", closeVideoPreviewModal);
    backdrop.addEventListener("click", function (e) {
      if (e.target === backdrop) closeVideoPreviewModal();
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && backdrop.classList.contains("visible")) closeVideoPreviewModal();
    });
    modalEl = backdrop;
    return modalEl;
  }

  function openProcessedVideoModal(blob) {
    const backdrop = ensureVideoPreviewModal();
    const vid = backdrop.querySelector("video");
    if (modalVideoUrl) {
      URL.revokeObjectURL(modalVideoUrl);
      modalVideoUrl = null;
    }
    modalVideoUrl = URL.createObjectURL(blob);
    vid.src = modalVideoUrl;
    backdrop.classList.add("visible");
    vid.play().catch(function () {});
  }

  function setVideoPagePreviewButtonIcon(idx, playing) {
    const btn = el("resultsList").querySelector('[data-preview-index="' + idx + '"]');
    if (!btn) return;
    const item = processedItems[idx];
    if (item && item.type === "video") {
      btn.innerHTML = ICON_PLAY;
      btn.setAttribute("aria-pressed", "false");
      return;
    }
    btn.innerHTML = playing ? ICON_PAUSE : ICON_PLAY;
    btn.setAttribute("aria-pressed", playing ? "true" : "false");
  }

  function stopResultAudioPreview() {
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
    if (oldIdx >= 0) setVideoPagePreviewButtonIcon(oldIdx, false);
  }

  function bindAudioResultPreview(btn, idx) {
    btn.addEventListener("click", function () {
      const it = processedItems[idx];
      if (!it || !it.blob || it.type !== "audio") return;
      if (resultPreviewIndex === idx && resultPreviewAudio && !resultPreviewAudio.paused) {
        resultPreviewAudio.pause();
        setVideoPagePreviewButtonIcon(idx, false);
        return;
      }
      if (resultPreviewIndex === idx && resultPreviewAudio && resultPreviewAudio.paused) {
        resultPreviewAudio.play();
        setVideoPagePreviewButtonIcon(idx, true);
        return;
      }
      stopResultAudioPreview();
      resultPreviewUrl = URL.createObjectURL(it.blob);
      resultPreviewAudio = new Audio(resultPreviewUrl);
      resultPreviewIndex = idx;
      resultPreviewAudio.onended = function () {
        setVideoPagePreviewButtonIcon(idx, false);
        resultPreviewAudio = null;
        if (resultPreviewUrl) {
          URL.revokeObjectURL(resultPreviewUrl);
          resultPreviewUrl = null;
        }
        resultPreviewIndex = -1;
      };
      resultPreviewAudio.play();
      setVideoPagePreviewButtonIcon(idx, true);
    });
  }

  function fillSelect(select, pairs) {
    select.innerHTML = "";
    pairs.forEach(([label, val]) => {
      const o = document.createElement("option");
      o.value = val;
      o.textContent = label;
      select.appendChild(o);
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

  function setVideoSource(blob, format) {
    const v = el("videoPreview");
    v.srcObject = null;
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    if (processedPreviewUrl) {
      URL.revokeObjectURL(processedPreviewUrl);
      processedPreviewUrl = null;
    }
    videoBlob = blob;
    videoFormat = format || "webm";
    previewUrl = URL.createObjectURL(blob);
    v.src = previewUrl;
    el("previewWrap").classList.add("visible");
    el("recordingSection").classList.add("visible");
    el("processBtn").disabled = false;
    el("videoStatus").textContent = "Video ready for processing";
  }

  function attachLiveCameraPreview(stream) {
    const v = el("videoPreview");
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = null;
    if (processedPreviewUrl) {
      URL.revokeObjectURL(processedPreviewUrl);
      processedPreviewUrl = null;
    }
    v.removeAttribute("src");
    v.srcObject = stream;
    v.muted = true;
    v.playsInline = true;
    el("previewWrap").classList.add("visible");
    el("recordingSection").classList.add("visible");
    v.play().catch(function () {});
  }

  function detachLiveCameraPreview() {
    const v = el("videoPreview");
    v.pause();
    v.srcObject = null;
  }

  function onFileSelected(file) {
    if (!file || !file.type.startsWith("video/")) {
      alert("Please choose a video file.");
      return;
    }
    const ext = (file.name.split(".").pop() || "mp4").toLowerCase();
    videoFormat = ext.replace(/[^a-z0-9]/g, "") || "mp4";
    setVideoSource(file, videoFormat);
  }

  async function startVideoRecord() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "user" },
        audio: true,
      });
      attachLiveCameraPreview(stream);
      recordChunks = [];
      const mime = MediaRecorder.isTypeSupported("video/webm;codecs=vp9,opus")
        ? "video/webm;codecs=vp9,opus"
        : MediaRecorder.isTypeSupported("video/webm")
          ? "video/webm"
          : "video/mp4";
      const baseMime = mime.split(";")[0];
      mediaRecorder = new MediaRecorder(stream, { mimeType: mime });
      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size) recordChunks.push(e.data);
      };
      mediaRecorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop());
        detachLiveCameraPreview();
        const blob = new Blob(recordChunks, { type: baseMime });
        const fmt = baseMime.indexOf("webm") >= 0 ? "webm" : "mp4";
        setVideoSource(blob, fmt);
        el("recordBtn").textContent = "Record Video";
        isRecording = false;
      };
      mediaRecorder.start(200);
      isRecording = true;
      el("processBtn").disabled = true;
      el("recordBtn").textContent = "Stop recording";
      el("videoStatus").textContent = "Recording… tap Stop when done.";
      el("recordingSection").classList.add("visible");
    } catch (e) {
      alert("Camera/mic not available: " + (e && e.message));
    }
  }

  function stopVideoRecord() {
    if (mediaRecorder && isRecording) {
      mediaRecorder.stop();
      mediaRecorder = null;
    }
  }

  async function processVideo() {
    if (!videoBlob) {
      alert("Select or record a video first.");
      return;
    }

    const lang = el("langSelect").value;
    const voice = el("voiceSelect").value;
    el("processBtn").disabled = true;
    el("videoStatus").textContent = "Processing…";

    let videoBase64;
    if (videoBlob instanceof File) {
      videoBase64 = await fileToBase64(videoBlob);
    } else {
      const ab = await videoBlob.arrayBuffer();
      const bytes = new Uint8Array(ab);
      let bin = "";
      for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
      videoBase64 = btoa(bin);
    }

    try {
      await window.DeltaVoiceAuth.ensureSignedIn();
      const authHeaders = window.DeltaVoiceAuth.authHeaders();
      const res = await fetch(baseUrl() + "/video-workflow", {
        method: "POST",
        headers: { "Content-Type": "application/json", ...authHeaders },
        body: JSON.stringify({
          videoBase64,
          targetLanguage: lang,
          voiceStyle: voice,
          videoFormat,
        }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.error || data.details || res.statusText);
      }

      if (data.convertedVideoBase64) {
        const bin = atob(data.convertedVideoBase64);
        const arr = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
        const outBlob = new Blob([arr], { type: "video/mp4" });
        const label =
          (VOICES.find((v) => v[1] === voice)?.[0] || "Video") +
          " #" +
          (processedItems.length + 1);
        processedItems.push({ type: "video", label, blob: outBlob });
        el("videoStatus").textContent = "Ready! Download or share below.";
        const v = el("videoPreview");
        v.srcObject = null;
        if (processedPreviewUrl) URL.revokeObjectURL(processedPreviewUrl);
        processedPreviewUrl = URL.createObjectURL(outBlob);
        v.src = processedPreviewUrl;
        v.muted = false;
      } else if (data.convertedAudioBase64) {
        const bin = atob(data.convertedAudioBase64);
        const arr = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
        const outBlob = new Blob([arr], { type: "audio/mpeg" });
        processedItems.push({
          type: "audio",
          label: "Processed audio (mux skipped)",
          blob: outBlob,
        });
        el("videoStatus").textContent =
          data.muxError || "Video mux unavailable; audio only below.";
      } else {
        throw new Error("No output from server");
      }

      renderResults();
    } catch (err) {
      alert(err.message || String(err));
      el("videoStatus").textContent = "Video ready for processing";
    } finally {
      el("processBtn").disabled = false;
    }
  }

  function renderResults() {
    stopResultAudioPreview();
    const list = el("resultsList");
    list.innerHTML = "";
    processedItems.forEach((item, idx) => {
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
      const previewBtn = mkBtn(
        "vp-icon-btn vp-icon-btn--ghost",
        "data-preview-index",
        String(idx),
        ICON_PLAY,
        "Preview"
      );
      actions.appendChild(previewBtn);
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
      if (item.type === "video") {
        previewBtn.addEventListener("click", function () {
          if (item.blob) openProcessedVideoModal(item.blob);
        });
      } else {
        bindAudioResultPreview(previewBtn, idx);
      }
    });
    list.querySelectorAll("[data-rename-index]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        const i = parseInt(btn.getAttribute("data-rename-index"), 10);
        const it = processedItems[i];
        if (!it) return;
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
        if (!it?.blob) return;
        const ext = it.type === "video" ? "mp4" : "mp3";
        const base = safeFileBase(it.label);
        const a = document.createElement("a");
        a.href = URL.createObjectURL(it.blob);
        a.download = base + "." + ext;
        a.click();
        URL.revokeObjectURL(a.href);
      });
    });
    list.querySelectorAll("[data-share-index]").forEach(function (btn) {
      btn.addEventListener("click", async function () {
        const i = parseInt(btn.getAttribute("data-share-index"), 10);
        const it = processedItems[i];
        if (!it?.blob) return;
        const ext = it.type === "video" ? "mp4" : "mp3";
        const mime = it.type === "video" ? "video/mp4" : "audio/mpeg";
        const base = safeFileBase(it.label);
        const file = new File([it.blob], base + "." + ext, {
          type: mime,
        });
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

  function init() {
    fillSelect(el("langSelect"), LANGUAGES);
    fillSelect(el("voiceSelect"), VOICES);

    el("uploadBtn").addEventListener("click", () => el("fileInput").click());
    el("fileInput").addEventListener("change", (e) => {
      const f = e.target.files && e.target.files[0];
      if (f) onFileSelected(f);
    });

    el("recordBtn").addEventListener("click", () => {
      if (isRecording) stopVideoRecord();
      else startVideoRecord();
    });

    el("processBtn").addEventListener("click", processVideo);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
