package org.pdflite.manager;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.pdflite.dialog.RecoveryDialog;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages document recovery operations including auto-save file handling
 * and recovery dialog presentation.
 */
public record RecoveryManager(AutoSaveManager autoSaveManager, UIStateManager uiStateManager,
                              ThemeManager themeManager) {

    private static final Logger logger = LoggerFactory.getLogger(RecoveryManager.class);

    /**
     * Checks for recovery files on startup and prompts user if found.
     *
     * @param onRecoveryCallback Callback to open recovered file
     */
    public void checkForRecovery(RecoveryCallback onRecoveryCallback) {
        if (autoSaveManager == null || !autoSaveManager.hasRecoveryFiles()) {
            return;
        }

        try {
            File[] recoveryFiles = autoSaveManager.getRecoveryFiles();
            if (recoveryFiles.length == 0) {
                return;
            }

            // Show recovery dialog
            RecoveryDialog dialog = new RecoveryDialog(recoveryFiles, themeManager);

            if (dialog.showAndWait()) {
                // User chose to recover
                File fileToRecover = dialog.getSelectedFile();
                if (fileToRecover != null && fileToRecover.exists()) {
                    // Copy auto-save file to a temp location before opening
                    File tempFile = new File(System.getProperty("java.io.tmpdir"),
                            "pdflite_recovery_" + System.currentTimeMillis() + ".pdf");
                    Files.copy(fileToRecover.toPath(), tempFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);

                    // Callback to open the file
                    onRecoveryCallback.onRecover(tempFile);

                    uiStateManager.updateStatus("Document recovered from auto-save - Save to keep changes");
                    logger.info("Recovered document: {}", fileToRecover.getName());

                    // Clean up the auto-save file after successful recovery
                    cleanupRecoveryFile(fileToRecover);
                }
            } else {
                // User chose to discard - clean up recovery files
                discardRecoveryFiles(recoveryFiles);
            }
        } catch (Exception e) {
            logger.error("Error during recovery check", e);
        }
    }

    /**
     * Generates auto-save file name for a document.
     */
    public String getAutoSaveFileName(PDFDocument document) {
        if (document.getFile() != null) {
            String originalName = document.getFile().getName();
            String baseName = originalName.replaceFirst("[.][^.]+$", "");
            return "autosave_" + baseName + "_" + System.identityHashCode(document) + ".pdf";
        }
        return "autosave_untitled_" + System.identityHashCode(document) + ".pdf";
    }

    /**
     * Performs auto-save before exit if there are unsaved changes.
     */
    public void autoSaveBeforeExit(PDFDocument document) {
        if (document == null || !document.hasUnsavedEdits()) {
            return;
        }

        logger.info("Forcing auto-save before exit (unsaved changes)");
        try {
            File autoSaveFile = new File(".pdflite/autosave", getAutoSaveFileName(document));
            File metadataFile = new File(autoSaveFile.getAbsolutePath() + ".meta");

            // Save the document
            document.getDocument().save(autoSaveFile);

            // Save metadata
            try (FileWriter writer = new FileWriter(metadataFile)) {
                writer.write("timestamp=" + System.currentTimeMillis() + "\n");
                if (document.getFile() != null) {
                    writer.write("originalPath=" + document.getFile().getAbsolutePath() + "\n");
                }
                writer.write("pages=" + document.getTotalPages() + "\n");
            }

            logger.info("Auto-save completed before exit");
        } catch (Exception e) {
            logger.error("Failed to auto-save before exit", e);
        }
    }

    /**
     * Cleans up a single recovery file and its metadata.
     */
    private void cleanupRecoveryFile(File file) {
        try {
            Files.delete(file.toPath());
            File metaFile = new File(file.getAbsolutePath() + ".meta");
            if (metaFile.exists()) {
                Files.delete(metaFile.toPath());
            }
        } catch (Exception e) {
            logger.warn("Failed to delete recovery file after recovery: {}", file.getName(), e);
        }
    }

    /**
     * Discards all recovery files.
     */
    private void discardRecoveryFiles(File[] files) {
        for (File file : files) {
            try {
                Files.delete(file.toPath());
                File metaFile = new File(file.getAbsolutePath() + ".meta");
                if (metaFile.exists()) {
                    Files.delete(metaFile.toPath());
                }
            } catch (Exception e) {
                logger.warn("Failed to delete recovery file: {}", file.getName(), e);
            }
        }
        logger.info("Discarded {} recovery files", files.length);
    }

    /**
     * Callback interface for recovery operations.
     */
    @FunctionalInterface
    public interface RecoveryCallback {
        void onRecover(File file);
    }
}
