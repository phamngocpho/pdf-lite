package org.pdflite.manager;

import org.pdflite.dialog.MetadataDialog;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages document properties operations including metadata viewing and editing.
 */
public record DocumentPropertiesManager(MetadataManager metadataManager, UIStateManager uiStateManager,
                                        SaveStatusManager saveStatusManager, ThemeManager themeManager) {

    private static final Logger logger = LoggerFactory.getLogger(DocumentPropertiesManager.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    /**
     * Opens the document properties dialog and handles metadata updates.
     *
     * @param document The current PDF document
     */
    public void openDocumentPropertiesDialog(PDFDocument document) {
        if (document == null) {
            uiStateManager.showError(lang().getString("error.noDocument"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        try {
            // Get current metadata
            var currentMetadata = metadataManager.getMetadata(document);

            // Show metadata dialog
            MetadataDialog dialog = new MetadataDialog(currentMetadata, themeManager);

            if (dialog.showAndWait()) {
                // User clicked OK, update metadata
                var updatedMetadata = dialog.getMetadata();
                if (metadataManager.updateMetadata(document, updatedMetadata)) {
                    uiStateManager.updateStatus(lang().getString("status.complete"));
                    logger.info("Document metadata updated successfully");

                    // Trigger auto-save
                    if (saveStatusManager != null) {
                        saveStatusManager.triggerAutoSave();
                    }
                } else {
                    uiStateManager.showError(lang().getString("error.title"), lang().getString("error.saveFailed"));
                }
            }
        } catch (Exception e) {
            logger.error("Error opening document properties dialog", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.title") + ": " + e.getMessage());
        }
    }
}
