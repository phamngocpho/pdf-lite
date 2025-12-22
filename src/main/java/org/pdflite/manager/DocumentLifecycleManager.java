package org.pdflite.manager;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javafx.stage.Stage;
import org.pdflite.controller.PageRenderer;
import org.pdflite.dialog.CustomConfirmDialog;
import org.pdflite.dialog.CustomInfoDialog;
import org.pdflite.model.DocumentContext;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * Manages document lifecycle operations including opening, saving, and closing PDF files.
 */
public class DocumentLifecycleManager {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentLifecycleManager.class);
    
    private final PDFService pdfService;
    private final FileManager fileManager;
    private final ZoomManager zoomManager;
    private final RenderingManager renderingManager;
    private final PageInfoManager pageInfoManager;
    private final UIStateManager uiStateManager;
    private final ThemeManager themeManager;
    private final RecentFilesManager recentFilesManager;
    private final RecentFilesMenuManager recentFilesMenuManager;
    private final HighlightPersistenceManager highlightPersistenceManager;
    private final AutoSaveManager autoSaveManager;
    private final SaveStatusManager saveStatusManager;
    private final DialogManager dialogManager;
    private final ApplicationLifecycleManager applicationLifecycleManager;
    private final BorderPane rootPane;
    
    // Suppliers for dynamic context
    private Supplier<PDFDocument> currentDocumentSupplier;
    private Supplier<DocumentContext> currentContextSupplier;

    public DocumentLifecycleManager(PDFService pdfService,
                                   FileManager fileManager,
                                   ZoomManager zoomManager,
                                   RenderingManager renderingManager,
                                   PageInfoManager pageInfoManager,
                                   UIStateManager uiStateManager,
                                   ThemeManager themeManager,
                                   RecentFilesManager recentFilesManager,
                                   RecentFilesMenuManager recentFilesMenuManager,
                                   HighlightPersistenceManager highlightPersistenceManager,
                                   AutoSaveManager autoSaveManager,
                                   SaveStatusManager saveStatusManager,
                                   DialogManager dialogManager,
                                   ApplicationLifecycleManager applicationLifecycleManager,
                                   BorderPane rootPane) {
        this.pdfService = pdfService;
        this.fileManager = fileManager;
        this.zoomManager = zoomManager;
        this.renderingManager = renderingManager;
        this.pageInfoManager = pageInfoManager;
        this.uiStateManager = uiStateManager;
        this.themeManager = themeManager;
        this.recentFilesManager = recentFilesManager;
        this.recentFilesMenuManager = recentFilesMenuManager;
        this.highlightPersistenceManager = highlightPersistenceManager;
        this.autoSaveManager = autoSaveManager;
        this.saveStatusManager = saveStatusManager;
        this.dialogManager = dialogManager;
        this.applicationLifecycleManager = applicationLifecycleManager;
        this.rootPane = rootPane;
    }
    
    /**
     * Sets suppliers for dynamic context retrieval.
     */
    public void setContextSuppliers(Supplier<PDFDocument> currentDocumentSupplier,
                                    Supplier<DocumentContext> currentContextSupplier) {
        this.currentDocumentSupplier = currentDocumentSupplier;
        this.currentContextSupplier = currentContextSupplier;
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

            uiStateManager.updateStatus("Opened: " + file.getName());

            // Add to recent files
            recentFilesManager.addRecentFile(file.getAbsolutePath());
            recentFilesMenuManager.updateRecentFilesMenu();

            logger.info("Successfully opened PDF: {} ({} pages, starting at page 1)",
                    file.getName(), newDocument.getTotalPages());
            return newDocument;
        } catch (IOException e) {
            logger.error("Error opening PDF file", e);
            uiStateManager.showError("Error Opening PDF", "Could not open the PDF file: " + e.getMessage());
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
                    "Lưu file đã mã hóa",
                    "File PDF này có mật khẩu bảo vệ",
                    "Bạn muốn:\n" +
                            "- Lưu và GIỮ mật khẩu (chọn Cancel và dùng 'Save As')\n" +
                            "- Lưu và XÓA mật khẩu (chọn OK)",
                    themeManager
            );

            final boolean[] success = new boolean[1];
            if (removePassword) {
                // User wants to remove password - proceed with save
                try {
                    fileManager.save(currentDocument);

                    CustomInfoDialog.show(
                            "Thành công",
                            "Đã lưu file",
                            "File đã được lưu và mật khẩu đã được xóa.",
                            themeManager
                    );

                    success[0] = true;
                } catch (IOException e) {
                    logger.error("Error saving document", e);
                    uiStateManager.showError("Save Error", "Could not save the document: " + e.getMessage());
                    success[0] = false;
                }
            } else {
                // User wants to keep the password - suggest Save As
                CustomInfoDialog.show(
                        "Thông tin",
                        "Sử dụng Save As",
                        "Để giữ mật khẩu, vui lòng sử dụng chức năng 'Save As'\n" +
                                "hoặc chức năng 'Encrypt PDF' để đặt lại mật khẩu mới.",
                        themeManager
                );

                success[0] = false;
            }
        } else {
            // Normal save for non-encrypted documents
            try {
                fileManager.save(currentDocument);
            } catch (IOException e) {
                logger.error("Error saving document", e);
                uiStateManager.showError("Save Error", "Could not save the document: " + e.getMessage());
            }
        }
    }

    /**
     * Handles save operation with highlight persistence.
     */
    public void handleSave() {
        PDFDocument currentDocument = currentDocumentSupplier != null ? currentDocumentSupplier.get() : null;
        if (currentDocument == null) {
            return;
        }

        // Save highlights to PDF before saving document
        if (highlightPersistenceManager != null) {
            try {
                highlightPersistenceManager.saveHighlightsToPDF(
                        currentDocument.getDocument(),
                        currentDocument.getAnnotations());
                logger.info("Highlights saved to PDF");
            } catch (Exception e) {
                logger.error("Error saving highlights to PDF", e);
                uiStateManager.showError("Save Error",
                        "Failed to save highlights: " + e.getMessage());
            }
        }

        saveDocument(currentDocument);

        // Clear auto-save after a successful save
        if (autoSaveManager != null) {
            autoSaveManager.clearAutoSave(currentDocument);
        }

        // Update save status indicator
        if (saveStatusManager != null) {
            saveStatusManager.updateSaveStatusIndicator(true);
        }
    }
    
    /**
     * Handles save as operation.
     */
    public void handleSaveAs() {
        PDFDocument currentDocument = currentDocumentSupplier != null ? currentDocumentSupplier.get() : null;
        if (currentDocument == null) {
            return;
        }

        // Warn the user if the document is encrypted
        if (currentDocument.getDocument().isEncrypted()) {
            if (!dialogManager.showEncryptedSaveWarning()) {
                return; // User cancelled
            }
        }

        try {
            Stage stage = (Stage) rootPane.getScene().getWindow();
            fileManager.saveAs(currentDocument, stage);
        } catch (IOException e) {
            logger.error("Error saving document as", e);
            uiStateManager.showError("Save As Error", "Could not save the document: " + e.getMessage());
        }
    }
    
    /**
     * Performs application exit with cleanup.
     */
    public void performExit() {
        if (applicationLifecycleManager != null) {
            // Get current context to close document
            DocumentContext context = currentContextSupplier != null ? currentContextSupplier.get() : null;
            if (context != null) {
                applicationLifecycleManager.performExit(context.getDocument());
            }
        }
        System.exit(0);
    }
}