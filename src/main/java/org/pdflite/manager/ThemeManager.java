package org.pdflite.manager;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quản lý giao diện (Theme) cho ứng dụng.
 * Hỗ trợ Light, Dark và System theme (theo hệ thống).
 */
public class ThemeManager {

    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);

    private static final String LIGHT_THEME = "/org/pdflite/light-theme.css";
    private static final String DARK_THEME = "/org/pdflite/dark-theme.css";
    private static final String LOGO_LIGHT = "/org/pdflite/images/logo_light.png";
    private static final String LOGO_DARK = "/org/pdflite/images/logo_dark.png";

    /**
     * Theme mode enum
     */
    public enum ThemeMode {
        LIGHT,
        DARK,
        SYSTEM
    }

    private final Scene mainScene;
    private final ImageView logoImageView;
    private String currentTheme; // CSS path hiện tại
    private ThemeMode themeMode; // Mode hiện tại

    // Theme menu items (optional)
    private RadioMenuItem systemThemeItem;
    private RadioMenuItem lightThemeItem;
    private RadioMenuItem darkThemeItem;

    public ThemeManager(Scene mainScene, ImageView logoImageView) {
        this.mainScene = mainScene;
        this.logoImageView = logoImageView;

        // Load saved preference
        this.themeMode = org.pdflite.util.ThemePreference.loadThemePreference();

        // Apply theme based on mode
        if (themeMode == ThemeMode.SYSTEM) {
            this.currentTheme = detectSystemTheme();
        } else if (themeMode == ThemeMode.DARK) {
            this.currentTheme = DARK_THEME;
        } else {
            this.currentTheme = LIGHT_THEME;
        }

        if (mainScene != null) {
            applyThemeInternal(mainScene, currentTheme);
        }

        updateLogo();

        logger.info("ThemeManager initialized with mode: {} (theme: {})",
                themeMode, currentTheme.contains("dark") ? "Dark" : "Light");
    }

    /**
     * Sets light theme explicitly.
     */
    public void setLightTheme() {
        themeMode = ThemeMode.LIGHT;
        currentTheme = LIGHT_THEME;
        applyThemeInternal(mainScene, currentTheme);
        savePreference();
        updateThemeMenuGraphics();
        logger.info("Theme set to: Light");
    }

    /**
     * Sets dark theme explicitly.
     */
    public void setDarkTheme() {
        themeMode = ThemeMode.DARK;
        currentTheme = DARK_THEME;
        applyThemeInternal(mainScene, currentTheme);
        savePreference();
        updateThemeMenuGraphics();
        logger.info("Theme set to: Dark");
    }

    /**
     * Sets theme to follow system preference.
     */
    public void setSystemTheme() {
        themeMode = ThemeMode.SYSTEM;
        currentTheme = detectSystemTheme();
        applyThemeInternal(mainScene, currentTheme);
        savePreference();
        updateThemeMenuGraphics();
        logger.info("Theme set to: System (detected: {})", currentTheme.contains("dark") ? "Dark" : "Light");
    }

    /**
     * Saves the current theme preference.
     */
    private void savePreference() {
        try {
            org.pdflite.util.ThemePreference.saveThemePreference(themeMode);
        } catch (Exception e) {
            logger.warn("Failed to save theme preference", e);
        }
    }

    /**
     * Detects the system theme preference.
     *
     * @return the CSS path for the detected theme
     */
    private String detectSystemTheme() {
        try {
            // Windows: Check registry for dark mode
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                return detectWindowsTheme();
            }

            // macOS: Check system appearance
            if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                return detectMacOSTheme();
            }

            // Linux: Check GTK theme or environment variables
            if (System.getProperty("os.name").toLowerCase().contains("linux")) {
                return detectLinuxTheme();
            }
        } catch (Exception e) {
            logger.warn("Failed to detect system theme, defaulting to light", e);
        }

        // Default to light theme if detection fails
        return LIGHT_THEME;
    }

    /**
     * Detects Windows theme by checking registry.
     */
    private String detectWindowsTheme() {
        try {
            Process process = Runtime.getRuntime().exec(
                    "reg query HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize /v AppsUseLightTheme"
            );

            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("AppsUseLightTheme")) {
                    // If value is 0x0, dark mode is enabled
                    boolean isDark = line.contains("0x0");
                    logger.debug("Windows theme detected: {}", isDark ? "Dark" : "Light");
                    return isDark ? DARK_THEME : LIGHT_THEME;
                }
            }
        } catch (Exception e) {
            logger.debug("Could not detect Windows theme", e);
        }

        return LIGHT_THEME;
    }

    /**
     * Detects macOS theme using AppleScript.
     */
    private String detectMacOSTheme() {
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"defaults", "read", "-g", "AppleInterfaceStyle"}
            );

            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
            );

            String result = reader.readLine();
            boolean isDark = "Dark".equalsIgnoreCase(result);
            logger.debug("macOS theme detected: {}", isDark ? "Dark" : "Light");
            return isDark ? DARK_THEME : LIGHT_THEME;
        } catch (Exception e) {
            logger.debug("Could not detect macOS theme (likely Light mode)", e);
            return LIGHT_THEME;
        }
    }

    /**
     * Detects Linux theme by checking GTK settings.
     */
    private String detectLinuxTheme() {
        try {
            // Check GTK theme name
            String gtkTheme = System.getenv("GTK_THEME");
            if (gtkTheme != null && gtkTheme.toLowerCase().contains("dark")) {
                logger.debug("Linux theme detected from GTK_THEME: Dark");
                return DARK_THEME;
            }

            // Check color scheme preference
            String colorScheme = System.getenv("GTK_COLOR_SCHEME");
            if (colorScheme != null && colorScheme.toLowerCase().contains("dark")) {
                logger.debug("Linux theme detected from GTK_COLOR_SCHEME: Dark");
                return DARK_THEME;
            }
        } catch (Exception e) {
            logger.debug("Could not detect Linux theme", e);
        }

        return LIGHT_THEME;
    }

    /**
     * Gets the current theme mode.
     */
    public ThemeMode getThemeMode() {
        return themeMode;
    }

    /**
     * Checks if current theme is dark.
     */
    public boolean isDarkTheme() {
        return currentTheme.equals(DARK_THEME);
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
            updateLogo();
        } catch (Exception e) {
            logger.warn("Could not apply theme: {}", e.getMessage());
        }
    }

    private void updateLogo() {
        if (logoImageView == null) return;

        try {
            String logoPath = currentTheme.equals(DARK_THEME) ? LOGO_DARK : LOGO_LIGHT;
            var logoUrl = getClass().getResource(logoPath);
            if (logoUrl != null) {
                logoImageView.setImage(new Image(logoUrl.toExternalForm()));
            }
        } catch (Exception e) {
            logger.warn("Could not update logo: " + e.getMessage());
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
            logger.warn("Could not apply theme to dialog: {}", e.getMessage());
        }
    }

    public String getCurrentThemeCssPath() {
        return currentTheme;
    }

    /**
     * Sets the theme menu items for automatic updates.
     */
    public void setThemeMenuItems(RadioMenuItem systemThemeItem,
                                  RadioMenuItem lightThemeItem,
                                  RadioMenuItem darkThemeItem) {
        this.systemThemeItem = systemThemeItem;
        this.lightThemeItem = lightThemeItem;
        this.darkThemeItem = darkThemeItem;

        // Set selected based on loaded theme mode
        systemThemeItem.setSelected(themeMode == ThemeMode.SYSTEM);
        lightThemeItem.setSelected(themeMode == ThemeMode.LIGHT);
        darkThemeItem.setSelected(themeMode == ThemeMode.DARK);

        // Initial update
        updateThemeMenuGraphics();
    }

    /**
     * Updates theme menu graphics with bullet indicators.
     */
    public void updateThemeMenuGraphics() {
        if (systemThemeItem == null || lightThemeItem == null || darkThemeItem == null) {
            return;
        }

        // Create bullet graphic for selected item
        Circle systemBullet = systemThemeItem.isSelected() ? new Circle(3, Color.web("#0A84FF")) : null;
        Circle lightBullet = lightThemeItem.isSelected() ? new Circle(3, Color.web("#0A84FF")) : null;
        Circle darkBullet = darkThemeItem.isSelected() ? new Circle(3, Color.web("#0A84FF")) : null;

        // Set graphics - bullet for selected, null for others
        systemThemeItem.setGraphic(systemBullet);
        lightThemeItem.setGraphic(lightBullet);
        darkThemeItem.setGraphic(darkBullet);
    }
}