
# Whisper Transcription Backend

A free, self-hosted backend using OpenAI's open-source Whisper model for audio transcription.

## Quick Setup on Replit (Free Hosting)

1. Go to [Replit](https://replit.com) and create a new account (free)
2. Click "Create Repl" → "Import from GitHub" 
3. Or create a new Python Repl and copy these files:
   - `main.py`
   - `requirements.txt`
   - `.replit` (see below)

4. Create a `.replit` file in your project root:
```
[nix]
channel = "stable-22_11"

[deployment]
run = ["python", "main.py"]

[[ports]]
localPort = 8000
externalPort = 80
```

5. Click "Run" - Replit will automatically install dependencies
6. Your API will be available at: `https://your-repl-name.your-username.repl.co`

## Local Development

```bash
pip install -r requirements.txt
python main.py
```

API will be available at: `http://localhost:8000`

## API Endpoints

- `POST /transcribe` - Upload audio file for transcription
- `GET /health` - Health check
- `GET /` - API status

## Usage

```bash
curl -X POST "YOUR_REPLIT_URL/transcribe" \
     -F "file=@audio.wav" \
     -H "Content-Type: multipart/form-data"
```

Response:
```json
{
  "text": "Your transcribed text here"
}
```
