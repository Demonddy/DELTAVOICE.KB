# DeltaVoice Desktop

System-wide voice dictation and AI tools for desktop. Press **Ctrl+Space** anywhere to record your voice, process it with AI (translate, change voice, transcribe), and insert the result directly into any application.

## Prerequisites

1. **Rust** - Install from https://rustup.rs
2. **Node.js** (18+) - https://nodejs.org
3. **Windows Build Tools** - Visual Studio C++ Build Tools (installed with Rust on Windows)

## Setup

```bash
cd desktop

# 1. Install dependencies
npm install

# 2. Configure Supabase key
#    Copy .env.example to .env and fill in your SUPABASE_ANON_KEY
#    (same key from your Android app's local.properties)
cp .env.example .env

# 3. Run in development
npm run tauri dev

# 4. Build for production
npm run tauri build
```

## How It Works

### System-Wide Voice Recording (Ctrl+Space)
1. Press **Ctrl+Space** from any application
2. DeltaVoice overlay appears and starts recording
3. Press **Ctrl+Space** again to stop
4. Choose processing mode:
   - **Change Language & Voice** - Translate + new AI voice
   - **Translate My Same Voice** - Translate while keeping your voice (clone)
   - **Transcript & Translate** - Text only
5. Click **Insert Text at Cursor** to paste directly into your app

### AI Toolbar (Ctrl+Space × 2)
Double-press Ctrl+Space to open the toolbar with:
- **AI Chat** - Smart assistant for writing and questions
- **AI Writing Tools** - 12 tools (Grammar, Reply, Translate, Enhance, Tone, Paraphrase, Continue, Longer, Summarize, Synonyms, Shorter, Email)
- **Video** - Record/upload video for translation
- **Calculator** - Built-in calculator
- **Dictionary** - Word lookup
- **Clipboard** - Clipboard history manager

### Text Insertion
After processing, text is inserted into the focused application via clipboard + simulated Ctrl+V. Works with:
- WhatsApp Desktop, Telegram Desktop
- Microsoft Word, Google Docs
- Gmail, Outlook
- VS Code, any code editor
- Browsers, video editors
- Any application with a text field

## Architecture

- **Tauri v2** (Rust) - Global hotkeys, system tray, clipboard, keyboard simulation
- **React + TypeScript** - UI with Liquid Glass design
- **Supabase** - Authentication (same project as mobile app)
- **Convex** - Voice/AI/Video processing backend (same endpoints as mobile app)

## Shared Backend

This desktop app uses the exact same backend as the DeltaVoice Android keyboard:
- Supabase Auth (email/password)
- Convex HTTP endpoints for voice/AI/video workflows
- Same rate limits and premium tiers
