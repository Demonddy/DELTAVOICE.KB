
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import whisper
import tempfile
import os
import uvicorn
from pydantic import BaseModel

app = FastAPI(title="Whisper Transcription API", version="1.0.0")

# Enable CORS for all origins (needed for frontend integration)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Load Whisper model on startup (using base model for speed)
print("Loading Whisper model...")
model = whisper.load_model("base")
print("Whisper model loaded successfully!")

class TranscriptionResponse(BaseModel):
    text: str

@app.get("/")
async def root():
    return {"message": "Whisper Transcription API is running!", "status": "healthy"}

@app.post("/transcribe", response_model=TranscriptionResponse)
async def transcribe_audio(file: UploadFile = File(...)):
    """
    Transcribe audio file using Whisper
    Accepts: multipart/form-data with audio file
    Returns: JSON with transcribed text
    """
    try:
        # Validate file type
        if not file.content_type or not file.content_type.startswith('audio/'):
            # Allow common audio formats even if content-type isn't set correctly
            allowed_extensions = ['.wav', '.mp3', '.m4a', '.webm', '.ogg', '.flac']
            if not any(file.filename.lower().endswith(ext) for ext in allowed_extensions):
                raise HTTPException(status_code=400, detail="Invalid audio file format")
        
        # Create temporary file to store uploaded audio
        with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as temp_file:
            # Read and write file content
            content = await file.read()
            temp_file.write(content)
            temp_file_path = temp_file.name
        
        try:
            # Transcribe using Whisper
            print(f"Transcribing audio file: {file.filename}")
            result = model.transcribe(temp_file_path)
            transcribed_text = result["text"].strip()
            
            print(f"Transcription successful: {transcribed_text[:100]}...")
            return TranscriptionResponse(text=transcribed_text)
            
        finally:
            # Clean up temporary file
            if os.path.exists(temp_file_path):
                os.unlink(temp_file_path)
                
    except Exception as e:
        print(f"Transcription error: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Transcription failed: {str(e)}")

@app.get("/health")
async def health_check():
    return {"status": "healthy", "model": "whisper-base"}

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run(app, host="0.0.0.0", port=port)
