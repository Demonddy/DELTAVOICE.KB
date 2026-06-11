---
name: Differentiate three voice modes
overview: "Make the three Step-2 voice modes produce strictly distinct outputs: Change Language & Voice and Translate My Same Voice produce ONLY sendable audio (no auto-inserted text), while Transcript & Translate produces ONLY text (already correct). All edits are in MainKeyboardService.kt."
todos:
  - id: complete-audio-only
    content: In handleWorkflowSuccess 'complete' branch, remove insertText and only call saveAndShowProcessedAudio on success; on no-audio, toast and resetButtonState without inserting text.
    status: completed
  - id: fallback-audio-only
    content: In handleTtsFallback, remove insertText for complete/voice-only (both in !ttsInitialized guard and in the main path) so device-TTS fallback produces audio only.
    status: completed
  - id: verify-voiceonly-textonly
    content: Verify voice-only branch remains audio-only and text-only branch remains text-only (no code change expected).
    status: completed
  - id: build
    content: Run gradlew :app:compileDebugKotlin to confirm the edits build clean.
    status: completed
isProject: false
---

## Goal

Three modes in Step 2 must be strictly differentiated on the output:

- `complete` (Change Language & Voice): produce only the preset-voice MP3 ready to send. Do not type text into the field.
- `voice-only` (Translate My Same Voice): produce only the cloned-voice MP3 ready to send. Do not type text into the field.
- `text-only` (Transcript & Translate): insert translated text only, no audio (already correct from last turn).

All changes are in [`app/src/main/java/com/deltavoice/MainKeyboardService.kt`](app/src/main/java/com/deltavoice/MainKeyboardService.kt). The routing layer (`processRecordedVoice`, backend in [`convex/voiceWorkflow.ts`](convex/voiceWorkflow.ts)) is already correct and does not need changes.

## Why it is "broken" right now

`handleWorkflowSuccess` for `complete` currently calls `insertText(translatedText)` even when cloud audio is present, and `handleTtsFallback` always calls `insertText(text)` for `complete` and `voice-only` before synthesizing with device TTS. That makes the voice modes indistinguishable from the text mode, which the user reported.

## Edits

### 1. `handleWorkflowSuccess` — `complete` branch (around line 9352)

Remove text insertion, keep audio only.

Current:

```9353:9371:app/src/main/java/com/deltavoice/MainKeyboardService.kt
            "complete" -> {
                // Full Conversion: Insert text and show audio
                response.translatedText?.takeIf { it.isNotBlank() }?.let { 
                    insertText(it)
                    android.util.Log.d("DeltaVoice", "Inserted translated text")
                }
                
                if (hasAudio) {
                    android.util.Log.d("DeltaVoice", "Saving and playing audio...")
                    saveAndShowProcessedAudio(audioBase64!!, "mp3")
                    Toast.makeText(this, getString(R.string.ready_tap_hear_send), Toast.LENGTH_LONG).show()
                } else {
                    android.util.Log.w("DeltaVoice", "No audio returned - showing text only result")
                    Toast.makeText(this, getString(R.string.text_translated_no_audio), Toast.LENGTH_LONG).show()
                    // Don't hide UI - let user see the result and retry if needed
                    audioDurationText.text = getString(R.string.status_no_audio)
                    resetButtonState()
                }
            }
```

Change to:

```kotlin
"complete" -> {
    if (hasAudio) {
        saveAndShowProcessedAudio(audioBase64!!, "mp3")
        Toast.makeText(this, getString(R.string.ready_tap_hear_send), Toast.LENGTH_LONG).show()
    } else {
        Toast.makeText(this, getString(R.string.voice_generation_failed_retry), Toast.LENGTH_LONG).show()
        audioDurationText.text = getString(R.string.status_no_audio)
        resetButtonState()
    }
}
```

### 2. `handleWorkflowSuccess` — `voice-only` branch (around line 9373)

Already does not insert text. Keep as-is. Only cosmetic: ensure toast text reflects clone intent (already `R.string.voice_cloned_tap_hear`). No change.

### 3. `handleWorkflowSuccess` — `text-only` branch

Unchanged. Already correct (text insert + hide UI).

### 4. `handleTtsFallback` (line ~9479)

Remove all `insertText(text)` calls on the `complete` / `voice-only` path so the fallback produces audio only. Keep `text-only` behavior as-is.

Current (problematic lines):

```9479:9500:app/src/main/java/com/deltavoice/MainKeyboardService.kt
    private fun handleTtsFallback(text: String, targetLang: String, workflowType: String) {
        if (!ttsInitialized) {
            android.util.Log.w("DeltaVoice", "TTS fallback: TTS not initialized")
            insertText(text)
            Toast.makeText(this, getString(R.string.text_ready_device_voice_unavailable), Toast.LENGTH_LONG).show()
            if (workflowType == "text-only") hideVoiceProcessingUI() else resetButtonState()
            return
        }

        when (workflowType) {
            "text-only" -> {
                insertText(text)
                Toast.makeText(this, getString(R.string.translated_text_inserted), Toast.LENGTH_SHORT).show()
                hideVoiceProcessingUI()
                recordingFilePath = null
                return
            }
        }

        // For complete/voice-only: insert text and synthesize with device TTS
        insertText(text)
        audioDurationText.text = getString(R.string.status_device_voice)
```

Required changes:

- In the `!ttsInitialized` guard, only `insertText` when `workflowType == "text-only"`; for `complete` / `voice-only`, toast a failure and `resetButtonState()` without inserting text.
- Remove the top-level `insertText(text)` right before `audioDurationText.text = getString(R.string.status_device_voice)` (the comment line "For complete/voice-only: insert text and synthesize with device TTS").

The rest of the fallback (`tts.synthesizeToFile` → `saveAndShowProcessedAudio(base64, "wav")`) stays — it arms the send bar with the device-synthesized audio, which is exactly what we want for audio-only modes.

### 5. No changes needed elsewhere

- `processRecordedVoice` correctly forces `voiceStyle = "myvoiceclone"` for `voice-only` (line 1519) and routes to `runCompleteVoiceWorkflow` with the right workflow type.
- Backend [`convex/voiceWorkflow.ts`](convex/voiceWorkflow.ts) already branches correctly for all three workflow types (lines 420–462).
- Video pipeline (`processRecordedVideo`, `VideoConfigActivity`) is a separate feature and is not part of this request.

## Risk / notes

- After these changes, if both cloud TTS and device TTS fail in `complete` / `voice-only`, the user gets no text typed and no audio — only a toast to retry. That matches "only produce the voice" semantics.
- No string resource changes are required. Existing `voice_generation_failed_retry`, `status_no_audio`, `text_ready_device_voice_failed`, etc. continue to apply.