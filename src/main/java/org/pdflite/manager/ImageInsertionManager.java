package org.pdflite.manager;

import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.pdflite.controller.PageRenderer;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager for image and stamp insertion operations.
 * Handles inserting images, stamps, and watermarks into PDF documents.
 */
public class ImageInsertionManager {

    private static final Logger logger = LoggerFactory.getLogger(ImageInsertionManager.class);

    private final BorderPane rootPane;
    private final UIStateManager uiStateManager;
    private final RenderingManager renderingManager;
    private final PageRenderer pageRenderer;

    /**
     * Creates a new ImageInsertionManager.
     *
     * @param rootPane         the root pane for dialog ownership
     * @param uiStateManager   the UI state manager
     * @param renderingManager the rendering manager
     * @param pageRenderer     the page renderer
     */
    public ImageInsertionManager(BorderPane rootPane, UIStateManager uiStateManager,
                                 RenderingManager renderingManager, PageRenderer pageRenderer) {
        this.rootPane = rootPane;
        this.uiStateManager = uiStateManager;
        this.renderingManager = renderingManager;
        this.pageRenderer = pageRenderer;
    }

    /**
     * Opens the insert image dialog and handles image insertion.
     *
     * @param currentDocument the current PDF document
     */
    public void openInsertImageDialog(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF", "Please open a PDF file first.");
            return;
        }

        try {
            // Load the FXML file
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader();
            loader.setLocation(getClass().getResource("/org/pdflite/image-placement-dialog.fxml"));
            javafx.scene.layout.VBox dialogRoot = loader.load();

            // Get the controller and configure it
            org.pdflite.dialog.ImagePlacementDialogController controller = loader.getController();

            // Create ImageManager
            org.pdflite.manager.ImageManager imageManager = new org.pdflite.manager.ImageManager(uiStateManager);
            controller.setImageManager(imageManager);
            controller.setTotalPages(currentDocument.getTotalPages());
            controller.setDefaultPage(currentDocument.getCurrentPage() + 1);

            // Set page height for coordinate conversion
            int currentPageIndex = currentDocument.getCurrentPage();
            double pageHeight = currentDocument.getDocument().getPage(currentPageIndex).getMediaBox().getHeight();
            controller.setPageHeight(pageHeight);

            // Create and show the dialog
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Insert Image");
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.initOwner(rootPane.getScene().getWindow());
            dialogStage.setScene(new javafx.scene.Scene(dialogRoot));
            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();

            // If insert was clicked, place the image
            if (controller.isInsertClicked()) {
                org.pdflite.model.ImagePlacement placement = controller.getResultPlacement();
                imageManager.placeImage(currentDocument.getDocument(), placement);

                // Record the edit operation
                currentDocument.recordEdit(org.pdflite.model.ImageInsert.create(
                        placement.pageIndex(), placement));

                // Refresh display
                refreshDisplay(currentDocument);

                uiStateManager.updateStatus("Image inserted successfully - save document to persist changes");
                logger.info("Image inserted on page {}", placement.pageIndex());
            }
        } catch (java.io.IOException e) {
            logger.error("Error showing image placement dialog", e);
            uiStateManager.showError("Error", "Could not open image placement dialog: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error inserting image", e);
            uiStateManager.showError("Error", "Could not insert image: " + e.getMessage());
        }
    }

    /**
     * Opens the insert stamp dialog and handles stamp insertion.
     *
     * @param currentDocument the current PDF document
     */
    public void openInsertStampDialog(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF", "Please open a PDF file first.");
            return;
        }

        try {
            // Load the FXML file
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader();
            loader.setLocation(getClass().getResource("/org/pdflite/image-placement-dialog.fxml"));
            javafx.scene.layout.VBox dialogRoot = loader.load();

            // Get the controller and configure it
            org.pdflite.dialog.ImagePlacementDialogController controller = loader.getController();

            // Create ImageManager
            org.pdflite.manager.ImageManager imageManager = new org.pdflite.manager.ImageManager(uiStateManager);
            controller.setImageManager(imageManager);
            controller.setTotalPages(currentDocument.getTotalPages());
            controller.setDefaultPage(currentDocument.getCurrentPage() + 1);

            // Set page height for coordinate conversion
            int currentPageIndex = currentDocument.getCurrentPage();
            double pageHeight = currentDocument.getDocument().getPage(currentPageIndex).getMediaBox().getHeight();
            controller.setPageHeight(pageHeight);

            // Create and show the dialog
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Insert Stamp");
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.initOwner(rootPane.getScene().getWindow());
            dialogStage.setScene(new javafx.scene.Scene(dialogRoot));
            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();

            // If insert was clicked, create the stamp
            if (controller.isInsertClicked()) {
                org.pdflite.model.ImagePlacement placement = controller.getResultPlacement();

                if (placement.isStamp()) {
                    imageManager.createStampAnnotation(currentDocument.getDocument(), placement);
                } else {
                    // User unchecked the stamp option, place as regular image
                    imageManager.placeImage(currentDocument.getDocument(), placement);
                }

                // Record the edit operation
                currentDocument.recordEdit(org.pdflite.model.ImageInsert.create(
                        placement.pageIndex(), placement));

                // Refresh display
                refreshDisplay(currentDocument);

                uiStateManager.updateStatus("Stamp inserted successfully - save document to persist changes");
                logger.info("Stamp inserted on page {}", placement.pageIndex());
            }
        } catch (java.io.IOException e) {
            logger.error("Error showing stamp placement dialog", e);
            uiStateManager.showError("Error", "Could not open stamp placement dialog: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error inserting stamp", e);
            uiStateManager.showError("Error", "Could not insert stamp: " + e.getMessage());
        }
    }

    /**
     * Opens the watermark dialog and handles watermark application.
     *
     * @param currentDocument the current PDF document
     */
    public void openWatermarkDialog(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF", "Please open a PDF file first.");
            return;
        }

        try {
            // Load the FXML file
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader();
            loader.setLocation(getClass().getResource("/org/pdflite/watermark-dialog.fxml"));
            javafx.scene.layout.VBox dialogRoot = loader.load();

            // Get the controller and configure it
            org.pdflite.dialog.WatermarkDialogController controller = loader.getController();

            // Create and show the dialog
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Add Watermark");
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.initOwner(rootPane.getScene().getWindow());
            dialogStage.setScene(new javafx.scene.Scene(dialogRoot));
            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();

            // If apply was clicked, add the watermark
            if (controller.isApplyClicked()) {
                org.pdflite.model.WatermarkConfig config = controller.getConfig();
                org.pdflite.service.WatermarkService watermarkService = new org.pdflite.service.WatermarkService();

                watermarkService.applyWatermark(currentDocument, config);

                // Refresh display
                refreshDisplay(currentDocument);

                uiStateManager.updateStatus("Watermark applied successfully - save document to persist changes");
                logger.info("Watermark applied to document");
            }
        } catch (java.io.IOException e) {
            logger.error("Error showing watermark dialog", e);
            uiStateManager.showError("Error", "Could not open watermark dialog: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error applying watermark", e);
            uiStateManager.showError("Error", "Could not apply watermark: " + e.getMessage());
        }
    }

    /**
     * Shows information about text editing feature.
     *
     * @param currentDocument the current PDF document
     */
    public void showTextEditingInfo(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF", "Please open a PDF file first.");
            return;
        }

        uiStateManager.updateStatus("Text editing: This feature requires selecting text first. " +
                "Note: PDF text editing is complex and may not work for all PDFs.");

        // Show info dialog explaining how to use text editing
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Text Editing");
        alert.setHeaderText("How to Edit Text in PDF");
        alert.setContentText(
                """
                        Text editing in PDF is a complex operation with limitations:
                        
                        1. Enable 'Text Selection' mode from the toolbar
                        2. Click on the text you want to edit
                        3. Right-click and select 'Edit Text' from context menu
                        4. Edit the text in the dialog
                        5. Click OK to apply changes
                        
                        Note: This feature is experimental and may not work for:
                        - Scanned PDFs (images of text)
                        - PDFs with complex formatting
                        - Encrypted or protected PDFs
                        
                        For best results, use 'Insert Image' to add new content instead."""
        );
        alert.initOwner(rootPane.getScene().getWindow());
        alert.showAndWait();
    }

    /**
     * Refreshes the display after inserting content.
     *
     * @param currentDocument the current PDF document
     */
    private void refreshDisplay(PDFDocument currentDocument) {
        // Clear ALL caches to force re-render from modified PDDocument
        currentDocument.clearCache();  // Clear PDFDocument cache
        pageRenderer.clearCache();      // Clear PageRenderer cache
        pageRenderer.cancelAllPendingRenders();  // Cancel any pending renders

        // Refresh display to show the changes
        renderingManager.renderAllPages();
    }
}
