use enigo::{Direction, Enigo, Key, Keyboard, Settings};
use tauri::Manager;
use std::thread;
use std::time::Duration;

fn paste_modifier_key() -> Key {
    if cfg!(target_os = "macos") {
        Key::Meta
    } else {
        Key::Control
    }
}

/// Write text to clipboard and simulate paste (Cmd+V on macOS, Ctrl+V elsewhere).
#[tauri::command]
pub async fn insert_text_at_cursor(
    app: tauri::AppHandle,
    text: String,
) -> Result<(), String> {
    use tauri_plugin_clipboard_manager::ClipboardExt;

    app.clipboard()
        .write_text(&text)
        .map_err(|e| format!("Clipboard write failed: {e}"))?;

    // Brief delay so the target app regains focus after our window hides
    thread::sleep(Duration::from_millis(150));

    let mut enigo = Enigo::new(&Settings::default())
        .map_err(|e| format!("Enigo init failed: {e}"))?;

    let modifier = paste_modifier_key();

    enigo.key(modifier, Direction::Press)
        .map_err(|e| format!("Key press failed: {e}"))?;
    enigo.key(Key::Unicode('v'), Direction::Click)
        .map_err(|e| format!("Key click failed: {e}"))?;
    enigo.key(modifier, Direction::Release)
        .map_err(|e| format!("Key release failed: {e}"))?;

    Ok(())
}

/// Save audio bytes to a temp file and return the path.
#[tauri::command]
pub async fn save_audio_file(
    app: tauri::AppHandle,
    audio_base64: String,
    filename: String,
) -> Result<String, String> {
    use std::fs;
    use base64::Engine;

    let dir = app
        .path()
        .app_cache_dir()
        .map_err(|e| format!("Cache dir error: {e}"))?;
    fs::create_dir_all(&dir).map_err(|e| format!("Mkdir error: {e}"))?;

    let path = dir.join(&filename);
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(&audio_base64)
        .map_err(|e| format!("Base64 decode error: {e}"))?;

    fs::write(&path, &bytes).map_err(|e| format!("File write error: {e}"))?;

    Ok(path.to_string_lossy().to_string())
}
