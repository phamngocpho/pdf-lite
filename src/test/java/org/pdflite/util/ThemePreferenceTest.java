package org.pdflite.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pdflite.manager.ThemeManager;
import org.pdflite.manager.UserPreferencesManager;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ThemePreference.
 * Updated to work with unified UserPreferencesManager.
 */
class ThemePreferenceTest {

    private Path preferencesFile;

    @BeforeEach
    void setUp() throws Exception {
        // Reset UserPreferencesManager singleton for clean test state
        resetUserPreferencesManager();
        
        // Preferences file location (unified preferences.json)
        preferencesFile = Paths.get(".pdflite", "preferences.json");
        
        // Clean up before test
        if (Files.exists(preferencesFile)) {
            Files.delete(preferencesFile);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up test file
        if (Files.exists(preferencesFile)) {
            Files.delete(preferencesFile);
        }
        // Reset singleton
        resetUserPreferencesManager();
    }

    /**
     * Resets the UserPreferencesManager singleton for testing.
     */
    private void resetUserPreferencesManager() throws Exception {
        Field instanceField = UserPreferencesManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Test
    void testLoadThemePreferenceWhenFileDoesNotExist() throws Exception {
        // Ensure file doesn't exist before test
        if (Files.exists(preferencesFile)) {
            Files.delete(preferencesFile);
        }
        resetUserPreferencesManager();
        
        // Default theme should be SYSTEM when no preferences file exists
        ThemeManager.ThemeMode mode = ThemePreference.loadThemePreference();
        assertEquals(ThemeManager.ThemeMode.SYSTEM, mode);
    }

    @Test
    void testSaveAndLoadThemePreference() throws Exception {
        resetUserPreferencesManager();
        
        // Save DARK theme
        ThemePreference.saveThemePreference(ThemeManager.ThemeMode.DARK);
        
        // Reset to force reload from file
        resetUserPreferencesManager();
        
        // Load and verify
        ThemeManager.ThemeMode loaded = ThemePreference.loadThemePreference();
        assertEquals(ThemeManager.ThemeMode.DARK, loaded);
    }

    @Test
    void testSaveAndLoadLightTheme() throws Exception {
        resetUserPreferencesManager();
        
        ThemePreference.saveThemePreference(ThemeManager.ThemeMode.LIGHT);
        
        resetUserPreferencesManager();
        
        ThemeManager.ThemeMode loaded = ThemePreference.loadThemePreference();
        assertEquals(ThemeManager.ThemeMode.LIGHT, loaded);
    }

    @Test
    void testSaveAndLoadSystemTheme() throws Exception {
        resetUserPreferencesManager();
        
        ThemePreference.saveThemePreference(ThemeManager.ThemeMode.SYSTEM);
        
        resetUserPreferencesManager();
        
        ThemeManager.ThemeMode loaded = ThemePreference.loadThemePreference();
        assertEquals(ThemeManager.ThemeMode.SYSTEM, loaded);
    }

    @Test
    void testOverwriteExistingPreference() throws Exception {
        resetUserPreferencesManager();
        
        // Save LIGHT
        ThemePreference.saveThemePreference(ThemeManager.ThemeMode.LIGHT);
        assertEquals(ThemeManager.ThemeMode.LIGHT, ThemePreference.loadThemePreference());
        
        // Overwrite with DARK
        ThemePreference.saveThemePreference(ThemeManager.ThemeMode.DARK);
        assertEquals(ThemeManager.ThemeMode.DARK, ThemePreference.loadThemePreference());
    }

    @Test
    void testConfigFileCreated() throws Exception {
        resetUserPreferencesManager();
        
        ThemePreference.saveThemePreference(ThemeManager.ThemeMode.DARK);
        
        // Now uses preferences.json instead of theme.properties
        assertTrue(Files.exists(preferencesFile));
    }
}
