package org.pdflite.manager;

import java.io.*;
import java.nio.file.*;
import java.text.MessageFormat;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages application language/localization.
 * Supports switching between languages at runtime.
 */
public class LanguageManager {

    private static final Logger logger = LoggerFactory.getLogger(LanguageManager.class);
    private static final String BUNDLE_BASE_NAME = "org.pdflite.i18n.messages";
    private static final String LANGUAGE_PREF_FILE = ".pdflite/language.txt";

    private static LanguageManager instance;
    private ResourceBundle bundle;
    private Locale currentLocale;
    private final List<Runnable> languageChangeListeners = new ArrayList<>();

    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale VIETNAMESE = Locale.of("vi", "VN");

    private LanguageManager() {
        loadSavedLanguage();
    }

    public static synchronized LanguageManager getInstance() {
        if (instance == null) {
            instance = new LanguageManager();
        }
        return instance;
    }

    /**
     * Loads the saved language preference or defaults to system locale.
     */
    private void loadSavedLanguage() {
        Locale savedLocale = loadLanguagePreference();
        if (savedLocale != null) {
            setLocale(savedLocale, false);
        } else {
            // Default to system locale, fallback to English
            Locale systemLocale = Locale.getDefault();
            if (systemLocale.getLanguage().equals("vi")) {
                setLocale(VIETNAMESE, false);
            } else {
                setLocale(ENGLISH, false);
            }
        }
    }

    /**
     * Sets the current locale and reloads the resource bundle.
     */
    public void setLocale(Locale locale) {
        setLocale(locale, true);
    }

    private void setLocale(Locale locale, boolean notify) {
        this.currentLocale = locale;
        try {
            bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
            logger.info("Language set to: {}", locale.getDisplayLanguage());
            
            if (notify) {
                saveLanguagePreference(locale);
                notifyLanguageChange();
            }
        } catch (MissingResourceException e) {
            logger.error("Could not load resource bundle for locale: {}", locale, e);
            // Fallback to English
            bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, ENGLISH);
        }
    }

    /**
     * Gets a localized string for the given key.
     */
    public String getString(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            logger.warn("Missing translation for key: {}", key);
            return key;
        }
    }

    /**
     * Gets a localized string with parameters.
     */
    public String getString(String key, Object... params) {
        try {
            String pattern = bundle.getString(key);
            return MessageFormat.format(pattern, params);
        } catch (MissingResourceException e) {
            logger.warn("Missing translation for key: {}", key);
            return key;
        }
    }

    /**
     * Gets the current locale.
     */
    public Locale getCurrentLocale() {
        return currentLocale;
    }

    /**
     * Checks if current language is Vietnamese.
     */
    public boolean isVietnamese() {
        return currentLocale != null && currentLocale.getLanguage().equals("vi");
    }

    /**
     * Checks if current language is English.
     */
    public boolean isEnglish() {
        return currentLocale != null && currentLocale.getLanguage().equals("en");
    }

    /**
     * Adds a listener to be notified when language changes.
     */
    public void addLanguageChangeListener(Runnable listener) {
        languageChangeListeners.add(listener);
    }

    /**
     * Removes a language change listener.
     */
    public void removeLanguageChangeListener(Runnable listener) {
        languageChangeListeners.remove(listener);
    }

    /**
     * Notifies all listeners that language has changed.
     */
    private void notifyLanguageChange() {
        for (Runnable listener : languageChangeListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                logger.error("Error notifying language change listener", e);
            }
        }
    }

    /**
     * Saves language preference to file.
     */
    private void saveLanguagePreference(Locale locale) {
        try {
            Path prefFile = Paths.get(System.getProperty("user.dir"), LANGUAGE_PREF_FILE);
            Files.createDirectories(prefFile.getParent());
            Files.writeString(prefFile, locale.toLanguageTag());
            logger.debug("Saved language preference: {}", locale.toLanguageTag());
        } catch (IOException e) {
            logger.error("Failed to save language preference", e);
        }
    }

    /**
     * Loads language preference from file.
     */
    private Locale loadLanguagePreference() {
        try {
            Path prefFile = Paths.get(System.getProperty("user.dir"), LANGUAGE_PREF_FILE);
            if (Files.exists(prefFile)) {
                String tag = Files.readString(prefFile).trim();
                Locale locale = Locale.forLanguageTag(tag);
                logger.debug("Loaded language preference: {}", tag);
                return locale;
            }
        } catch (IOException e) {
            logger.error("Failed to load language preference", e);
        }
        return null;
    }

    /**
     * Gets available locales.
     */
    public List<Locale> getAvailableLocales() {
        return List.of(ENGLISH, VIETNAMESE);
    }
}
