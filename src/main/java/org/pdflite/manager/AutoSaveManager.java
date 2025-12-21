package org.pdflite.manager;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manager for auto-saving documents and recovery.
 * Automatically saves document changes after a debounce period.
 */
public class AutoSaveManager {
    private static final Logger logger = LoggerFactory.getLogger(AutoSaveManager.class);
    private static final int DEBOUNCE_SECONDS = 5; // Wait 5 seconds after last change
    private static final String AUTOSAVE_DIR = ".pdflite/autosave";

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pendingSaveTask;
    private PDFDocument currentDocument;
    private boolean enabled = true;
    private Runnable onAutoSaveCallback;

    public AutoSaveManager(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
        ensureAutoSaveDirectoryExists();
    }

    /**
     * Sets a callback to be called after successful auto-save.
     *
     * @param callback the callback to run
     */
    public void setOnAutoSaveCallback(Runnable callback) {
        this.onAutoSaveCallback = callback;
    }

    /**
     * Sets the current document to auto-save.
     * Clears any previous auto-save for the old document.
     *
     * @param document the document to auto-save
     */
    public void setDocument(PDFDocument document) {
        // Cancel pending save for old document
        cancelPendingSave();

        // Clear old auto-save if switching documents
        if (currentDocument != null && currentDocument != document) {
            clearAutoSave(currentDocument);
        }

        this.currentDocument = document;
    }

    /**
     * Schedules an auto-save after the debounce period.
     * If called again before the period expires, resets the timer.
     */
    public void scheduleAutoSave() {
        if (!enabled || currentDocument == null) {
            return;
        }

        // CRITICAL: Capture the document reference NOW, not when the task executes
        // This prevents saving the wrong document if user switches tabs before auto-save runs
        final PDFDocument documentToSave = currentDocument;

        // Cancel any pending save
        cancelPendingSave();

        // Schedule new save after debounce period
        pendingSaveTask = scheduler.schedule(() -> {
            try {
                performAutoSave(documentToSave);
            } catch (Exception e) {
                logger.error("Error during auto-save", e);
            }
        }, DEBOUNCE_SECONDS, TimeUnit.SECONDS);

        logger.debug("Auto-save scheduled in {} seconds for document: {}", 
            DEBOUNCE_SECONDS, 
            documentToSave.getFile() != null ? documentToSave.getFile().getName() : "unknown");
    }

    /**
     * Performs the actual auto-save operation for a specific document.
     * 
     * @param document the document to save (captured when auto-save was scheduled)
     */
    private void performAutoSave(PDFDocument document) {
        if (document == null || !document.hasUnsavedEdits()) {
            return;
        }

        try {
            // Auto-save directly to the original file (not backup)
            File originalFile = document.getFile();
            if (originalFile != null) {
                // CRITICAL: Use the same save logic as manual save (Ctrl+S)
                // This ensures proper handling of content stream modifications
                // by saving to temp file, closing, and reloading the document
                
                PDDocument pdDoc = document.getDocument();
                if (pdDoc != null) {
                    // Save to temporary file first
                    File tempFile = new File(originalFile.getParent(),
                            originalFile.getName() + ".autosave_tmp_" + System.currentTimeMillis());
                    
                    try {
                        // Synchronize on the document to prevent concurrent access
                        synchronized (pdDoc) {
                            pdDoc.save(tempFile);
                            logger.info("Auto-saved to temporary file: {}", tempFile.getName());
                        }
                        
                        // Close the document to release file locks
                        pdDoc.close();
                        logger.info("Document closed for auto-save");
                        
                        // Delete the original file
                        if (originalFile.exists()) {
                            boolean deleted = originalFile.delete();
                            if (!deleted) {
                                throw new IOException("Could not delete original file during auto-save");
                            }
                        }
                        
                        // Rename temp file to original name
                        boolean renamed = tempFile.renameTo(originalFile);
                        if (!renamed) {
                            // Try copy instead of rename
                            java.nio.file.Files.copy(
                                    tempFile.toPath(),
                                    originalFile.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                            );
                            tempFile.delete();
                        }
                        
                        // Reload the document
                        PDDocument newDoc = org.apache.pdfbox.Loader.loadPDF(originalFile);
                        document.updateDocument(newDoc);
                        
                        logger.info("Auto-saved and reloaded document: {}", originalFile.getAbsolutePath());
                        
                        // Clear unsaved edits flag since we saved to the real file
                        document.setHasUnsavedEdits(false);
                        
                        // Call callback if set
                        if (onAutoSaveCallback != null) {
                            javafx.application.Platform.runLater(onAutoSaveCallback);
                        }
                        
                    } catch (Exception e) {
                        // Cleanup temp file if something goes wrong
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                        throw e;
                    }
                }
            } else {
                // No original file - save to backup location for recovery
                File autoSaveFile = getAutoSaveFile(document);
                File metadataFile = getMetadataFile(document);

                PDDocument pdDoc = document.getDocument();
                if (pdDoc != null) {
                    // CRITICAL: Synchronize on the document to prevent concurrent access
                    synchronized (pdDoc) {
                        pdDoc.save(autoSaveFile);
                        logger.info("Auto-saved to backup (no original file): {}", autoSaveFile.getAbsolutePath());
                    }

                    // Save metadata
                    saveMetadata(metadataFile, document);

                    // Keep hasUnsavedEdits = true for untitled documents

                    // Call callback if set
                    if (onAutoSaveCallback != null) {
                        javafx.application.Platform.runLater(onAutoSaveCallback);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to auto-save document", e);
        }
    }

    /**
     * Clears the auto-save for a document.
     *
     * @param document the document whose auto-save to clear
     */
    public void clearAutoSave(PDFDocument document) {
        if (document == null) {
            return;
        }

        try {
            File autoSaveFile = getAutoSaveFile(document);
            File metadataFile = getMetadataFile(document);

            if (autoSaveFile.exists()) {
                Files.delete(autoSaveFile.toPath());
                logger.info("Cleared auto-save: {}", autoSaveFile.getName());
            }

            if (metadataFile.exists()) {
                Files.delete(metadataFile.toPath());
            }
        } catch (IOException e) {
            logger.error("Failed to clear auto-save", e);
        }
    }

    /**
     * Cancels any pending auto-save task.
     */
    private void cancelPendingSave() {
        if (pendingSaveTask != null && !pendingSaveTask.isDone()) {
            pendingSaveTask.cancel(false);
            logger.debug("Cancelled pending auto-save");
        }
    }

    /**
     * Gets the auto-save file for a document.
     *
     * @param document the document
     * @return the auto-save file
     */
    private File getAutoSaveFile(PDFDocument document) {
        String fileName = getAutoSaveFileName(document);
        return new File(AUTOSAVE_DIR, fileName);
    }

    /**
     * Gets the metadata file for a document.
     *
     * @param document the document
     * @return the metadata file
     */
    private File getMetadataFile(PDFDocument document) {
        String fileName = getAutoSaveFileName(document) + ".meta";
        return new File(AUTOSAVE_DIR, fileName);
    }

    /**
     * Generates a unique auto-save file name for a document.
     *
     * @param document the document
     * @return the file name
     */
    private String getAutoSaveFileName(PDFDocument document) {
        if (document.getFile() != null) {
            String originalName = document.getFile().getName();
            String baseName = originalName.replaceFirst("[.][^.]+$", "");
            return "autosave_" + baseName + "_" + System.identityHashCode(document) + ".pdf";
        }
        return "autosave_untitled_" + System.identityHashCode(document) + ".pdf";
    }

    /**
     * Saves metadata about the auto-saved document.
     *
     * @param metadataFile the metadata file
     * @param document     the document
     */
    private void saveMetadata(File metadataFile, PDFDocument document) throws IOException {
        try (FileWriter writer = new FileWriter(metadataFile)) {
            writer.write("timestamp=" + System.currentTimeMillis() + "\n");
            if (document.getFile() != null) {
                writer.write("originalPath=" + document.getFile().getAbsolutePath() + "\n");
            }
            writer.write("pages=" + document.getTotalPages() + "\n");
        }
    }

    /**
     * Ensures the auto-save directory exists.
     */
    private void ensureAutoSaveDirectoryExists() {
        try {
            Path autoSavePath = Paths.get(AUTOSAVE_DIR);
            if (!Files.exists(autoSavePath)) {
                Files.createDirectories(autoSavePath);
                logger.info("Created auto-save directory: {}", AUTOSAVE_DIR);
            }
        } catch (IOException e) {
            logger.error("Failed to create auto-save directory", e);
        }
    }

    /**
     * Checks if there are any auto-save files available for recovery.
     *
     * @return true if recovery files exist
     */
    public boolean hasRecoveryFiles() {
        File autoSaveDir = new File(AUTOSAVE_DIR);
        if (!autoSaveDir.exists()) {
            return false;
        }

        File[] files = autoSaveDir.listFiles((dir, name) -> name.startsWith("autosave_") && name.endsWith(".pdf"));
        return files != null && files.length > 0;
    }

    /**
     * Gets all available recovery files.
     *
     * @return array of recovery files
     */
    public File[] getRecoveryFiles() {
        File autoSaveDir = new File(AUTOSAVE_DIR);
        if (!autoSaveDir.exists()) {
            return new File[0];
        }

        File[] files = autoSaveDir.listFiles((dir, name) -> name.startsWith("autosave_") && name.endsWith(".pdf"));
        return files != null ? files : new File[0];
    }

    /**
     * Enables or disables auto-save.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            cancelPendingSave();
        }
        logger.info("Auto-save {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Checks if auto-save is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Shuts down the auto-save manager.
     */
    public void shutdown() {
        cancelPendingSave();
        logger.info("Auto-save manager shut down");
    }
}
