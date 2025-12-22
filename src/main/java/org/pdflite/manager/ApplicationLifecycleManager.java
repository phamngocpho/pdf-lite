package org.pdflite.manager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages application lifecycle operations including startup and shutdown.
 */
public record ApplicationLifecycleManager(FileManager fileManager, AutoSaveManager autoSaveManager,
                                          RecoveryManager recoveryManager, ExecutorService renderExecutor,
                                          ScheduledExecutorService autoSaveExecutor,
                                          TabManager tabManager,
                                          RecentFilesManager recentFilesManager) {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationLifecycleManager.class);

    /**
     * Performs application exit with proper cleanup.
     */
    public void performExit(PDFDocument currentDocument) {
        logger.info("Performing application exit");

        try {
            // Save all opened tabs before closing
            if (tabManager != null && recentFilesManager != null) {
                java.util.List<String> openedTabs = tabManager.getAllOpenedFilePaths();
                recentFilesManager.saveOpenedTabs(openedTabs);
                logger.info("Saved {} opened tabs", openedTabs.size());
            }
            
            // Close document first (important to save state)
            if (currentDocument != null) {
                // If there are unsaved changes, force an auto-save before exit
                if (currentDocument.hasUnsavedEdits()) {
                    recoveryManager.autoSaveBeforeExit(currentDocument);
                } else if (autoSaveManager != null) {
                    // No unsaved changes - clear auto-save
                    autoSaveManager.clearAutoSave(currentDocument);
                }

                fileManager.close(currentDocument);
            }

            // Shutdown executors
            shutdownExecutors();

        } catch (Exception e) {
            logger.error("Error during application exit cleanup", e);
            // Continue with exit even if cleanup fails
        }

        // Force exit immediately
        logger.info("Exiting application");
        System.exit(0);
    }

    /**
     * Shuts down all executor services.
     */
    private void shutdownExecutors() {
        try {
            if (autoSaveManager != null) {
                autoSaveManager.shutdown();
            }

            if (renderExecutor != null && !renderExecutor.isShutdown()) {
                renderExecutor.shutdown();
            }

            if (autoSaveExecutor != null && !autoSaveExecutor.isShutdown()) {
                autoSaveExecutor.shutdown();
            }

            logger.info("All executors shut down successfully");
        } catch (Exception e) {
            logger.warn("Error shutting down executors", e);
        }
    }
}
