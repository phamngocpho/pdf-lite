package org.pdflite.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RecentFilesManager {
    private static final Logger logger = LoggerFactory.getLogger(RecentFilesManager.class);
    private static final int MAX_RECENT_FILES = 10;
    private static final String RECENT_FILES_FILE = "recent-files.txt";
    private static final String OPENED_TABS_FILE = "opened-tabs.txt";
    private static final String APP_DATA_DIR = ".pdflite";

    private final List<String> recentFiles = new ArrayList<>();
    private final Path recentFilesPath;
    private final Path openedTabsPath;

    public RecentFilesManager() {
        // Store in project directory (current working directory) instead of user home
        String workingDir = System.getProperty("user.dir");
        Path appDataDir = Paths.get(workingDir, APP_DATA_DIR);

        try {
            if (!Files.exists(appDataDir)) {
                Files.createDirectories(appDataDir);
            }
            recentFilesPath = appDataDir.resolve(RECENT_FILES_FILE);
            openedTabsPath = appDataDir.resolve(OPENED_TABS_FILE);
            loadRecentFiles();
        } catch (IOException e) {
            logger.error("Error creating app data directory", e);
            throw new RuntimeException("Failed to initialize recent files manager", e);
        }
    }

    private void loadRecentFiles() {
        if (!Files.exists(recentFilesPath)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(recentFilesPath)) {
            recentFiles.clear();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && Files.exists(Paths.get(line))) {
                    recentFiles.add(line);
                }
            }
            logger.info("Loaded {} recent files", recentFiles.size());
        } catch (IOException e) {
            logger.error("Error loading recent files", e);
        }
    }

    private void saveRecentFiles() {
        try (BufferedWriter writer = Files.newBufferedWriter(recentFilesPath)) {
            for (String filePath : recentFiles) {
                writer.write(filePath);
                writer.newLine();
            }
            logger.info("Saved {} recent files", recentFiles.size());
        } catch (IOException e) {
            logger.error("Error saving recent files", e);
        }
    }

    public void addRecentFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }

        recentFiles.remove(filePath);
        recentFiles.addFirst(filePath);

        if (recentFiles.size() > MAX_RECENT_FILES) {
            recentFiles.removeLast();
        }

        saveRecentFiles();
        saveLastOpenedFile(filePath);
    }

    public List<String> getRecentFiles() {
        return recentFiles.stream()
                .filter(path -> Files.exists(Paths.get(path)))
                .collect(Collectors.toList());
    }

    public void clearRecentFiles() {
        recentFiles.clear();
        saveRecentFiles();
    }

    private void saveLastOpenedFile(String filePath) {
        String workingDir = System.getProperty("user.dir");
        Path lastFilePath = Paths.get(workingDir, APP_DATA_DIR, "last-opened.txt");

        try (BufferedWriter writer = Files.newBufferedWriter(lastFilePath)) {
            writer.write(filePath);
        } catch (IOException e) {
            logger.error("Error saving last opened file", e);
        }
    }

    public String getLastOpenedFile() {
        String workingDir = System.getProperty("user.dir");
        Path lastFilePath = Paths.get(workingDir, APP_DATA_DIR, "last-opened.txt");

        if (!Files.exists(lastFilePath)) {
            return null;
        }

        try (BufferedReader reader = Files.newBufferedReader(lastFilePath)) {
            String filePath = reader.readLine();
            if (filePath != null && !filePath.isEmpty() && Files.exists(Paths.get(filePath))) {
                return filePath;
            }
        } catch (IOException e) {
            logger.error("Error loading last opened file", e);
        }

        return null;
    }

    /**
     * Saves all currently opened tabs to a file.
     * @param openedFiles list of file paths for all opened tabs
     */
    public void saveOpenedTabs(List<String> openedFiles) {
        try (BufferedWriter writer = Files.newBufferedWriter(openedTabsPath)) {
            for (String filePath : openedFiles) {
                writer.write(filePath);
                writer.newLine();
            }
            logger.info("Saved {} opened tabs", openedFiles.size());
        } catch (IOException e) {
            logger.error("Error saving opened tabs", e);
        }
    }

    /**
     * Loads all previously opened tabs from file.
     * @return list of file paths that were opened, or empty list if none
     */
    public List<String> getOpenedTabs() {
        List<String> openedTabs = new ArrayList<>();
        
        if (!Files.exists(openedTabsPath)) {
            return openedTabs;
        }

        try (BufferedReader reader = Files.newBufferedReader(openedTabsPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && Files.exists(Paths.get(line))) {
                    openedTabs.add(line);
                }
            }
            logger.info("Loaded {} opened tabs", openedTabs.size());
        } catch (IOException e) {
            logger.error("Error loading opened tabs", e);
        }

        return openedTabs;
    }
}

