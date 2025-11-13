package org.pdflite.manager;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import java.net.URL;

/**
 * ThemeManager quản lý việc áp dụng Light/Dark theme cho toàn bộ ứng dụng.
 * Có thể dùng cho Scene chính và cho các Alert/Dialog riêng.
 */
public class ThemeManager {

    private final Scene scene;
    private String currentTheme;

    // Đường dẫn tới file CSS trong resources
    private static final String LIGHT_THEME = "/org/pdflite/light-theme.css";
    private static final String DARK_THEME  = "/org/pdflite/dark-theme.css";

    public ThemeManager(Scene scene) {
        this.scene = scene;
        // Áp dụng theme mặc định khi khởi tạo (ví dụ Light)
        setLightTheme();
    }

    /**
     * Đặt theme sáng (Light)
     */
    public void setLightTheme() {
        applyTheme(LIGHT_THEME);
    }

    /**
     * Đặt theme tối (Dark)
     */
    public void setDarkTheme() {
        applyTheme(DARK_THEME);
    }

    /**
     * Trả về theme hiện tại (để áp dụng cho Alert hoặc Stage khác)
     */
    public String getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Áp dụng CSS theme vào scene chính
     */
    private void applyTheme(String themePath) {
        if (scene == null) {
            System.err.println("ThemeManager: Scene is null, cannot apply theme.");
            return;
        }

        URL resource = getClass().getResource(themePath);
        if (resource == null) {
            System.err.println("ThemeManager: Theme file not found: " + themePath);
            return;
        }

        // Xóa stylesheet cũ và thêm stylesheet mới
        scene.getStylesheets().clear();
        scene.getStylesheets().add(resource.toExternalForm());
        currentTheme = themePath;
        System.out.println("Applied theme: " + themePath);
    }

    /**
     * Áp dụng theme hiện tại cho Alert/Dialog riêng (qua DialogPane)
     */
    public void applyToDialog(DialogPane dialogPane) {
        if (dialogPane == null) return;

        if (currentTheme != null) {
            URL resource = getClass().getResource(currentTheme);
            if (resource != null) {
                dialogPane.getStylesheets().clear();
                dialogPane.getStylesheets().add(resource.toExternalForm());
            }
        }
    }
}
