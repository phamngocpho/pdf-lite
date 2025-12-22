package org.pdflite.manager;

import java.io.IOException;
import java.util.function.Supplier;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.control.Alert;

/**
 * Manages text editing operations on PDF documents.
 */
public record TextEditManager(UIStateManager uiStateManager, ContentStreamManager contentStreamManager,
                              Supplier<RenderingManager> renderingManagerSupplier, SaveStatusManager saveStatusManager) {

    private static final Logger logger = LoggerFactory.getLogger(TextEditManager.class);

    /**
     * Creates a text edit callback for the context menu handler.
     */
    public TextEditCallback createTextEditCallback(Supplier<PDFDocument> documentSupplier) {
        return (pageIndex, coverX, coverY, coverWidth, coverHeight, textX, textY, newText, fontSize, font) -> {
            try {
                // Get current document
                PDFDocument currentDocument = documentSupplier.get();
                if (currentDocument == null) {
                    uiStateManager.updateStatus("No document loaded");
                    logger.warn("Cannot replace text: no document loaded");
                    return;
                }

                // Get the page
                PDPage page = currentDocument.getDocument().getPage(pageIndex);

                // Replace text: cover old text with white rectangle, then add new text
                logger.info("Replacing text on page {}: covering ({}, {}) {}x{}, adding '{}' at ({}, {}) with font {} size {}",
                        pageIndex + 1, coverX, coverY, coverWidth, coverHeight,
                        newText, textX, textY, font.getName(), fontSize);

                contentStreamManager.replaceText(
                        currentDocument.getDocument(),
                        page,
                        coverX, coverY, coverWidth, coverHeight,
                        newText,
                        textX, textY,
                        font,
                        fontSize
                );

                // Mark document as modified
                currentDocument.setHasUnsavedEdits(true);
                logger.info("Document marked as modified");

                // Refresh the page rendering to show the new text
                refreshCurrentPage(currentDocument, pageIndex);

                // Update status
                uiStateManager.updateStatus("Text replaced successfully");

            } catch (IOException e) {
                logger.error("Error adding text to PDF", e);
                uiStateManager.updateStatus("Error adding text: " + e.getMessage());
                showTextEditError("Failed to add text to PDF", e.getMessage());

            } catch (IndexOutOfBoundsException e) {
                logger.error("Invalid page index: {}", pageIndex, e);
                uiStateManager.updateStatus("Error: Invalid page index");
                showTextEditError("Invalid page index",
                        "Page " + (pageIndex + 1) + " does not exist in the document.");
            }
        };
    }

    /**
     * Refreshes the current page rendering to show changes.
     */
    private void refreshCurrentPage(PDFDocument document, int pageIndex) {
        if (document == null) {
            return;
        }

        logger.info("Refreshing page {} rendering after text edit", pageIndex + 1);

        // Clear document cache
        document.clearCache();

        // CRITICAL: Get RenderingManager BEFORE Platform.runLater()
        // because user might switch tabs before runLater executes
        RenderingManager currentRenderingManager = renderingManagerSupplier.get();
        
        if (currentRenderingManager == null) {
            logger.error("RenderingManager is null, cannot refresh page after text edit");
            return;
        }

        // Clear PageRenderer cache and re-render all pages
        Platform.runLater(() -> {
            logger.info("Clearing cache and re-rendering pages for document: {}", 
                document.getFile() != null ? document.getFile().getName() : "unknown");
            currentRenderingManager.clearPageRendererCache();
            currentRenderingManager.renderAllPages();
            logger.info("Cleared cache and re-rendered all pages after text edit");
            
            // CRITICAL: Wait for rendering to complete before triggering auto-save
            // This ensures the document is in a stable state before saving
            // Use another Platform.runLater to ensure rendering tasks complete first
            Platform.runLater(() -> {
                // Add a small delay to ensure all rendering is truly complete
                try {
                    Thread.sleep(500); // 500ms delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                if (saveStatusManager != null) {
                    saveStatusManager.triggerAutoSave();
                    logger.info("Auto-save scheduled after text edit and rendering completed");
                }
            });
        });
    }

    /**
     * Shows an error dialog for text editing errors.
     */
    private void showTextEditError(String header, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Text Edit Error");
            alert.setHeaderText(header);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    /**
     * Functional interface for text edit callback.
     */
    @FunctionalInterface
    public interface TextEditCallback {
        void onTextEdit(int pageIndex, float coverX, float coverY, float coverWidth, float coverHeight,
                        float textX, float textY, String newText, float fontSize,
                        PDFont font);
    }
}
