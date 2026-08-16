mod hotkeys;
mod insertion;
mod platform;
mod window_position;

use tauri::Manager;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .plugin(tauri_plugin_clipboard_manager::init())
        .plugin(tauri_plugin_shell::init())
        .invoke_handler(tauri::generate_handler![
            insertion::insert_text_at_cursor,
            insertion::save_audio_file,
            window_position::position_floating_bar,
            platform::get_platform_info,
        ])
        .setup(|app| {
            hotkeys::register_hotkeys(app)?;
            setup_tray(app)?;
            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running DeltaVoice desktop");
}

fn setup_tray(app: &tauri::App) -> Result<(), Box<dyn std::error::Error>> {
    use tauri::menu::{MenuBuilder, MenuItemBuilder};
    use tauri::tray::TrayIconEvent;

    let show = MenuItemBuilder::with_id("show", "Show DeltaVoice").build(app)?;
    let quit = MenuItemBuilder::with_id("quit", "Quit").build(app)?;

    let menu = MenuBuilder::new(app)
        .item(&show)
        .separator()
        .item(&quit)
        .build()?;

    if let Some(tray) = app.tray_by_id("main") {
        tray.set_tooltip(Some(&platform::tray_tooltip()))?;
        tray.set_menu(Some(menu))?;
        tray.on_menu_event(move |app, event| match event.id().as_ref() {
            "show" => {
                if let Some(w) = app.get_webview_window("main") {
                    let _ = w.show();
                    let _ = w.set_focus();
                }
            }
            "quit" => std::process::exit(0),
            _ => {}
        });
        tray.on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::DoubleClick { .. } = event {
                if let Some(w) = tray.app_handle().get_webview_window("main") {
                    let _ = w.show();
                    let _ = w.set_focus();
                }
            }
        });
    }

    Ok(())
}
