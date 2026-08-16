import { invoke } from "@tauri-apps/api/core";

export interface PlatformInfo {
  os: string;
  voiceHotkey: string;
  toolbarHotkey: string;
  pasteShortcut: string;
  modKey: string;
}

function fallbackPlatformInfo(): PlatformInfo {
  const isMac = navigator.userAgent.includes("Mac");
  return {
    os: isMac ? "macos" : "windows",
    voiceHotkey: "Ctrl+Space",
    toolbarHotkey: "Ctrl+Space×2",
    pasteShortcut: isMac ? "Cmd+V" : "Ctrl+V",
    modKey: isMac ? "Cmd" : "Ctrl",
  };
}

let cached: PlatformInfo | null = null;

export async function getPlatformInfo(): Promise<PlatformInfo> {
  if (cached) return cached;
  try {
    cached = await invoke<PlatformInfo>("get_platform_info");
  } catch {
    cached = fallbackPlatformInfo();
  }
  return cached;
}

export function getPlatformInfoSync(): PlatformInfo {
  return cached ?? fallbackPlatformInfo();
}

export function isMacOS(): boolean {
  return getPlatformInfoSync().os === "macos";
}
