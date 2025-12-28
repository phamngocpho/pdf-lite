package org.pdflite.config;

import org.pdflite.manager.UserPreferencesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for AI services.
 * Now delegates to UserPreferencesManager for unified settings storage.
 */
public class AIConfig {
    private static final Logger logger = LoggerFactory.getLogger(AIConfig.class);

    private static AIConfig instance;

    public static AIConfig getInstance() {
        if (instance == null) {
            instance = new AIConfig();
        }
        return instance;
    }

    /**
     * Loads config (now from UserPreferencesManager).
     */
    public static AIConfig load() {
        return getInstance();
    }

    /**
     * Saves config to UserPreferencesManager.
     */
    public void save() {
        UserPreferencesManager.getInstance().savePreferences();
        logger.debug("AI config saved via UserPreferencesManager");
    }

    /**
     * Reloads config.
     */
    public static void reload() {
        // UserPreferencesManager handles this
        logger.debug("AI config reload requested");
    }

    /**
     * Gets the config file path.
     */
    public static String getConfigFilePath() {
        return UserPreferencesManager.getInstance().getPreferencesFilePath().toString();
    }

    // Getters and setters - delegate to UserPreferencesManager
    public String getGroqApiKey() {
        return UserPreferencesManager.getInstance().getPreferences().getAiApiKey();
    }

    public void setGroqApiKey(String groqApiKey) {
        UserPreferencesManager.getInstance().getPreferences().setAiApiKey(groqApiKey);
    }

    public String getModel() {
        return UserPreferencesManager.getInstance().getPreferences().getAiModel();
    }

    public void setModel(String model) {
        UserPreferencesManager.getInstance().getPreferences().setAiModel(model);
    }

    public boolean isEnabled() {
        return UserPreferencesManager.getInstance().getPreferences().isAiEnabled();
    }

    public void setEnabled(boolean enabled) {
        UserPreferencesManager.getInstance().getPreferences().setAiEnabled(enabled);
    }

    public boolean isConfigured() {
        String apiKey = getGroqApiKey();
        return apiKey != null && !apiKey.isEmpty();
    }

    public String getFastModel() {
        return UserPreferencesManager.getInstance().getPreferences().getAiFastModel();
    }

    public void setFastModel(String fastModel) {
        UserPreferencesManager.getInstance().getPreferences().setAiFastModel(fastModel);
    }

    public boolean isPrivacyConsented() {
        return UserPreferencesManager.getInstance().getPreferences().isAiPrivacyConsented();
    }

    public void setPrivacyConsented(boolean privacyConsented) {
        UserPreferencesManager.getInstance().getPreferences().setAiPrivacyConsented(privacyConsented);
        save();
    }
}
