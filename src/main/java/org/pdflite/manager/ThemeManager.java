package org.pdflite.manager;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import java.util.Objects;

/**
 * Quản lý giao diện (Theme) cho ứng dụng.
 * Chuyển từ record sang class để quản lý trạng thái tốt hơn.
 */
public class ThemeManager {

    private static final String LIGHT_THEME = "/org/pdflite/light-theme.css";
    private static final String DARK_THEME = "/org/pdflite/dark-theme.css";

    private final Scene mainScene;
    private String currentTheme; // Biến lưu theme hiện tại

    public ThemeManager(Scene mainScene) {
        this.mainScene = mainScene;
        this.currentTheme = LIGHT_THEME;

        if (mainScene != null) {
            applyThemeInternal(mainScene, currentTheme);
        }
    }

    public void setLightTheme() {
        currentTheme = LIGHT_THEME;
        applyThemeInternal(mainScene, currentTheme);
    }

    public void setDarkTheme() {
        currentTheme = DARK_THEME;
        applyThemeInternal(mainScene, currentTheme);
    }

    private void applyThemeInternal(Scene targetScene, String themePath) {
        if (targetScene == null) return;

        try {
            var resource = getClass().getResource(themePath);
            if (resource == null) {
                System.err.println("Lỗi: Không tìm thấy file theme: " + themePath);
                return;
            }

            targetScene.getStylesheets().clear();
            targetScene.getStylesheets().add(resource.toExternalForm());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void applyThemeToScene(Scene targetScene) {
        if (targetScene == null) return;
        applyThemeInternal(targetScene, currentTheme);
    }


    public void applyThemeToDialog(DialogPane dialogPane) {
        if (dialogPane == null) return;

        try {
            dialogPane.getStylesheets().clear();
            var resource = getClass().getResource(currentTheme);
            if (resource != null) {
                dialogPane.getStylesheets().add(resource.toExternalForm());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getCurrentThemeCssPath() {
        return currentTheme;
    }
}