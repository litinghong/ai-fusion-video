#[cfg(debug_assertions)]
use tauri::Manager;

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(|_app| {
            #[cfg(debug_assertions)]
            if let Some(window) = _app.get_webview_window("main") {
                window.open_devtools();
            }

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("failed to run AI Fusion Video desktop app");
}
