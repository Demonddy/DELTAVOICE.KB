---
name: Differentiate Three Voice Modes
overview: "Fix the three voice processing modes so each produces the correct output: Mode 1 (Change Language & Voice) produces audio only, Mode 2 (Translate My Same Voice) produces cloned-voice audio, and Mode 3 (Transcript & Translate) produces text only."
todos:
  - id: fix-complete-mode
    content: Remove insertText from 'complete' mode success handler so it only produces audio output (ready to send)
    status: completed
  - id: fix-tts-fallback
    content: Update handleTtsFallback to skip text insertion for 'complete' and 'voice-only' modes (audio-only output)
    status: completed
  - id: verify-text-only
    content: Verify 'text-only' mode works correctly (text insertion only, no audio)
    status: completed
isProject: false
---

# Differentiate Three Voice Processing Modes

## Current State

The backend edge function ([`supabase/functions/complete-voice-workflow/index.ts`](supabase/functions/complete-voice-workflow/index.ts)) already branches correctly per mode:
- `"complete"` -> transcribe + translate + TTS with preset voice (Adam, Aria, etc.)
- `"voice-only"` -> transcribe + translate + TTS with freshly cloned voice
- `"text-only"` -> transcribe + translate only (no TTS)

The Android client ([`MainKeyboardService.kt`](app/src/main/java/com/deltavoice/MainKeyboardService.kt)) handles results in `handleWorkflowSuccess` (line ~9189), but has issues.

---

## Task 1: Fix "Change Language & Voice" (complete) -- Audio Output Only

**Problem:** This mode currently inserts translated text into the text field (line 9192-9195) AND produces audio. The user wants ONLY audio output (ready to send to a friend). Also, the user reports audio is not being produced -- likely because the TTS step is failing and the fallback path shows "text translated, no audio" toast instead.

**Changes in `MainKeyboardService.kt`:**
- Remove the `insertText(it)` call in the `"complete"` branch of `handleWorkflowSuccess` (line 9192-9195). This mode should ONLY save the audio file and show the "Send" button.
- Keep the TTS fallback path -- if no cloud audio, use device TTS to synthesize audio from the translated text (this is already handled by lines 9173-9179 which call `handleTtsFallback`). The device TTS fallback already saves audio + shows Send button.
- If audio IS returned from backend: `saveAndShowProcessedAudio` already changes the button to "Send" and auto-plays.

```kotlin
"complete" -> {
    // Change Language & Voice: produce audio ONLY (no text insertion)
    if (hasAudio) {
        saveAndShowProcessedAudio(audioBase64!!, "mp3")
        Toast.makeText(this, getString(R.string.ready_tap_hear_send), Toast.LENGTH_LONG).show()
    } else {
        // No audio from cloud -- should not reach here because TTS fallback handles it above
        Toast.makeText(this, getString(R.string.text_translated_no_audio), Toast.LENGTH_LONG).show()
        audioDurationText.text = getString(R.string.status_no_audio)
        resetButtonState()
    }
}
```

---

## Task 2: Fix "Transcript & Translate" (text-only) -- Text Output Only

**Problem:** This mode already works correctly in code. It inserts translated text and hides the audio UI. No changes needed in the success path.

**Verification only** -- confirm the `"text-only"` branch (lines 9225-9243) does:
- Insert translated text (or original if translation empty)
- Hide voice processing UI
- Clear recordingFilePath
- No audio saved, no "Send" button

No code changes required for this task.

---

## Task 3: Fix "Translate My Same Voice" (voice-only) -- Cloned Voice Audio Only

**Problem:** This mode should produce audio using the user's cloned voice, ready to send. The code path already does this (lines 9210-9222) -- it saves audio and shows "Send" button. However, if voice cloning fails on the backend (ElevenLabs create-voice-clone call), it falls through to the TTS fallback path which inserts text first (line in `handleTtsFallback`).

**Changes in `MainKeyboardService.kt`:**
- In the TTS fallback path for `"voice-only"`, do NOT insert text into the field. Only produce the device TTS audio file and show the "Send" button. The `handleTtsFallback` function likely inserts text before synthesizing -- we need to skip text insertion when the original workflow was `"voice-only"`.

**Check `handleTtsFallback` behavior:**
- If it currently calls `insertText()`, we need to condition that on `workflowType != "voice-only"` and `workflowType != "complete"` (only insert text for `"text-only"` fallback or don't call fallback for text-only at all since text-only doesn't use TTS).

---

## Summary of File Changes

| File | Change |
|------|--------|
| [`MainKeyboardService.kt`](app/src/main/java/com/deltavoice/MainKeyboardService.kt) ~line 9189 | Remove `insertText` from `"complete"` branch |
| [`MainKeyboardService.kt`](app/src/main/java/com/deltavoice/MainKeyboardService.kt) `handleTtsFallback` | Skip text insertion for `"complete"` and `"voice-only"` modes |

The backend edge function needs no changes -- it already correctly produces audio for `complete`/`voice-only` and text-only for `text-only`.
