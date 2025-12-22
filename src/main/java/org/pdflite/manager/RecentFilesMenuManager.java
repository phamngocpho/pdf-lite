package org.pdflite.manager;

import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

/**
 * Manages the recent files menu UI.
 * Handles updating the menu and clearing recent files.
 */
public record RecentFilesMenuManager(Menu recentFilesMenu, RecentFilesManager recentFilesManager,
                                     UIStateManager uiStateManager, Consumer<File> onFileSelected,
                                     BiConsumer<File, Boolean> onFileSelectedWithActivate) {
    /**
     * Creates a new RecentFilesMenuManager.
     *
     * @param recentFilesMenu    the recent files menu
     * @param recentFilesManager the recent files manager
     * @param uiStateManager     the UI state manager
     * @param onFileSelected     callback when a file is selected from the menu
     * @param onFileSelectedWithActivate callback with shouldActivate parameter
     */
    public RecentFilesMenuManager {
    }

    /**
     * Updates the recent files menu with current recent files.
     */
    public void updateRecentFilesMenu() {
        if (recentFilesMenu == null || recentFilesManager == null) {
            return;
        }

        recentFilesMenu.getItems().clear();
        List<String> recentFiles = recentFilesManager.getRecentFiles();

        if (recentFiles.isEmpty()) {
            MenuItem noFiles = new MenuItem("No recent files");
            noFiles.setDisable(true);
            recentFilesMenu.getItems().add(noFiles);
        } else {
            for (String filePath : recentFiles) {
                File file = new File(filePath);
                MenuItem item = new MenuItem(file.getName());
                item.setOnAction(e -> onFileSelected.accept(file));
                recentFilesMenu.getItems().add(item);
            }
        }
    }

    /**
     * Clears all recent files.
     */
    public void clearRecentFiles() {
        if (recentFilesManager == null) {
            return;
        }
        recentFilesManager.clearRecentFiles();
        updateRecentFilesMenu();
        uiStateManager.updateStatus("Recent files cleared");
    }

    /**
     * Opens the last opened file if it exists.
     */
    public void openLastFile() {
        if (recentFilesManager == null) {
            return;
        }
        
        // Try to open all previously opened tabs
        List<String> openedTabs = recentFilesManager.getOpenedTabs();
        if (!openedTabs.isEmpty()) {
            // Open all files - only the last one will be activated and rendered
            for (int i = 0; i < openedTabs.size(); i++) {
                String filePath = openedTabs.get(i);
                File file = new File(filePath);
                if (file.exists()) {
                    boolean isLast = (i == openedTabs.size() - 1);
                    if (onFileSelectedWithActivate != null) {
                        onFileSelectedWithActivate.accept(file, isLast);
                    } else {
                        onFileSelected.accept(file);
                    }
                }
            }
        } else {
            // Fallback to single last opened file for backward compatibility
            String lastFile = recentFilesManager.getLastOpenedFile();
            if (lastFile != null) {
                File file = new File(lastFile);
                if (file.exists()) {
                    onFileSelected.accept(file);
                }
            }
        }
    }
}

