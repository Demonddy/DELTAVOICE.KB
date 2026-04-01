/**
 * Video workflow orchestration: FFmpeg worker (extract + mux) + runVoiceWorkflow.
 * Convex cannot run ffmpeg natively; FFMPEG_VIDEO_SERVICE_URL must point to ffmpeg-service.
 */
import {
  runVoiceWorkflow,
  type WorkflowRequest,
  type WorkflowResult,
} from "./voiceWorkflow";

export interface VideoPipelineResult extends WorkflowResult {
  convertedVideoBase64?: string;
  muxSkipped?: boolean;
  muxError?: string;
}

export async function runVideoPipeline(input: {
  videoBase64: string;
  targetLanguage: string;
  voiceStyle: string;
  videoFormat?: string;
}): Promise<VideoPipelineResult> {
  const base = process.env.FFMPEG_VIDEO_SERVICE_URL?.replace(/\/$/, "");
  if (!base) {
    throw new Error(
      "FFMPEG_VIDEO_SERVICE_URL is not configured. Deploy ffmpeg-service and set this env in Convex."
    );
  }

  const authHeaders: Record<string, string> = {
    "Content-Type": "application/json",
  };
  const apiKey = process.env.FFMPEG_SERVICE_API_KEY;
  if (apiKey) {
    authHeaders["Authorization"] = `Bearer ${apiKey}`;
  }

  const extractRes = await fetch(`${base}/extract-audio`, {
    method: "POST",
    headers: authHeaders,
    body: JSON.stringify({
      videoBase64: input.videoBase64,
      format: input.videoFormat || "mp4",
    }),
  });

  if (!extractRes.ok) {
    const t = await extractRes.text();
    throw new Error(`Extract audio failed: ${extractRes.status} ${t}`);
  }

  const extracted = (await extractRes.json()) as {
    audioBase64: string;
    format?: string;
  };
  if (!extracted.audioBase64) {
    throw new Error("No audio extracted from video");
  }

  const wfReq: WorkflowRequest = {
    audioBase64: extracted.audioBase64,
    targetLanguage: input.targetLanguage,
    voiceStyle: input.voiceStyle,
    workflowType: "complete",
    format: extracted.format || "m4a",
  };

  const wfResult = await runVoiceWorkflow(wfReq);

  if (!wfResult.convertedAudioBase64 || wfResult.convertedAudioBase64.length === 0) {
    return {
      ...wfResult,
      muxSkipped: true,
      muxError: wfResult.ttsFallback ? "TTS fallback — no audio to mux" : "No converted audio",
    };
  }

  const muxRes = await fetch(`${base}/mux`, {
    method: "POST",
    headers: authHeaders,
    body: JSON.stringify({
      videoBase64: input.videoBase64,
      processedAudioBase64: wfResult.convertedAudioBase64,
      videoFormat: input.videoFormat || "mp4",
    }),
  });

  if (!muxRes.ok) {
    const t = await muxRes.text();
    return {
      ...wfResult,
      muxSkipped: true,
      muxError: `Mux failed: ${muxRes.status} ${t}`,
    };
  }

  const muxed = (await muxRes.json()) as { videoBase64?: string };
  return {
    ...wfResult,
    convertedVideoBase64: muxed.videoBase64,
    muxSkipped: !muxed.videoBase64,
  };
}
