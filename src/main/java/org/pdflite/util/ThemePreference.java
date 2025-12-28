package org.pdflite.util;

import org.pdflite.manager.ThemeManager;
import org.pdflite.manager.UserPreferencesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for saving and loading theme preferences.
 * Now delegates to UserPreferencesManager for unified settings storage.
 */
public class ThemePreference {

    private static final Logger logger = LoggerFactory.getLogger(ThemePreference.class);

    /**
     * Saves the theme preference.
     *
     * @param mode the theme mode to save
     */
    public static void saveThemePreference(ThemeManager.ThemeMode mode) {
        try {
            UserPreferencesManager.getInstance().setThemeModeEnum(mode);
            logger.debug("Theme preference saved: {}", mode);
        } catch (Exception e) {
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
            ThemeManager.ThemeMode mode = UserPreferencesManager.getInstance().getThemeModeEnum();
            logger.debug("Theme preference loaded: {}", mode);
            return mode;
        } catch (Exception e) {
            logger.error("Failed to load theme preference, using default: SYSTEM", e);
            return ThemeManager.ThemeMode.SYSTEM;
        }
    }
}
