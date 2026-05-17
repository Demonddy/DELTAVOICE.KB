/**
 * DeltaVoice Web App
 * Voice recording, processing, and translation using Supabase Edge Functions
 * 
 * This web app uses the same Supabase backend as the Android keyboard app.
 * Both platforms share configuration in their respective config files.
 */

// Get configuration (loaded from config.js)
const config = window.DeltaVoiceConfig || {
    SUPABASE_URL: 'https://rkfveqzktfmgegtsoxlf.supabase.co',
    SUPABASE_ANON_KEY: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJrZnZlcXprdGZtZ2VndHNveGxmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY3NzAyMDYsImV4cCI6MjA5MjM0NjIwNn0.dOmPCxz5Dq5ZtnX3LU7LTNjyHFcxWbJ5XLNrWPUF0NM',
    FUNCTIONS: { COMPLETE_VOICE_WORKFLOW: 'complete-voice-workflow', WRITING_TOOL: 'writing-tool', AI_CHAT: 'ai-chat' }
};

class DeltaVoiceApp {
    constructor() {
        // State
        this.isRecording = false;
        this.isPlaying = false;
        this.isProcessing = false;
        this.audioBlob = null;
        this.processedAudioBase64 = null;
        this.recordingStartTime = null;
        this.timerInterval = null;
        this.selectedMode = 'complete';
        
        // Audio
        this.mediaRecorder = null;
        this.audioChunks = [];
        this.audioContext = null;
        this.analyser = null;
        this.recordedAudio = null;
        
        // DOM Elements
        this.initElements();
        this.initEventListeners();
        this.initWaveform();
        
        console.log('DeltaVoice Web App initialized');
    }
    
    initElements() {
        // Sections
        this.recordSection = document.getElementById('recordSection');
        this.processSection = document.getElementById('processSection');
        
        // Recording elements
        this.micButton = document.getElementById('micButton');
        this.timerEl = document.getElementById('timer');
        this.recordHint = document.getElementById('recordHint');
        this.recorder = this.micButton.parentElement;
        this.statusEl = document.getElementById('status');
        
        // Player elements
        this.playButton = document.getElementById('playButton');
        this.playIcon = document.getElementById('playIcon');
        this.pauseIcon = document.getElementById('pauseIcon');
        this.waveform = document.getElementById('waveform');
        this.durationEl = document.getElementById('duration');
        
        // Mode selection
        this.modeCards = document.querySelectorAll('.mode-card');
        
        // Options
        this.languageSelect = document.getElementById('languageSelect');
        this.voiceSelect = document.getElementById('voiceSelect');
        
        // Buttons
        this.sendButton = document.getElementById('sendButton');
        this.processButton = document.getElementById('processButton');
        this.backButton = document.getElementById('backButton');
        
        // Output
        this.outputSection = document.getElementById('outputSection');
        this.outputText = document.getElementById('outputText');
        this.outputAudioPlayer = document.getElementById('outputAudioPlayer');
        
        // Loading
        this.loadingOverlay = document.getElementById('loadingOverlay');
        this.loadingText = document.getElementById('loadingText');
    }
    
    initEventListeners() {
        // Recording
        this.micButton.addEventListener('click', () => this.toggleRecording());
        
        // Playback
        this.playButton.addEventListener('click', () => this.togglePlayback());
        
        // Mode selection
        this.modeCards.forEach(card => {
            card.addEventListener('click', () => this.selectMode(card.dataset.mode));
        });
        
        // Actions
        this.processButton.addEventListener('click', () => this.processVoice());
        this.sendButton.addEventListener('click', () => this.sendVoice());
        this.backButton.addEventListener('click', () => this.goBack());
        
        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            if (e.code === 'Space' && !e.target.matches('input, textarea, select')) {
                e.preventDefault();
                if (this.recordSection.classList.contains('hidden')) {
                    this.togglePlayback();
                } else {
                    this.toggleRecording();
                }
            }
        });
    }
    
    initWaveform() {
        // Create fake waveform bars
        const bars = 40;
        this.waveform.innerHTML = '';
        for (let i = 0; i < bars; i++) {
            const bar = document.createElement('div');
            bar.className = 'waveform-bar';
            bar.style.height = `${Math.random() * 20 + 8}px`;
            this.waveform.appendChild(bar);
        }
    }
    
    // ===================
    // RECORDING
    // ===================
    
    async toggleRecording() {
        if (this.isRecording) {
            await this.stopRecording();
        } else {
            await this.startRecording();
        }
    }
    
    async startRecording() {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ 
                audio: {
                    echoCancellation: true,
                    noiseSuppression: true,
                    sampleRate: 44100
                } 
            });
            
            // Set up audio context for visualization
            this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
            this.analyser = this.audioContext.createAnalyser();
            const source = this.audioContext.createMediaStreamSource(stream);
            source.connect(this.analyser);
            
            // Choose best available format
            const mimeType = this.getSupportedMimeType();
            
            this.mediaRecorder = new MediaRecorder(stream, { mimeType });
            this.audioChunks = [];
            
            this.mediaRecorder.ondataavailable = (e) => {
                if (e.data.size > 0) {
                    this.audioChunks.push(e.data);
                }
            };
            
            this.mediaRecorder.onstop = () => {
                this.processRecordedAudio();
            };
            
            this.mediaRecorder.start(100); // Collect data every 100ms
            this.isRecording = true;
            this.recordingStartTime = Date.now();
            
            // Update UI
            this.recorder.classList.add('recording');
            this.statusEl.textContent = 'Recording...';
            this.statusEl.classList.add('recording');
            this.recordHint.textContent = 'Tap to stop';
            
            // Start timer
            this.startTimer();
            
            console.log('Recording started');
        } catch (err) {
            console.error('Error starting recording:', err);
            alert('Could not access microphone. Please grant permission.');
        }
    }
    
    getSupportedMimeType() {
        const types = [
            'audio/webm;codecs=opus',
            'audio/webm',
            'audio/mp4',
            'audio/ogg;codecs=opus',
            'audio/ogg'
        ];
        
        for (const type of types) {
            if (MediaRecorder.isTypeSupported(type)) {
                console.log('Using MIME type:', type);
                return type;
            }
        }
        
        console.log('Using default MIME type');
        return '';
    }
    
    async stopRecording() {
        if (this.mediaRecorder && this.mediaRecorder.state !== 'inactive') {
            this.mediaRecorder.stop();
            this.mediaRecorder.stream.getTracks().forEach(track => track.stop());
        }
        
        this.isRecording = false;
        this.stopTimer();
        
        // Update UI
        this.recorder.classList.remove('recording');
        this.statusEl.textContent = 'Ready';
        this.statusEl.classList.remove('recording');
        this.recordHint.textContent = 'Tap to record';
        
        console.log('Recording stopped');
    }
    
    processRecordedAudio() {
        const mimeType = this.mediaRecorder?.mimeType || 'audio/webm';
        this.audioBlob = new Blob(this.audioChunks, { type: mimeType });
        
        console.log('Audio recorded:', this.audioBlob.size, 'bytes, type:', mimeType);
        
        // Create audio element for playback
        const audioUrl = URL.createObjectURL(this.audioBlob);
        this.recordedAudio = new Audio(audioUrl);
        
        this.recordedAudio.onloadedmetadata = () => {
            const duration = this.recordedAudio.duration;
            this.durationEl.textContent = this.formatTime(duration);
        };
        
        this.recordedAudio.onended = () => {
            this.isPlaying = false;
            this.updatePlayButton();
        };
        
        // Show process section
        this.showProcessSection();
    }
    
    // ===================
    // TIMER
    // ===================
    
    startTimer() {
        this.timerInterval = setInterval(() => {
            const elapsed = (Date.now() - this.recordingStartTime) / 1000;
            this.timerEl.textContent = this.formatTime(elapsed);
        }, 100);
    }
    
    stopTimer() {
        if (this.timerInterval) {
            clearInterval(this.timerInterval);
            this.timerInterval = null;
        }
    }
    
    formatTime(seconds) {
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    }
    
    // ===================
    // PLAYBACK
    // ===================
    
    togglePlayback() {
        if (this.isPlaying) {
            this.pausePlayback();
        } else {
            this.startPlayback();
        }
    }
    
    startPlayback() {
        const audioToPlay = this.processedAudioBase64 
            ? this.outputAudioPlayer 
            : this.recordedAudio;
            
        if (audioToPlay) {
            audioToPlay.play();
            this.isPlaying = true;
            this.updatePlayButton();
            
            // Update waveform animation
            this.animateWaveform();
        }
    }
    
    pausePlayback() {
        const audioToPlay = this.processedAudioBase64 
            ? this.outputAudioPlayer 
            : this.recordedAudio;
            
        if (audioToPlay) {
            audioToPlay.pause();
            this.isPlaying = false;
            this.updatePlayButton();
        }
    }
    
    updatePlayButton() {
        this.playIcon.classList.toggle('hidden', this.isPlaying);
        this.pauseIcon.classList.toggle('hidden', !this.isPlaying);
    }
    
    animateWaveform() {
        const bars = this.waveform.querySelectorAll('.waveform-bar');
        
        const animate = () => {
            if (!this.isPlaying) {
                bars.forEach(bar => bar.style.height = `${Math.random() * 20 + 8}px`);
                return;
            }
            
            bars.forEach(bar => {
                bar.style.height = `${Math.random() * 24 + 8}px`;
            });
            
            requestAnimationFrame(animate);
        };
        
        animate();
    }
    
    // ===================
    // MODE SELECTION
    // ===================
    
    selectMode(mode) {
        this.selectedMode = mode;
        this.modeCards.forEach(card => {
            card.classList.toggle('selected', card.dataset.mode === mode);
        });
        console.log('Selected mode:', mode);
    }
    
    // ===================
    // PROCESSING
    // ===================
    
    async processVoice() {
        if (!this.audioBlob) {
            alert('No recording to process');
            return;
        }
        
        if (this.isProcessing) {
            return;
        }
        
        this.isProcessing = true;
        this.showLoading(this.getLoadingMessage());
        
        try {
            // Convert blob to base64
            const audioBase64 = await this.blobToBase64(this.audioBlob);
            
            // Determine format from MIME type
            const format = this.getFormatFromMimeType(this.audioBlob.type);
            
            // Build request
            const request = {
                audioBase64: audioBase64,
                targetLanguage: this.languageSelect.value,
                voiceStyle: this.voiceSelect.value,
                workflowType: this.selectedMode,
                format: format
            };
            
            console.log('Sending request:', {
                ...request,
                audioBase64: `[${audioBase64.length} chars]`
            });
            
            // Call Supabase Edge Function
            const functionUrl = config.getFunctionUrl 
                ? config.getFunctionUrl(config.FUNCTIONS.COMPLETE_VOICE_WORKFLOW)
                : `${config.SUPABASE_URL}/functions/v1/${config.FUNCTIONS.COMPLETE_VOICE_WORKFLOW}`;
                
            const headers = config.getHeaders 
                ? config.getHeaders()
                : {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${config.SUPABASE_ANON_KEY}`,
                    'apikey': config.SUPABASE_ANON_KEY
                };
            
            const response = await fetch(functionUrl, {
                method: 'POST',
                headers: headers,
                body: JSON.stringify(request)
            });
            
            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Server error: ${errorText}`);
            }
            
            const result = await response.json();
            console.log('Response:', result);
            
            if (!result.success) {
                throw new Error(result.error || 'Processing failed');
            }
            
            // Handle response based on mode
            this.handleWorkflowResponse(result);
            
        } catch (error) {
            console.error('Processing error:', error);
            alert(`Error: ${error.message}`);
        } finally {
            this.isProcessing = false;
            this.hideLoading();
        }
    }
    
    handleWorkflowResponse(result) {
        console.log('Handling workflow response:', {
            hasTranslatedText: !!result.translatedText,
            hasOriginalText: !!result.originalText,
            hasAudio: !!result.convertedAudioBase64,
            audioLength: result.convertedAudioBase64?.length || 0
        });
        
        // Show output section
        this.outputSection.classList.remove('hidden');
        this.sendButton.disabled = false;
        
        // Handle text output
        if (result.translatedText) {
            this.outputText.textContent = result.translatedText;
            this.outputText.style.display = 'block';
        } else if (result.originalText && this.selectedMode === 'text-only') {
            this.outputText.textContent = result.originalText;
            this.outputText.style.display = 'block';
        } else {
            this.outputText.style.display = 'none';
        }
        
        // Handle audio output
        const hasAudio = result.convertedAudioBase64 && result.convertedAudioBase64.length > 100;
        
        if (hasAudio) {
            console.log('Processing audio output, base64 length:', result.convertedAudioBase64.length);
            
            try {
                this.processedAudioBase64 = result.convertedAudioBase64;
                
                // Create audio URL from base64
                const audioBytes = this.base64ToArrayBuffer(result.convertedAudioBase64);
                console.log('Decoded audio bytes:', audioBytes.byteLength);
                
                const audioBlob = new Blob([audioBytes], { type: 'audio/mpeg' });
                const audioUrl = URL.createObjectURL(audioBlob);
                
                this.outputAudioPlayer.src = audioUrl;
                this.outputAudioPlayer.style.display = 'block';
                this.outputAudioPlayer.parentElement.style.display = 'block';
                
                // Update duration text to show processed audio is ready
                this.durationEl.textContent = '✓ Ready';
                
                // Update duration when metadata loads
                this.outputAudioPlayer.onloadedmetadata = () => {
                    const duration = this.outputAudioPlayer.duration;
                    if (isFinite(duration)) {
                        this.durationEl.textContent = '✓ ' + this.formatTime(duration);
                    }
                };
                
                this.outputAudioPlayer.onended = () => {
                    this.isPlaying = false;
                    this.updatePlayButton();
                };
                
                this.outputAudioPlayer.onerror = (e) => {
                    console.error('Audio playback error:', e);
                    alert('Error playing audio. The file may be corrupted.');
                };
                
                // Auto-play processed audio
                this.outputAudioPlayer.play().catch(err => {
                    console.log('Auto-play blocked:', err);
                    // Show play button hint if auto-play blocked
                    this.statusEl.textContent = 'Ready - tap play ▶';
                });
                
            } catch (err) {
                console.error('Error processing audio:', err);
                alert('Error processing audio: ' + err.message);
            }
        } else {
            console.log('No audio in response');
            this.outputAudioPlayer.style.display = 'none';
            this.outputAudioPlayer.parentElement.style.display = 'none';
        }
        
        // Show success message
        this.showSuccessMessage();
    }
    
    showSuccessMessage() {
        const messages = {
            'complete': '✓ Translation complete! Audio ready to send.',
            'voice-only': '✓ Voice converted! Ready to send.',
            'text-only': '✓ Text transcribed and translated!'
        };
        
        this.statusEl.textContent = messages[this.selectedMode] || '✓ Done';
        this.statusEl.classList.remove('recording', 'processing');
        
        setTimeout(() => {
            this.statusEl.textContent = 'Ready';
        }, 3000);
    }
    
    getLoadingMessage() {
        const messages = {
            'complete': 'Translating and converting voice...',
            'voice-only': `Converting to ${this.voiceSelect.options[this.voiceSelect.selectedIndex].text}...`,
            'text-only': 'Transcribing and translating...'
        };
        return messages[this.selectedMode] || 'Processing...';
    }
    
    getFormatFromMimeType(mimeType) {
        if (mimeType.includes('webm')) return 'webm';
        if (mimeType.includes('mp4')) return 'mp4';
        if (mimeType.includes('ogg')) return 'ogg';
        if (mimeType.includes('wav')) return 'wav';
        return 'webm'; // default
    }
    
    // ===================
    // SEND/SHARE
    // ===================
    
    async sendVoice() {
        if (!this.processedAudioBase64 && !this.outputText.textContent) {
            alert('Nothing to send');
            return;
        }
        
        // If we have processed audio, download it
        if (this.processedAudioBase64) {
            const audioBytes = this.base64ToArrayBuffer(this.processedAudioBase64);
            const audioBlob = new Blob([audioBytes], { type: 'audio/mpeg' });
            
            // Try Web Share API first
            if (navigator.share && navigator.canShare) {
                try {
                    const file = new File([audioBlob], 'deltavoice-output.mp3', { type: 'audio/mpeg' });
                    
                    if (navigator.canShare({ files: [file] })) {
                        await navigator.share({
                            files: [file],
                            title: 'DeltaVoice Output',
                            text: this.outputText.textContent || 'Voice converted by DeltaVoice'
                        });
                        return;
                    }
                } catch (err) {
                    console.log('Share failed, falling back to download:', err);
                }
            }
            
            // Fallback: download file
            const url = URL.createObjectURL(audioBlob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'deltavoice-output.mp3';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
            
        } else if (this.outputText.textContent) {
            // Share/copy text
            if (navigator.share) {
                try {
                    await navigator.share({
                        text: this.outputText.textContent,
                        title: 'DeltaVoice Transcript'
                    });
                    return;
                } catch (err) {
                    console.log('Share failed, copying to clipboard');
                }
            }
            
            // Fallback: copy to clipboard
            await navigator.clipboard.writeText(this.outputText.textContent);
            alert('Text copied to clipboard!');
        }
    }
    
    // ===================
    // UI HELPERS
    // ===================
    
    showProcessSection() {
        this.recordSection.classList.add('hidden');
        this.processSection.classList.remove('hidden');
        this.outputSection.classList.add('hidden');
        this.sendButton.disabled = true;
        this.processedAudioBase64 = null;
    }
    
    goBack() {
        this.processSection.classList.add('hidden');
        this.recordSection.classList.remove('hidden');
        this.timerEl.textContent = '0:00';
        this.audioBlob = null;
        this.processedAudioBase64 = null;
        this.recordedAudio = null;
        this.outputText.textContent = '';
        this.outputAudioPlayer.src = '';
    }
    
    showLoading(message) {
        this.loadingText.textContent = message;
        this.loadingOverlay.classList.remove('hidden');
        this.statusEl.textContent = 'Processing...';
        this.statusEl.classList.add('processing');
        this.processButton.disabled = true;
    }
    
    hideLoading() {
        this.loadingOverlay.classList.add('hidden');
        this.statusEl.classList.remove('processing');
        this.processButton.disabled = false;
    }
    
    // ===================
    // UTILITIES
    // ===================
    
    async blobToBase64(blob) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onloadend = () => {
                const base64 = reader.result.split(',')[1];
                resolve(base64);
            };
            reader.onerror = reject;
            reader.readAsDataURL(blob);
        });
    }
    
    base64ToArrayBuffer(base64) {
        const binaryString = atob(base64);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
            bytes[i] = binaryString.charCodeAt(i);
        }
        return bytes.buffer;
    }
}

// Initialize app when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    window.deltaVoice = new DeltaVoiceApp();
});

// Register service worker for PWA
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('sw.js')
            .then(reg => console.log('Service Worker registered:', reg.scope))
            .catch(err => console.log('Service Worker registration failed:', err));
    });
}
