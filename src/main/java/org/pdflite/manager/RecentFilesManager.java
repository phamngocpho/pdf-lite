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
    private static final String APP_DATA_DIR = ".pdflite";

    private final List<String> recentFiles = new ArrayList<>();
    private final Path recentFilesPath;

    public RecentFilesManager() {
        String userHome = System.getProperty("user.home");
        Path appDataDir = Paths.get(userHome, APP_DATA_DIR);

        try {
            if (!Files.exists(appDataDir)) {
                Files.createDirectories(appDataDir);
            }
            recentFilesPath = appDataDir.resolve(RECENT_FILES_FILE);
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
        String userHome = System.getProperty("user.home");
        Path lastFilePath = Paths.get(userHome, APP_DATA_DIR, "last-opened.txt");

        try (BufferedWriter writer = Files.newBufferedWriter(lastFilePath)) {
            writer.write(filePath);
        } catch (IOException e) {
            logger.error("Error saving last opened file", e);
        }
    }

    public String getLastOpenedFile() {
        String userHome = System.getProperty("user.home");
        Path lastFilePath = Paths.get(userHome, APP_DATA_DIR, "last-opened.txt");

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
}

