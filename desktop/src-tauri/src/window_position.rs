use tauri::{LogicalSize, PhysicalPosition};

#[tauri::command]
pub async fn position_floating_bar(
    window: tauri::WebviewWindow,
    width: f64,
    height: f64,
) -> Result<(), String> {
    window.set_resizable(false).map_err(|e| e.to_string())?;
    window
        .set_size(LogicalSize::new(width, height))
        .map_err(|e| e.to_string())?;

    let monitor = window
        .current_monitor()
        .map_err(|e| e.to_string())?
        .or(window.primary_monitor().map_err(|e| e.to_string())?);

    if let Some(monitor) = monitor {
        let area = monitor.work_area();
        let size = window.outer_size().map_err(|e| e.to_string())?;
        let x = area.position.x + ((area.size.width as i32 - size.width as i32) / 2);
        let y = area.position.y + area.size.height as i32 - size.height as i32;
        window
            .set_position(PhysicalPosition::new(x, y))
            .map_err(|e| e.to_string())?;
    }

    window.set_always_on_top(true).map_err(|e| e.to_string())?;
    window.show().map_err(|e| e.to_string())?;
    window.unminimize().map_err(|e| e.to_string())?;
    window.set_focus().map_err(|e| e.to_string())?;
    Ok(())
}
