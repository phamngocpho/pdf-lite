package org.pdflite.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pdflite.manager.ThemeManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ThemePreference.
 */
class ThemePreferenceTest {

    private Path configFile;

    @BeforeEach
    void setUp() {
        // Project directory, not user home
        configFile = Paths.get(".pdflite", "theme.properties");
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up test file
        if (Files.exists(configFile)) {
            Files.delete(configFile);
        }
    }

    @Test
    void testLoadThemePreferenceWhenFileDoesNotExist() throws IOException {
        // Ensure file doesn't exist before test
        if (Files.exists(configFile)) {
            Files.delete(configFile);
        }
        
        ThemeManager.ThemeMode mode = ThemePreference.loadThemePreference();
        assertEquals(ThemeManager.ThemeMode.SYSTEM, mode);
    }

    @Test
    void testSaveAndLoadThemePreference() {
        // Save DARK theme
        ThemePreference.saveThemePreference(ThemeManager.ThemeMode.DARK);
        
        // Load and verify
        ThemeManager.ThemeMode loaded = ThemePreference.loadThemePreference();
        assertEquals(ThemeManager.ThemeMode.DARK, loaded);
    }

    @Test
    void testSaveAndLoadLightTheme() {
        ThemePreference.saveThemePreference(ThemeManager.ThemeMode.LIGHT);
        
        ThemeManager.ThemeMode loaded = ThemePreference.loadThemePreference();
        assertEquals(ThemeManager.ThemeMode.LIGHT, loaded);
    }

    @Test
    void testSaveAndLoadSystemTheme() {
        ThemePreference.saveThemePreference(ThemeManager.ThemeMode.SYSTEM);
        
        ThemeManager.ThemeMode loaded = ThemePreference.loadThemePreference();
        assertEquals(ThemeManager.ThemeMode.SYSTEM, loaded);
    }

    @Test
    void testOverwriteExistingPreference() {
        // Save LIGHT
        ThemePreference.saveThemePreference(ThemeManager.ThemeMode.LIGHT);
        assertEquals(ThemeManager.ThemeMode.LIGHT, ThemePreference.loadThemePreference());
        
        // Overwrite with DARK
        ThemePreference.saveThemePreference(ThemeManager.ThemeMode.DARK);
        assertEquals(ThemeManager.ThemeMode.DARK, ThemePreference.loadThemePreference());
    }

    @Test
    void testConfigFileCreated() {
        ThemePreference.saveThemePreference(ThemeManager.ThemeMode.DARK);
        
        assertTrue(Files.exists(configFile));
    }
}
