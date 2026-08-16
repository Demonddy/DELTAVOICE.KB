const STORAGE_KEY = "deltavoice_saved_voice_clone";

export interface SavedVoiceClone {
  voiceId: string;
  name: string;
  createdAt: number;
}

export function loadSavedVoiceClone(): SavedVoiceClone | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as SavedVoiceClone;
    if (!parsed?.voiceId) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function saveVoiceClone(clone: SavedVoiceClone): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(clone));
}

export function cloneVoiceStyle(voiceId: string): string {
  return `clone_${voiceId}`;
}
