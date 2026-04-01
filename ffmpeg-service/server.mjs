/**
 * FFmpeg microservice for DeltaVoice: extract audio from video, mux processed audio back.
 * Deploy with Docker; set FFMPEG_SERVICE_API_KEY and pass URL to Convex as FFMPEG_VIDEO_SERVICE_URL.
 */
import express from "express";
import { spawn } from "child_process";
import fs from "fs";
import os from "os";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
app.use(express.json({ limit: "512mb" }));

const PORT = process.env.PORT || 8790;
const API_KEY = process.env.FFMPEG_SERVICE_API_KEY || "";

function runFfmpeg(args) {
  return new Promise((resolve, reject) => {
    const p = spawn("ffmpeg", args, { stdio: ["ignore", "pipe", "pipe"] });
    let err = "";
    p.stderr.on("data", (d) => {
      err += d.toString();
    });
    p.on("error", reject);
    p.on("close", (code) => {
      if (code === 0) resolve();
      else reject(new Error(err || `ffmpeg exited ${code}`));
    });
  });
}

function auth(req, res, next) {
  if (!API_KEY) return next();
  const h = req.headers.authorization || "";
  const token = h.startsWith("Bearer ") ? h.slice(7) : "";
  if (token !== API_KEY) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  next();
}

app.get("/health", (_req, res) => {
  res.json({ ok: true });
});

app.post("/extract-audio", auth, async (req, res) => {
  const { videoBase64, format } = req.body || {};
  if (!videoBase64) {
    return res.status(400).json({ error: "videoBase64 required" });
  }
  const ext = (format || "mp4").toString().replace(/[^a-z0-9]/gi, "") || "mp4";
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "dv-"));
  const inPath = path.join(tmp, `in.${ext}`);
  const outPath = path.join(tmp, "out.m4a");
  try {
    fs.writeFileSync(inPath, Buffer.from(videoBase64, "base64"));
    await runFfmpeg([
      "-y",
      "-i",
      inPath,
      "-vn",
      "-acodec",
      "aac",
      "-b:a",
      "128k",
      outPath,
    ]);
    const audioBuf = fs.readFileSync(outPath);
    res.json({
      audioBase64: audioBuf.toString("base64"),
      format: "m4a",
    });
  } catch (e) {
    res.status(500).json({
      error: e instanceof Error ? e.message : String(e),
    });
  } finally {
    try {
      fs.rmSync(tmp, { recursive: true, force: true });
    } catch (_) {}
  }
});

app.post("/mux", auth, async (req, res) => {
  const { videoBase64, processedAudioBase64, videoFormat } = req.body || {};
  if (!videoBase64 || !processedAudioBase64) {
    return res.status(400).json({
      error: "videoBase64 and processedAudioBase64 required",
    });
  }
  const ext = (videoFormat || "mp4").toString().replace(/[^a-z0-9]/gi, "") || "mp4";
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "dv-mux-"));
  const videoPath = path.join(tmp, `v.${ext}`);
  const audioPath = path.join(tmp, "processed.mp3");
  const outPath = path.join(tmp, "out.mp4");
  try {
    fs.writeFileSync(videoPath, Buffer.from(videoBase64, "base64"));
    fs.writeFileSync(audioPath, Buffer.from(processedAudioBase64, "base64"));
    // WebM/VP9 → MP4 cannot use -c:v copy; re-encode to H.264 + AAC for a playable MP4.
    await runFfmpeg([
      "-y",
      "-i",
      videoPath,
      "-i",
      audioPath,
      "-map",
      "0:v:0",
      "-map",
      "1:a:0",
      "-c:v",
      "libx264",
      "-preset",
      "fast",
      "-crf",
      "23",
      "-pix_fmt",
      "yuv420p",
      "-c:a",
      "aac",
      "-b:a",
      "192k",
      "-shortest",
      "-movflags",
      "+faststart",
      outPath,
    ]);
    const outBuf = fs.readFileSync(outPath);
    res.json({
      videoBase64: outBuf.toString("base64"),
    });
  } catch (e) {
    res.status(500).json({
      error: e instanceof Error ? e.message : String(e),
    });
  } finally {
    try {
      fs.rmSync(tmp, { recursive: true, force: true });
    } catch (_) {}
  }
});

app.listen(PORT, () => {
  console.log(`ffmpeg-service listening on ${PORT}`);
});
