package org.pdflite.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages all user preferences and settings for the application.
 * Consolidates all settings into a single JSON file.
 */
public class UserPreferencesManager {

    private static final Logger logger = LoggerFactory.getLogger(UserPreferencesManager.class);
    private static final String PREFERENCES_DIR = ".pdflite";
    private static final String PREFERENCES_FILE = ".pdflite/preferences.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static UserPreferencesManager instance;
    private UserPreferences preferences;
    private final List<Runnable> changeListeners = new ArrayList<>();

    /**
     * User preferences data class - contains ALL app settings.
     */
    public static class UserPreferences {
        // Display settings
        private double defaultZoom = 1.0;
        private String fitMode = "none"; // none, fitWidth, fitPage
        
        // Theme settings
        private String themeMode = "SYSTEM"; // LIGHT, DARK, SYSTEM
        
        // Language settings
        private String language = "en"; // en, vi
        
        // Sidebar settings
        private String sidebarPosition = "left"; // left, right
        private boolean sidebarVisible = true;
        private int sidebarWidth = 250;
        
        // Auto-save settings
        private boolean autoSaveEnabled = true;
        private int autoSaveDelaySeconds = 5;
        
        // Recent files settings
        private int maxRecentFiles = 10;
        
        // View settings
        private boolean showToolbar = true;
        private boolean showStatusBar = true;

        // Annotation style settings
        private String annotationDrawingColor = "#FFFFFFFF";
        private String annotationHighlightColor = "#FFFF00FF";
        private double annotationStrokeWidth = 2.0;
        private String annotationLineStyle = "SOLID";
        private double annotationOpacity = 1.0;
        
        // AI settings
        private String aiProvider = "groq";
        private String aiApiKey = "";
        private String aiModel = "llama-3.3-70b-versatile";
        private String aiFastModel = "llama-3.1-8b-instant";
        private boolean aiEnabled = true;
        private boolean aiPrivacyConsented = false;
        
        // Getters and setters
        public double getDefaultZoom() { return defaultZoom; }
        public void setDefaultZoom(double defaultZoom) { this.defaultZoom = defaultZoom; }
        
        public String getFitMode() { return fitMode; }
        public void setFitMode(String fitMode) { this.fitMode = fitMode; }
        
        public String getThemeMode() { return themeMode; }
        public void setThemeMode(String themeMode) { this.themeMode = themeMode; }
        
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        
        public String getSidebarPosition() { return sidebarPosition; }
        public void setSidebarPosition(String sidebarPosition) { this.sidebarPosition = sidebarPosition; }
        
        public boolean isSidebarVisible() { return sidebarVisible; }
        public void setSidebarVisible(boolean sidebarVisible) { this.sidebarVisible = sidebarVisible; }
        
        public int getSidebarWidth() { return sidebarWidth; }
        public void setSidebarWidth(int sidebarWidth) { this.sidebarWidth = sidebarWidth; }
        
        public boolean isAutoSaveEnabled() { return autoSaveEnabled; }
        public void setAutoSaveEnabled(boolean autoSaveEnabled) { this.autoSaveEnabled = autoSaveEnabled; }
        
        public int getAutoSaveDelaySeconds() { return autoSaveDelaySeconds; }
        public void setAutoSaveDelaySeconds(int autoSaveDelaySeconds) { this.autoSaveDelaySeconds = autoSaveDelaySeconds; }
        
        public int getMaxRecentFiles() { return maxRecentFiles; }
        public void setMaxRecentFiles(int maxRecentFiles) { this.maxRecentFiles = maxRecentFiles; }
        
        public boolean isShowToolbar() { return showToolbar; }
        public void setShowToolbar(boolean showToolbar) { this.showToolbar = showToolbar; }
        
        public boolean isShowStatusBar() { return showStatusBar; }
        public void setShowStatusBar(boolean showStatusBar) { this.showStatusBar = showStatusBar; }

        public String getAnnotationDrawingColor() { return annotationDrawingColor; }
        public void setAnnotationDrawingColor(String annotationDrawingColor) { this.annotationDrawingColor = annotationDrawingColor; }

        public String getAnnotationHighlightColor() { return annotationHighlightColor; }
        public void setAnnotationHighlightColor(String annotationHighlightColor) { this.annotationHighlightColor = annotationHighlightColor; }

        public double getAnnotationStrokeWidth() { return annotationStrokeWidth; }
        public void setAnnotationStrokeWidth(double annotationStrokeWidth) { this.annotationStrokeWidth = annotationStrokeWidth; }

        public String getAnnotationLineStyle() { return annotationLineStyle; }
        public void setAnnotationLineStyle(String annotationLineStyle) { this.annotationLineStyle = annotationLineStyle; }

        public double getAnnotationOpacity() { return annotationOpacity; }
        public void setAnnotationOpacity(double annotationOpacity) { this.annotationOpacity = annotationOpacity; }
        
        public String getAiProvider() { return aiProvider; }
        public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }
        
        public String getAiApiKey() { return aiApiKey; }
        public void setAiApiKey(String aiApiKey) { this.aiApiKey = aiApiKey; }
        
        public String getAiModel() { return aiModel; }
        public void setAiModel(String aiModel) { this.aiModel = aiModel; }
        
        public boolean isAiEnabled() { return aiEnabled; }
        public void setAiEnabled(boolean aiEnabled) { this.aiEnabled = aiEnabled; }
        
        public String getAiFastModel() { return aiFastModel; }
        public void setAiFastModel(String aiFastModel) { this.aiFastModel = aiFastModel; }
        
        public boolean isAiPrivacyConsented() { return aiPrivacyConsented; }
        public void setAiPrivacyConsented(boolean aiPrivacyConsented) { this.aiPrivacyConsented = aiPrivacyConsented; }
    }

    private UserPreferencesManager() {
        loadPreferences();
        migrateOldSettings();
    }

    public static synchronized UserPreferencesManager getInstance() {
        if (instance == null) {
            instance = new UserPreferencesManager();
        }
        return instance;
    }

    /**
     * Gets the current preferences.
     */
    public UserPreferences getPreferences() {
        return preferences;
    }

    /**
     * Loads preferences from file.
     */
    private void loadPreferences() {
        try {
            Path prefFile = Paths.get(System.getProperty("user.dir"), PREFERENCES_FILE);
            if (Files.exists(prefFile)) {
                String json = Files.readString(prefFile);
                preferences = gson.fromJson(json, UserPreferences.class);
                if (preferences == null) {
                    preferences = new UserPreferences();
                }
                logger.info("Loaded user preferences from: {}", prefFile);
            } else {
                preferences = new UserPreferences();
                logger.info("Using default preferences");
            }
        } catch (IOException e) {
            logger.error("Failed to load preferences", e);
            preferences = new UserPreferences();
        }
    }

    /**
     * Migrates settings from old separate files to unified preferences.
     */
    private void migrateOldSettings() {
        boolean needsSave = false;
        Path baseDir = Paths.get(System.getProperty("user.dir"), PREFERENCES_DIR);

        // Migrate language.txt
        Path langFile = baseDir.resolve("language.txt");
        if (Files.exists(langFile)) {
            try {
                String lang = Files.readString(langFile).trim();
                if (lang.startsWith("vi")) {
                    preferences.setLanguage("vi");
                } else {
                    preferences.setLanguage("en");
                }
                Files.delete(langFile);
                needsSave = true;
                logger.info("Migrated language setting from language.txt");
            } catch (IOException e) {
                logger.warn("Failed to migrate language.txt", e);
            }
        }

        // Migrate theme.properties
        Path themeFile = baseDir.resolve("theme.properties");
        if (Files.exists(themeFile)) {
            try {
                String content = Files.readString(themeFile);
                if (content.contains("DARK")) {
                    preferences.setThemeMode("DARK");
                } else if (content.contains("LIGHT")) {
                    preferences.setThemeMode("LIGHT");
                } else {
                    preferences.setThemeMode("SYSTEM");
                }
                Files.delete(themeFile);
                needsSave = true;
                logger.info("Migrated theme setting from theme.properties");
            } catch (IOException e) {
                logger.warn("Failed to migrate theme.properties", e);
            }
        }

        // Migrate ai-config.json
        Path aiConfigFile = baseDir.resolve("ai-config.json");
        if (Files.exists(aiConfigFile)) {
            try {
                String json = Files.readString(aiConfigFile);
                // Parse API key
                if (json.contains("\"groqApiKey\"")) {
                    int start = json.indexOf("\"groqApiKey\"") + 14;
                    int end = json.indexOf("\"", start);
                    if (end > start) {
                        String apiKey = json.substring(start, end);
                        if (!apiKey.isEmpty() && !apiKey.equals("YOUR_API_KEY_HERE")) {
                            preferences.setAiApiKey(apiKey);
                        }
                    }
                }
                // Parse model
                if (json.contains("\"model\"")) {
                    int start = json.indexOf("\"model\"") + 9;
                    int end = json.indexOf("\"", start);
                    if (end > start) {
                        preferences.setAiModel(json.substring(start, end));
                    }
                }
                // Parse fastModel
                if (json.contains("\"fastModel\"")) {
                    int start = json.indexOf("\"fastModel\"") + 13;
                    int end = json.indexOf("\"", start);
                    if (end > start) {
                        preferences.setAiFastModel(json.substring(start, end));
                    }
                }
                // Parse enabled
                if (json.contains("\"enabled\"")) {
                    preferences.setAiEnabled(json.contains("\"enabled\": true") || json.contains("\"enabled\":true"));
                }
                // Parse privacyConsented
                if (json.contains("\"privacyConsented\"")) {
                    preferences.setAiPrivacyConsented(json.contains("\"privacyConsented\": true") || json.contains("\"privacyConsented\":true"));
                }
                Files.delete(aiConfigFile);
                needsSave = true;
                logger.info("Migrated AI settings from ai-config.json");
            } catch (IOException e) {
                logger.warn("Failed to migrate ai-config.json", e);
            }
        }

        if (needsSave) {
            savePreferences();
        }
    }

    /**
     * Saves preferences to file.
     */
    public void savePreferences() {
        try {
            Path prefFile = Paths.get(System.getProperty("user.dir"), PREFERENCES_FILE);
            Files.createDirectories(prefFile.getParent());
            String json = gson.toJson(preferences);
            Files.writeString(prefFile, json);
            logger.info("Saved user preferences to: {}", prefFile);
            notifyChangeListeners();
        } catch (IOException e) {
            logger.error("Failed to save preferences", e);
        }
    }

    /**
     * Resets preferences to defaults.
     */
    public void resetToDefaults() {
        preferences = new UserPreferences();
        savePreferences();
        logger.info("Reset preferences to defaults");
    }

    /**
     * Exports preferences to a JSON string.
     */
    public String exportToJson() {
        return gson.toJson(preferences);
    }

    /**
     * Imports preferences from a JSON string.
     */
    public boolean importFromJson(String json) {
        try {
            UserPreferences imported = gson.fromJson(json, UserPreferences.class);
            if (imported != null) {
                preferences = imported;
                savePreferences();
                logger.info("Imported preferences from JSON");
                return true;
            }
        } catch (Exception e) {
            logger.error("Failed to import preferences from JSON", e);
        }
        return false;
    }

    /**
     * Adds a listener to be notified when preferences change.
     */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    /**
     * Removes a change listener.
     */
    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    /**
     * Notifies all listeners that preferences have changed.
     */
    private void notifyChangeListeners() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                logger.error("Error notifying preference change listener", e);
            }
        }
    }

    /**
     * Gets the preferences file path.
     */
    public Path getPreferencesFilePath() {
        return Paths.get(System.getProperty("user.dir"), PREFERENCES_FILE);
    }
    
    // ==================== Convenience methods for ThemeManager ====================
    
    /**
     * Gets the theme mode for ThemeManager compatibility.
     */
    public ThemeManager.ThemeMode getThemeModeEnum() {
        return switch (preferences.getThemeMode()) {
            case "LIGHT" -> ThemeManager.ThemeMode.LIGHT;
            case "DARK" -> ThemeManager.ThemeMode.DARK;
            default -> ThemeManager.ThemeMode.SYSTEM;
        };
    }
    
    /**
     * Sets the theme mode from ThemeManager enum.
     */
    public void setThemeModeEnum(ThemeManager.ThemeMode mode) {
        preferences.setThemeMode(mode.name());
        savePreferences();
    }
    
    // ==================== Convenience methods for LanguageManager ====================
    
    /**
     * Gets the language locale tag.
     */
    public String getLanguageTag() {
        return "vi".equals(preferences.getLanguage()) ? "vi-VN" : "en";
    }
}
