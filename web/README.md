# DeltaVoice Web App

A Progressive Web App (PWA) version of DeltaVoice - AI Voice Translator & Converter. This web app uses the same Supabase backend as the Android keyboard app.

## Features

- 🎙️ **Voice Recording** - Record audio directly in the browser
- 🌐 **Full Translation** - Transcribe, translate, and convert voice
- 🗣️ **Voice Conversion** - Change voice style without translation
- 📝 **Text Only** - Get text transcription and translation
- 📱 **PWA Support** - Install as a standalone app on any device
- 🔄 **Offline Support** - Basic offline functionality with service worker

## Quick Start

### Option 1: Local Development

1. **Start a local server:**
   ```bash
   # Using Python
   python -m http.server 8000
   
   # Using Node.js
   npx serve .
   
   # Using PHP
   php -S localhost:8000
   ```

2. **Open in browser:**
   ```
   http://localhost:8000
   ```

### Option 2: Deploy to Vercel

1. Install Vercel CLI:
   ```bash
   npm i -g vercel
   ```

2. Deploy:
   ```bash
   cd web
   vercel
   ```

### Option 3: Deploy to Netlify

1. Drag and drop the `web` folder to [Netlify Drop](https://app.netlify.com/drop)

### Option 4: Deploy to GitHub Pages

1. Create a GitHub repository
2. Push the `web` folder contents
3. Enable GitHub Pages in repository settings

## Generate Icons

Before deploying, generate the PWA icons:

1. Open `generate-icons.html` in a browser
2. Click "Generate Icons"
3. Download both icon files (`icon-192.png` and `icon-512.png`)
4. Place them in the `web` folder

## Configuration

Edit `config.js` to update Supabase credentials:

```javascript
const DeltaVoiceConfig = {
    SUPABASE_URL: 'your-supabase-url',
    SUPABASE_ANON_KEY: 'your-anon-key',
    // ...
};
```

## File Structure

```
web/
├── index.html          # Main HTML file
├── styles.css          # CSS styles
├── app.js              # Main application logic
├── config.js           # Configuration (Supabase credentials)
├── sw.js               # Service Worker for PWA
├── manifest.json       # PWA manifest
├── icon.svg            # Vector icon
├── icon-192.png        # 192x192 icon (generate this)
├── icon-512.png        # 512x512 icon (generate this)
├── generate-icons.html # Icon generator tool
└── README.md           # This file
```

## API Endpoints

The web app communicates with these Supabase Edge Functions:

| Function | Description |
|----------|-------------|
| `complete-voice-workflow` | Full pipeline: transcribe → translate → voice convert |
| `voice-to-text` | Speech-to-text only |
| `translate-text` | Text translation |
| `voice-conversion` | Voice style conversion |

## Browser Support

- ✅ Chrome / Edge (recommended)
- ✅ Firefox
- ✅ Safari (iOS 14.5+)
- ✅ Samsung Internet

**Note:** Microphone access requires HTTPS in production.

## Troubleshooting

### Microphone not working
- Ensure the site is served over HTTPS
- Check browser permissions for microphone access
- Try a different browser

### Processing fails
- Check browser console for errors
- Verify Supabase credentials in `config.js`
- Ensure the Edge Functions are deployed

### PWA not installing
- The site must be served over HTTPS
- Icons must be valid PNG files
- Check `manifest.json` for errors

## Development

### Hot Reload (with Python)
```bash
pip install watchdog
watchmedo shell-command --patterns="*.html;*.css;*.js" --command='echo "Reload browser"' .
```

### Debug Service Worker
1. Open Chrome DevTools
2. Go to Application → Service Workers
3. Check "Update on reload"

## License

MIT License - same as the main DeltaVoice project.
