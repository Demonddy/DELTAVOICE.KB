# DeltaVoice FFmpeg service

Small HTTP service that runs **FFmpeg** for:

- `POST /extract-audio` — extract AAC audio from a video (base64 in/out).
- `POST /mux` — mux processed audio into video; output is **MP4** with **H.264 + AAC** (re-encodes video so browser **WebM/VP9** sources work; `-c:v copy` cannot mux WebM into MP4).

Used by [Convex `videoPipeline`](../convex/videoPipeline.ts). Convex cannot run FFmpeg in the serverless runtime; this service must be deployed separately.

## Environment

| Variable | Description |
|----------|-------------|
| `PORT` | Listen port (default `8790`) |
| `FFMPEG_SERVICE_API_KEY` | Optional. If set, require `Authorization: Bearer <key>` |

## Convex (required for video workflow)

Without **`FFMPEG_VIDEO_SERVICE_URL`**, [`runVideoPipeline`](../convex/videoPipeline.ts) throws and `/video-workflow` cannot complete (extract + mux).

Set the **public HTTPS base URL** of this service (no trailing slash):

```bash
# From the repo root (where package.json has convex)
npx convex env set FFMPEG_VIDEO_SERVICE_URL https://YOUR_HOST

# Production deployment (if you use a separate prod Convex app)
npx convex env set FFMPEG_VIDEO_SERVICE_URL https://YOUR_HOST --prod
```

If you protect the service with **`FFMPEG_SERVICE_API_KEY`**, set the **same secret** in Convex so actions can call `/extract-audio` and `/mux`:

```bash
npx convex env set FFMPEG_SERVICE_API_KEY YOUR_SECRET
# On Fly.io:  fly secrets set FFMPEG_SERVICE_API_KEY=YOUR_SECRET
```

Redeploy **this** service after any `server.mjs` / Docker change, then confirm Convex still points at the live URL.

## Redeploy after code changes

1. Rebuild and push/restart your container (Docker, Fly, Railway, Render, etc.).
2. Smoke test: `GET https://YOUR_HOST/health` should return `{"ok":true}`.
3. No Convex change is needed **unless** the public URL changed.

## Fly.io (optional)

[`fly.toml`](fly.toml) is a template. Install [Fly CLI](https://fly.io/docs/hands-on/install-flyctl/), then:

```bash
cd ffmpeg-service
fly apps create YOUR_UNIQUE_APP_NAME   # or edit app name in fly.toml first
fly secrets set FFMPEG_SERVICE_API_KEY=YOUR_SECRET   # optional
fly deploy
```

Your app URL is `https://YOUR_UNIQUE_APP_NAME.fly.dev` (check `fly status`). Use that value for `FFMPEG_VIDEO_SERVICE_URL`.

## Local

```bash
cd ffmpeg-service
npm install
# Ensure `ffmpeg` is on PATH
node server.mjs
```

## Docker

```bash
docker build -t deltavoice-ffmpeg .
docker run -p 8790:8790 -e FFMPEG_SERVICE_API_KEY=secret deltavoice-ffmpeg
```

## Limits

Large base64 payloads increase memory use. For production, prefer **presigned uploads** to object storage and pass URLs to this service (future work).
