use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};
use tauri::{Emitter, Manager};
use tauri_plugin_global_shortcut::{GlobalShortcutExt, Shortcut, ShortcutState};

static LAST_PRESS: Mutex<Option<Instant>> = Mutex::new(None);
static VOICE_CANCEL: Mutex<Option<Arc<AtomicBool>>> = Mutex::new(None);
const DOUBLE_PRESS_MS: u64 = 400;

fn show_main_window(app: &tauri::AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.unminimize();
        let _ = window.set_focus();
    }
}

pub fn register_hotkeys(app: &tauri::App) -> Result<(), Box<dyn std::error::Error>> {
    let shortcut: Shortcut = "ctrl+space".parse()?;

    app.global_shortcut().on_shortcut(shortcut, move |app, _shortcut, event| {
        if event.state != ShortcutState::Pressed {
            return;
        }

        show_main_window(app);

        let now = Instant::now();
        let is_double = {
            let mut last = LAST_PRESS.lock().unwrap();
            let double = last
                .map(|prev| now.duration_since(prev).as_millis() < DOUBLE_PRESS_MS as u128)
                .unwrap_or(false);
            *last = Some(now);
            double
        };

        if is_double {
            // Cancel pending single-press (voice) action.
            if let Some(cancel) = VOICE_CANCEL.lock().unwrap().take() {
                cancel.store(true, Ordering::SeqCst);
            }
            *LAST_PRESS.lock().unwrap() = None;

            if let Some(window) = app.get_webview_window("main") {
                let _ = window.emit("toolbar-toggle", ());
            }
            return;
        }

        // Delay voice action so a quick second press opens the toolbar instead.
        let cancel = Arc::new(AtomicBool::new(false));
        *VOICE_CANCEL.lock().unwrap() = Some(cancel.clone());

        let app_handle = app.clone();
        thread::spawn(move || {
            thread::sleep(Duration::from_millis(DOUBLE_PRESS_MS));
            if cancel.load(Ordering::SeqCst) {
                return;
            }
            VOICE_CANCEL.lock().unwrap().take();
            if let Some(window) = app_handle.get_webview_window("main") {
                let _ = window.emit("voice-record-toggle", ());
            }
        });
    })?;

    Ok(())
}
