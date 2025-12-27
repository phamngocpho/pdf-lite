package org.pdflite.manager;

import java.io.IOException;

import javafx.stage.Stage;

import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages document saving operations including save, save as, and highlight persistence.
 */
public class SaveManager {

    private static final Logger logger = LoggerFactory.getLogger(SaveManager.class);

    private final FileManager fileManager;
    private final DocumentLifecycleManager documentLifecycleManager;
    private final HighlightPersistenceManager highlightPersistenceManager;
    private final AutoSaveManager autoSaveManager;
    private final SaveStatusManager saveStatusManager;
    private final UIStateManager uiStateManager;
    private final DialogManager dialogManager;

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    public SaveManager(FileManager fileManager, DocumentLifecycleManager documentLifecycleManager,
                       HighlightPersistenceManager highlightPersistenceManager, AutoSaveManager autoSaveManager,
                       SaveStatusManager saveStatusManager, UIStateManager uiStateManager,
                       DialogManager dialogManager) {
        this.fileManager = fileManager;
        this.documentLifecycleManager = documentLifecycleManager;
        this.highlightPersistenceManager = highlightPersistenceManager;
        this.autoSaveManager = autoSaveManager;
        this.saveStatusManager = saveStatusManager;
        this.uiStateManager = uiStateManager;
        this.dialogManager = dialogManager;
    }

    /**
     * Saves the current document with highlights.
     */
    public void save(PDFDocument document) {
        if (document == null) {
            return;
        }

        // Save highlights to PDF before saving document
        if (highlightPersistenceManager != null) {
            try {
                highlightPersistenceManager.saveHighlightsToPDF(
                        document.getDocument(),
                        document.getAnnotations());
                logger.info("Highlights saved to PDF");
            } catch (Exception e) {
                logger.error("Error saving highlights to PDF", e);
                uiStateManager.showError(lang().getString("error.saveFailed"),
                        lang().getString("error.saveHighlights") + ": " + e.getMessage());
            }
        }

        documentLifecycleManager.saveDocument(document);

        // Clear auto-save after a successful save
        if (autoSaveManager != null) {
            autoSaveManager.clearAutoSave(document);
        }

        // Update save status indicator
        if (saveStatusManager != null) {
            saveStatusManager.updateSaveStatusIndicator(true);
        }
    }

    /**
     * Saves the document to a new location.
     */
    public void saveAs(PDFDocument document, Stage stage) {
        if (document == null) {
            return;
        }

        // Warn the user if the document is encrypted
        if (document.getDocument().isEncrypted()) {
            if (dialogManager != null && !dialogManager.showEncryptedSaveWarning()) {
                return; // User cancelled
            }
        }

        try {
            fileManager.saveAs(document, stage);
        } catch (IOException e) {
            logger.error("Error saving document as", e);
            uiStateManager.showError(lang().getString("error.saveFailed"), lang().getString("error.saveFailed") + ": " + e.getMessage());
        }
    }
}
