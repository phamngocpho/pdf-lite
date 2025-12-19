package org.pdflite.util;

import org.pdflite.manager.ThemeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Utility class for saving and loading theme preferences.
 */
public class ThemePreference {

    private static final Logger logger = LoggerFactory.getLogger(ThemePreference.class);
    private static final String PREFERENCE_FILE = ".pdflite/theme.properties";
    private static final String THEME_MODE_KEY = "theme.mode";

    /**
     * Saves the theme preference.
     *
     * @param mode the theme mode to save
     */
    public static void saveThemePreference(ThemeManager.ThemeMode mode) {
        try {
            File configDir = new File(System.getProperty("user.home"), ".pdflite");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            File configFile = new File(configDir, "theme.properties");
            Properties props = new Properties();
            props.setProperty(THEME_MODE_KEY, mode.name());

            try (FileOutputStream out = new FileOutputStream(configFile)) {
                props.store(out, "PDF Lite Theme Preferences");
            }

            logger.info("Theme preference saved: {}", mode);
        } catch (IOException e) {
            logger.error("Failed to save theme preference", e);
        }
    }

    /**
     * Loads the saved theme preference.
     *
     * @return the saved theme mode, or SYSTEM if not found
     */
    public static ThemeManager.ThemeMode loadThemePreference() {
        try {
            File configFile = new File(System.getProperty("user.home"), PREFERENCE_FILE);
            if (!configFile.exists()) {
                logger.debug("No theme preference file found, using default: SYSTEM");
                return ThemeManager.ThemeMode.SYSTEM;
            }

            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(configFile)) {
                props.load(in);
            }

            String modeStr = props.getProperty(THEME_MODE_KEY, "SYSTEM");
            ThemeManager.ThemeMode mode = ThemeManager.ThemeMode.valueOf(modeStr);
            logger.info("Theme preference loaded: {}", mode);
            return mode;
        } catch (Exception e) {
            logger.error("Failed to load theme preference, using default: SYSTEM", e);
            return ThemeManager.ThemeMode.SYSTEM;
        }
    }
}
