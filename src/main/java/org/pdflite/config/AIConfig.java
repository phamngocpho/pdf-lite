package org.pdflite.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for AI services.
 * Reads from .pdflite/ai-config.json (in project directory)
 */
public class AIConfig {
    private static final Logger logger = LoggerFactory.getLogger(AIConfig.class);
    private static final String CONFIG_DIR = ".pdflite";
    private static final String CONFIG_FILE = CONFIG_DIR + "/ai-config.json";

    private String groqApiKey = "";
    private String model = "llama-3.3-70b-versatile";
    private String fastModel = "llama-3.1-8b-instant";
    private boolean enabled = true;
    private boolean privacyConsented = false;

    private static AIConfig instance;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static AIConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /**
     * Loads config from file, creates default if not exists.
     */
    public static AIConfig load() {
        Path configPath = Paths.get(CONFIG_FILE);
        
        // Create config directory if not exists
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
        } catch (IOException e) {
            logger.error("Failed to create config directory", e);
        }

        // Load existing config or create default
        if (Files.exists(configPath)) {
            try (Reader reader = new FileReader(configPath.toFile())) {
                AIConfig config = gson.fromJson(reader, AIConfig.class);
                if (config != null) {
                    logger.info("Loaded AI config from {}", CONFIG_FILE);
                    return config;
                }
            } catch (IOException e) {
                logger.error("Failed to load AI config", e);
            }
        }

        // Create default config
        AIConfig defaultConfig = new AIConfig();
        defaultConfig.save();
        logger.info("Created default AI config at {}", CONFIG_FILE);
        return defaultConfig;
    }

    /**
     * Saves config to file.
     */
    public void save() {
        try (Writer writer = new FileWriter(CONFIG_FILE)) {
            gson.toJson(this, writer);
            logger.info("Saved AI config to {}", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("Failed to save AI config", e);
        }
    }

    /**
     * Reloads config from file.
     */
    public static void reload() {
        instance = load();
    }

    /**
     * Gets the config file path.
     */
    public static String getConfigFilePath() {
        return CONFIG_FILE;
    }

    // Getters and setters
    public String getGroqApiKey() {
        return groqApiKey;
    }

    public void setGroqApiKey(String groqApiKey) {
        this.groqApiKey = groqApiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isConfigured() {
        return groqApiKey != null && !groqApiKey.isEmpty();
    }

    public String getFastModel() {
        return fastModel;
    }

    public void setFastModel(String fastModel) {
        this.fastModel = fastModel;
    }

    public boolean isPrivacyConsented() {
        return privacyConsented;
    }

    public void setPrivacyConsented(boolean privacyConsented) {
        this.privacyConsented = privacyConsented;
    }
}
