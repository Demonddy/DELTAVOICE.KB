import { invoke } from "@tauri-apps/api/core";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { type PhysicalSize, type PhysicalPosition } from "@tauri-apps/api/dpi";

export type FloatingBarMode = "recording" | "toolbar" | "step2";

const BAR_SIZES: Record<FloatingBarMode, { width: number; height: number }> = {
  recording: { width: 420, height: 58 },
  toolbar: { width: 420, height: 72 },
  step2: { width: 420, height: 640 },
};

interface SavedWindowState {
  size: PhysicalSize;
  position: PhysicalPosition;
  alwaysOnTop: boolean;
}

let savedState: SavedWindowState | null = null;

export async function enterFloatingBarMode(mode: FloatingBarMode): Promise<void> {
  const win = getCurrentWindow();
  const { width, height } = BAR_SIZES[mode];

  if (!savedState) {
    savedState = {
      size: await win.outerSize(),
      position: await win.outerPosition(),
      alwaysOnTop: await win.isAlwaysOnTop(),
    };
  }

  await invoke("position_floating_bar", { width, height });
}

export async function exitFloatingBarMode(): Promise<void> {
  const win = getCurrentWindow();

  if (savedState) {
    await win.setSize(savedState.size);
    await win.setPosition(savedState.position);
    await win.setAlwaysOnTop(savedState.alwaysOnTop);
    savedState = null;
  }

  await win.setResizable(true);
  await win.show();
  await win.unminimize();
  await win.setFocus();
}

export async function showMainWindow(): Promise<void> {
  const win = getCurrentWindow();
  await win.show();
  await win.unminimize();
  await win.setFocus();
}

export async function hideFloatingBar(): Promise<void> {
  await getCurrentWindow().hide();
}

/** Switch between floating bar sizes without restoring the main window. */
export async function resizeFloatingBar(mode: FloatingBarMode): Promise<void> {
  const { width, height } = BAR_SIZES[mode];
  await invoke("position_floating_bar", { width, height });
}

/** @deprecated use enterFloatingBarMode("recording") */
export const enterRecordingWindowMode = () => enterFloatingBarMode("recording");
/** @deprecated use exitFloatingBarMode */
export const exitRecordingWindowMode = exitFloatingBarMode;
/** @deprecated use hideFloatingBar */
export const hideAfterRecordingCancel = hideFloatingBar;
