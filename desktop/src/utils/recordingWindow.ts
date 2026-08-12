import { getCurrentWindow } from "@tauri-apps/api/window";
import {
  LogicalSize,
  PhysicalPosition,
  type PhysicalSize,
} from "@tauri-apps/api/dpi";

const BAR_WIDTH = 420;
const BAR_HEIGHT = 58;

interface SavedWindowState {
  size: PhysicalSize;
  position: PhysicalPosition;
  alwaysOnTop: boolean;
}

let savedState: SavedWindowState | null = null;

export async function enterRecordingWindowMode(): Promise<void> {
  const win = getCurrentWindow();

  if (!savedState) {
    savedState = {
      size: await win.outerSize(),
      position: await win.outerPosition(),
      alwaysOnTop: await win.isAlwaysOnTop(),
    };
  }

  await win.setResizable(false);
  await win.setSize(new LogicalSize(BAR_WIDTH, BAR_HEIGHT));

  const monitor = await win.currentMonitor();
  if (monitor) {
    const scale = monitor.scaleFactor;
    const barW = BAR_WIDTH * scale;
    const barH = BAR_HEIGHT * scale;
    const x = monitor.position.x + (monitor.size.width - barW) / 2;
    const y = monitor.position.y + monitor.size.height - barH - 56;
    await win.setPosition(new PhysicalPosition(Math.round(x), Math.round(y)));
  }

  await win.setAlwaysOnTop(true);
  await win.show();
  await win.setFocus();
}

export async function exitRecordingWindowMode(): Promise<void> {
  const win = getCurrentWindow();

  if (savedState) {
    await win.setSize(savedState.size);
    await win.setPosition(savedState.position);
    await win.setAlwaysOnTop(savedState.alwaysOnTop);
    savedState = null;
  }

  await win.setResizable(true);
}

export async function hideAfterRecordingCancel(): Promise<void> {
  await getCurrentWindow().hide();
}
