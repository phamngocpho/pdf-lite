package org.pdflite.manager;

import javafx.scene.Scene;

public record ThemeManager(Scene mainScene) { // Đổi tên 'scene' thành 'mainScene' để rõ ràng hơn
    private static final String LIGHT_THEME = "/org/pdflite/light-theme.css"; // Đảm bảo đường dẫn đúng
    private static final String DARK_THEME = "/org/pdflite/dark-theme.css";   // Đảm bảo đường dẫn đúng

    // NEW: Thêm một trường để lưu trữ theme CSS hiện tại được áp dụng
    private static String currentThemeCssPath = LIGHT_THEME; // Mặc định là LIGHT_THEME

    // Constructor chuẩn của record.
    // Thêm logic khởi tạo ban đầu cho mainScene (đã có) và áp dụng theme mặc định.
    public ThemeManager(Scene mainScene) {
        this.mainScene = mainScene;
        // Áp dụng theme mặc định khi ThemeManager được tạo cho mainScene
        if (mainScene != null) {
            applyThemeInternal(mainScene, currentThemeCssPath);
        }
    }

    public void setLightTheme() {
        currentThemeCssPath = LIGHT_THEME; // Cập nhật theme hiện tại
        applyThemeInternal(mainScene, currentThemeCssPath); // Áp dụng cho mainScene
    }

    public void setDarkTheme() {
        currentThemeCssPath = DARK_THEME; // Cập nhật theme hiện tại
        applyThemeInternal(mainScene, currentThemeCssPath); // Áp dụng cho mainScene
    }

    // Phương thức private nội bộ để thực hiện việc áp dụng CSS
    private void applyThemeInternal(Scene targetScene, String themePath) {
        if (targetScene == null) return;

        var resource = getClass().getResource(themePath);
        if (resource == null) {
            System.err.println("Theme file not found: " + themePath);
            return;
        }

        targetScene.getStylesheets().clear();
        targetScene.getStylesheets().add(resource.toExternalForm());
    }

    // NEW: Phương thức public để áp dụng theme cho MỘT SCENE BẤT KỲ từ bên ngoài.
    // Phương thức này sẽ được MainController gọi cho Alert/Dialog
    public void applyThemeToScene(Scene targetScene) {
        if (targetScene == null) return;
        applyThemeInternal(targetScene, currentThemeCssPath); // Dùng theme hiện tại đã lưu
    }

    // NEW: Getter để UIStateManager (hoặc các nơi khác) có thể lấy đường dẫn CSS theme hiện tại
    public String getCurrentThemeCssPath() {
        return currentThemeCssPath;
    }
}