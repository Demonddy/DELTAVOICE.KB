# DeltaVoice - AI Voice Translator & Converter

A cross-platform AI voice translation and conversion app available as:
- 📱 **Android System Keyboard** - Use directly in any app
- 🌐 **Web App (PWA)** - Access from any browser

Both platforms share the same **Supabase** backend for instant voice processing.

## 🎯 Features

| Feature | Description |
|---------|-------------|
| 🌍 **Full Translation** | Transcribe → Translate → Convert voice (12+ languages) |
| 🗣️ **Voice Conversion** | Change voice style while keeping your language |
| 📝 **Text Transcription** | Get text from speech with translation |
| ⚡ **Instant Processing** | Real-time response from Supabase Edge Functions |
| 🔒 **Privacy First** | Audio processed in-memory, not stored |

## 🚀 Quick Start

### Android Keyboard

```bash
# 1. Open in Android Studio
# 2. Build and install on device
# 3. Enable keyboard in Settings > Languages & Input
# 4. Use the voice features from any text field
```

### Web App

```bash
cd web

# Start local server (choose one)
python -m http.server 8000
# or
npx serve .

# Open http://localhost:8000
```

## 📁 Project Structure

```
keyboard/
├── app/                          # Android Keyboard App
│   ├── src/main/java/com/deltavoice/
│   │   ├── MainKeyboardService.kt    # Main IME service
│   │   ├── VoiceProcessModeActivity.kt
│   │   ├── api/                      # API services
│   │   └── config/                   # Configuration
│   └── src/main/res/                 # Resources
│
├── web/                          # Web App (PWA)
│   ├── index.html                    # Main page
│   ├── app.js                        # Application logic
│   ├── styles.css                    # Styling
│   ├── config.js                     # Configuration
│   ├── sw.js                         # Service Worker
│   └── manifest.json                 # PWA manifest
│
├── supabase/                     # Backend
│   ├── functions/                    # Edge Functions
│   │   ├── complete-voice-workflow/  # Main processing
│   │   ├── voice-to-text/
│   │   ├── translate-text/
│   │   └── voice-conversion/
│   └── migrations/                   # Database schema
│
└── whisper-backend/              # Optional: Self-hosted Whisper
```

## 🔧 Configuration

### Supabase Setup

1. Create a Supabase project at [supabase.com](https://supabase.com)
2. Deploy Edge Functions from `/supabase/functions/`
3. Update credentials:

**Android** (`app/src/main/java/com/deltavoice/config/SupabaseConfig.kt`):
```kotlin
const val SUPABASE_URL = "your-supabase-url"
const val SUPABASE_ANON_KEY = "your-anon-key"
```

**Web** (`web/config.js`):
```javascript
SUPABASE_URL: 'your-supabase-url',
SUPABASE_ANON_KEY: 'your-anon-key'
```

## 🌐 Deployment

### Web App

**Vercel:**
```bash
cd web && vercel
```

**Netlify:**
```bash
cd web && netlify deploy --prod
```

**GitHub Pages:**
Push `web/` contents to your repo and enable Pages.

### Android

Build signed APK in Android Studio or use:
```bash
./gradlew assembleRelease
```

## 📱 Supported Platforms

| Platform | Version | Notes |
|----------|---------|-------|
| Android | 5.0+ (API 21) | System keyboard |
| Web | Modern browsers | Chrome, Firefox, Safari, Edge |
| iOS | Safari 14.5+ | PWA install available |

## 🛠️ Tech Stack

- **Android**: Kotlin, Jetpack, Supabase SDK
- **Web**: Vanilla JS, Web Audio API, PWA
- **Backend**: Supabase Edge Functions (Deno)
- **AI Services**: OpenAI Whisper, ElevenLabs, Google Translate

## 📖 Documentation

- [Supabase Setup](SUPABASE_SETUP.md)
- [Testing Guide](TESTING_GUIDE.md)
- [Web App README](web/README.md)
- [Project Details](PROJECT_README.md)

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## 📄 License

MIT License - see LICENSE file for details.

---

**Built with ❤️ using Supabase, OpenAI, and ElevenLabs**
