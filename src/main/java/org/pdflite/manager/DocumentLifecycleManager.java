package org.pdflite.manager;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.pdflite.controller.PageRenderer;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
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

            // Calculate initial zoom
            Image firstPage = pdfService.renderPage(newDocument, 0, 1.0f);
            double initialZoom = zoomManager.calculateInitialZoom(firstPage);
            zoomManager.setCurrentZoom(initialZoom);
            newDocument.setZoomLevel(initialZoom);

            // Update renderer and scroll handler with a new document
            pageRenderer.setDocument(newDocument, initialZoom);
            zoomManager.setDocument(newDocument);
            renderingManager.setDocument(newDocument);

            // Update UI
            uiStateManager.updateUIState(true);
            renderingManager.renderAllPages();
            pagesContainer.set(renderingManager.getPagesContainer());
            pageInfoManager.updatePageInfo(newDocument);

            // Scroll to the top (page 1) to ensure we're viewing the first page
            Platform.runLater(() -> {
                if (scrollPane != null && pagesContainer.get() != null) {
                    scrollPane.setVvalue(0.0);
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

        // Check if the document is encrypted
        if (currentDocument.getDocument().isEncrypted()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Lưu file đã mã hóa");
            alert.setHeaderText("File PDF này có mật khẩu bảo vệ");
            alert.setContentText("""
                    Bạn muốn:
                    - Lưu và GIỮ mật khẩu (chọn Cancel và dùng 'Save As')
                    - Lưu và XÓA mật khẩu (chọn OK)""");

            ButtonType keepPassword = new ButtonType("Giữ mật khẩu", ButtonBar.ButtonData.CANCEL_CLOSE);
            ButtonType removePassword = new ButtonType("Xóa mật khẩu", ButtonBar.ButtonData.OK_DONE);
            alert.getButtonTypes().setAll(removePassword, keepPassword);

            if (themeManager != null) {
                themeManager.applyThemeToScene(alert.getDialogPane().getScene());
            }

            final boolean[] success = new boolean[1];
            alert.showAndWait().ifPresent(response -> {
                if (response == removePassword) {
                    // User wants to remove password - proceed with save
                    try {
                        fileManager.save(currentDocument);

                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("Thành công");
                        successAlert.setHeaderText("Đã lưu file");
                        successAlert.setContentText("File đã được lưu và mật khẩu đã được xóa.");

                        if (themeManager != null) {
                            themeManager.applyThemeToScene(successAlert.getDialogPane().getScene());
                        }

                        successAlert.showAndWait();
                        success[0] = true;
                    } catch (IOException e) {
                        logger.error("Error saving document", e);
                        uiStateManager.showError("Save Error", "Could not save the document: " + e.getMessage());
                        success[0] = false;
                    }
                } else {
                    // User wants to keep the password - suggest Save As
                    Alert infoAlert = new Alert(Alert.AlertType.INFORMATION);
                    infoAlert.setTitle("Thông tin");
                    infoAlert.setHeaderText("Sử dụng Save As");
                    infoAlert.setContentText("Để giữ mật khẩu, vui lòng sử dụng chức năng 'Save As'\n" +
                            "hoặc chức năng 'Encrypt PDF' để đặt lại mật khẩu mới.");

                    if (themeManager != null) {
                        themeManager.applyThemeToScene(infoAlert.getDialogPane().getScene());
                    }

                    infoAlert.showAndWait();
                    success[0] = false;
                }
            });
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
}

