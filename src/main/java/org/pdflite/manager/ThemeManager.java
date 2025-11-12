package org.pdflite.manager;

import javafx.scene.Scene;

public class ThemeManager {
    private final Scene scene;

    private static final String LIGHT_THEME = "/org/pdflite/light-theme.css";
    private static final String DARK_THEME = "/org/pdflite/dark-theme.css";

    public ThemeManager(Scene scene) {
        this.scene = scene;
    }

    public void setLightTheme() {
        applyTheme(LIGHT_THEME);
    }

    public void setDarkTheme() {
        applyTheme(DARK_THEME);
    }

    private void applyTheme(String themePath) {
        if (scene == null) return;

        var resource = getClass().getResource(themePath);
        if (resource == null) {
            System.err.println(" Theme file not found: " + themePath);
            return;
        }

        scene.getStylesheets().clear();
        scene.getStylesheets().add(resource.toExternalForm());
    }
}
