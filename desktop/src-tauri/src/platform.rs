use serde::Serialize;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PlatformInfo {
    pub os: &'static str,
    pub voice_hotkey: &'static str,
    pub toolbar_hotkey: &'static str,
    pub paste_shortcut: &'static str,
    pub mod_key: &'static str,
}

pub fn current_os() -> &'static str {
    if cfg!(target_os = "macos") {
        "macos"
    } else if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "linux") {
        "linux"
    } else {
        "other"
    }
}

pub fn platform_info() -> PlatformInfo {
    if cfg!(target_os = "macos") {
        PlatformInfo {
            os: "macos",
            voice_hotkey: "Ctrl+Space",
            toolbar_hotkey: "Ctrl+Space×2",
            paste_shortcut: "Cmd+V",
            mod_key: "Cmd",
        }
    } else {
        PlatformInfo {
            os: current_os(),
            voice_hotkey: "Ctrl+Space",
            toolbar_hotkey: "Ctrl+Space×2",
            paste_shortcut: "Ctrl+V",
            mod_key: "Ctrl",
        }
    }
}

#[tauri::command]
pub fn get_platform_info() -> PlatformInfo {
    platform_info()
}

pub fn voice_hotkey_label() -> &'static str {
    "Ctrl+Space"
}

pub fn tray_tooltip() -> String {
    format!("DeltaVoice - {} to record", voice_hotkey_label())
}
