package org.pdflite.manager;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.pdflite.controller.PageRenderer;
import org.pdflite.dialog.CustomConfirmDialog;
import org.pdflite.dialog.CustomInfoDialog;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/**
 * Manages document lifecycle operations including opening and saving PDF files.
 */
public record DocumentLifecycleManager(PDFService pdfService, FileManager fileManager, ZoomManager zoomManager,
                                       RenderingManager renderingManager, PageInfoManager pageInfoManager,
                                       UIStateManager uiStateManager, ThemeManager themeManager,
                                       RecentFilesManager recentFilesManager,
                                       RecentFilesMenuManager recentFilesMenuManager) {
    private static final Logger logger = LoggerFactory.getLogger(DocumentLifecycleManager.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    /**
     * Creates a new DocumentLifecycleManager.
     *
     * @param pdfService             the PDF service
     * @param fileManager            the file manager
     * @param zoomManager            the zoom manager
     * @param renderingManager       the rendering manager
     * @param pageInfoManager        the page info manager
     * @param uiStateManager         the UI state manager
     * @param themeManager           the theme manager
     * @param recentFilesManager     the recent files manager
     * @param recentFilesMenuManager the recent files menu manager
     */
    public DocumentLifecycleManager {
    }

    /**
     * Opens a PDF file and initializes the document for viewing.
     *
     * @param file            the PDF file to open
     * @param currentDocument the current document (will be closed if not null)
     * @param pageRenderer    the page renderer
     * @param scrollPane      the scroll pane
     * @param pagesContainer  reference to page container (will be updated)
     * @return the opened PDFDocument, or null if opening failed
     */
    public PDFDocument openPDFFile(File file, PDFDocument currentDocument,
                                   PageRenderer pageRenderer, ScrollPane scrollPane,
                                   AtomicReference<VBox> pagesContainer) {
        try {
            // Close the current document if open
            if (currentDocument != null) {
                fileManager.close(currentDocument);
            }

            // Open a new document
            PDFDocument newDocument = fileManager.openFile(file);
            if (newDocument == null) {
                return null;
            }

            // CRITICAL: Reset to page 1 (index 0) when opening a new file
            newDocument.setCurrentPage(0);

            // Set initial zoom to 100% (1.0) for consistent display and accurate text copying
            // Using 100% ensures coordinate calculations for text selection are accurate
            double initialZoom = 1.0;
            newDocument.setZoomLevel(initialZoom);

            // Update renderer and scroll handler with a new document
            pageRenderer.setDocument(newDocument, initialZoom);
            zoomManager.setDocument(newDocument);
            zoomManager.setCurrentZoom(initialZoom); // This will update the zoom combo box display
            renderingManager.setDocument(newDocument);

            // Update UI
            uiStateManager.updateUIState(true);
            renderingManager.renderAllPages();
            pagesContainer.set(renderingManager.getPagesContainer());
            pageInfoManager.updatePageInfo(newDocument);

            // Scroll to the top (page 1) to ensure we're viewing the first page
            // Also enable text selection by default after pages are rendered
            Platform.runLater(() -> {
                if (scrollPane != null && pagesContainer.get() != null) {
                    scrollPane.setVvalue(0.0);
                }
                // Enable text selection by default (like browsers) after pages are rendered
                // Text selection is already enabled by default in PageRenderer, but ensure it's applied
                if (pagesContainer.get() != null) {
                    pageRenderer.setSelectionModeActive(pagesContainer.get(), true);
                }
            });

            uiStateManager.updateStatus(lang().getString("status.opened", file.getName()));

            // Add to recent files
            recentFilesManager.addRecentFile(file.getAbsolutePath());
            recentFilesMenuManager.updateRecentFilesMenu();

            logger.info("Successfully opened PDF: {} ({} pages, starting at page 1)",
                    file.getName(), newDocument.getTotalPages());
            return newDocument;
        } catch (IOException e) {
            logger.error("Error opening PDF file", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.openPdf") + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Saves the document, handling encrypted documents appropriately.
     *
     * @param currentDocument the current PDF document
     */
    public void saveDocument(PDFDocument currentDocument) {
        if (currentDocument == null) {
            return;
        }

        // No need to confirm overwriting when saving the currently open file
        // The overwriting confirmation is only needed for "Save As" operation
        File targetFile = currentDocument.getFile();

        // Check if the document is encrypted
        if (currentDocument.getDocument().isEncrypted()) {
            boolean removePassword = CustomConfirmDialog.show(
                    lang().getString("menu.file.save"),
                    lang().getString("message.passwordRequired"),
                    lang().getString("message.unsavedChanges"),
                    themeManager
            );

            final boolean[] success = new boolean[1];
            if (removePassword) {
                // User wants to remove password - proceed with save
                try {
                    fileManager.save(currentDocument);

                    CustomInfoDialog.show(
                            lang().getString("success.title"),
                            lang().getString("success.saved"),
                            targetFile.getAbsolutePath(),
                            themeManager
                    );

                    success[0] = true;
                } catch (IOException e) {
                    logger.error("Error saving document", e);
                    uiStateManager.showError(lang().getString("error.title"), lang().getString("error.saveFailed") + ": " + e.getMessage());
                    success[0] = false;
                }
            } else {
                // User wants to keep the password - suggest Save As
                CustomInfoDialog.show(
                        lang().getString("dialog.info"),
                        lang().getString("menu.file.saveAs"),
                        lang().getString("save.keepPasswordMsg"),
                        themeManager
                );

                success[0] = false;
            }
        } else {
            // Normal save for non-encrypted documents
            try {
                fileManager.save(currentDocument);
            } catch (IOException e) {
                // Check if this is a signed PDF warning (not a real error)
                if (e.getMessage() != null && e.getMessage().contains("digital signatures")) {
                    logger.warn("Cannot save signed PDF: {}", e.getMessage());
                    uiStateManager.showError(lang().getString("error.title"), lang().getString("error.saveSignedPdf"));
                } else {
                    logger.error("Error saving document", e);
                    uiStateManager.showError(lang().getString("error.title"), lang().getString("error.saveFailed") + ": " + e.getMessage());
                }
            }
        }
    }
}

