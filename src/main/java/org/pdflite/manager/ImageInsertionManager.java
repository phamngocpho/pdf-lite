package org.pdflite.manager;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.controller.PageRenderer;
import org.pdflite.dialog.ImagePlacementDialogController;
import org.pdflite.dialog.WatermarkDialogController;
import org.pdflite.model.ImageInsert;
import org.pdflite.model.ImagePlacement;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.WatermarkConfig;
import org.pdflite.service.WatermarkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Manager for image and stamp insertion operations.
 * Handles inserting images, stamps, and watermarks into PDF documents.
 */
public class ImageInsertionManager {

    private static final Logger logger = LoggerFactory.getLogger(ImageInsertionManager.class);

    private final BorderPane rootPane;
    private final UIStateManager uiStateManager;
    private final PageRenderer pageRenderer;
    private final ThemeManager themeManager;
    private Supplier<RenderingManager> renderingManagerSupplier;

    /**
     * Creates a new ImageInsertionManager.
     *
     * @param rootPane         the root pane for dialog ownership
     * @param uiStateManager   the UI state manager
     * @param pageRenderer     the page renderer
     * @param themeManager     the theme manager
     */
    public ImageInsertionManager(BorderPane rootPane, UIStateManager uiStateManager,
                                 PageRenderer pageRenderer, ThemeManager themeManager) {
        this.rootPane = rootPane;
        this.uiStateManager = uiStateManager;
        this.pageRenderer = pageRenderer;
        this.themeManager = themeManager;
    }

    /**
     * Sets the supplier for getting the current RenderingManager.
     * This is needed for multi-tab support where each tab has its own RenderingManager.
     *
     * @param supplier the supplier for RenderingManager
     */
    public void setRenderingManagerSupplier(Supplier<RenderingManager> supplier) {
        this.renderingManagerSupplier = supplier;
    }

    /**
     * Gets the current RenderingManager from the supplier.
     */
    private RenderingManager getRenderingManager() {
        return renderingManagerSupplier != null ? renderingManagerSupplier.get() : null;
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
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/org/pdflite/image-placement-dialog.fxml"));
            VBox dialogRoot = loader.load();

            // Get the controller and configure it
            ImagePlacementDialogController controller = loader.getController();

            // Create ImageManager
            ImageManager imageManager = new ImageManager(uiStateManager);
            controller.setImageManager(imageManager);
            controller.setTotalPages(currentDocument.getTotalPages());
            controller.setDefaultPage(currentDocument.getCurrentPage() + 1);

            // Set page height for coordinate conversion
            int currentPageIndex = currentDocument.getCurrentPage();
            double pageHeight = currentDocument.getDocument().getPage(currentPageIndex).getMediaBox().getHeight();
            controller.setPageHeight(pageHeight);

            // Create and show the dialog
            Stage dialogStage = new Stage();
            String dialogTitle = "Insert Image";
            dialogStage.setTitle(dialogTitle); // Store title for controller to use
            dialogStage.initStyle(StageStyle.TRANSPARENT); // Transparent for rounded corners
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(rootPane.getScene().getWindow());
            Scene dialogScene = new Scene(dialogRoot);
            dialogScene.setFill(Color.TRANSPARENT); // Transparent background

            // Apply current theme to dialog
            if (themeManager != null) {
                themeManager.applyThemeToScene(dialogScene);
            }

            dialogStage.setScene(dialogScene);
            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();

            // If insert was clicked, place the image
            if (controller.isInsertClicked()) {
                ImagePlacement placement = controller.getResultPlacement();
                imageManager.placeImage(currentDocument.getDocument(), placement);

                // Record the edit operation
                currentDocument.recordEdit(ImageInsert.create(
                        placement.pageIndex(), placement));

                // Refresh display
                refreshDisplay(currentDocument);

                uiStateManager.updateStatus("Image inserted successfully - save document to persist changes");
                logger.info("Image inserted on page {}", placement.pageIndex());
            }
        } catch (IOException e) {
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
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/org/pdflite/image-placement-dialog.fxml"));
            VBox dialogRoot = loader.load();

            // Get the controller and configure it
            ImagePlacementDialogController controller = loader.getController();

            // Create ImageManager
            ImageManager imageManager = new ImageManager(uiStateManager);
            controller.setImageManager(imageManager);
            controller.setTotalPages(currentDocument.getTotalPages());
            controller.setDefaultPage(currentDocument.getCurrentPage() + 1);

            // Set page height for coordinate conversion
            int currentPageIndex = currentDocument.getCurrentPage();
            double pageHeight = currentDocument.getDocument().getPage(currentPageIndex).getMediaBox().getHeight();
            controller.setPageHeight(pageHeight);

            // Create and show the dialog
            Stage dialogStage = new Stage();
            String dialogTitle = "Insert Stamp";
            dialogStage.setTitle(dialogTitle); // Store title for controller to use
            dialogStage.initStyle(StageStyle.TRANSPARENT); // Transparent for rounded corners
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(rootPane.getScene().getWindow());
            Scene dialogScene = new Scene(dialogRoot);
            dialogScene.setFill(Color.TRANSPARENT); // Transparent background

            // Apply current theme to dialog
            if (themeManager != null) {
                themeManager.applyThemeToScene(dialogScene);
            }

            dialogStage.setScene(dialogScene);
            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();

            // If insert was clicked, create the stamp
            if (controller.isInsertClicked()) {
                ImagePlacement placement = controller.getResultPlacement();

                if (placement.isStamp()) {
                    imageManager.createStampAnnotation(currentDocument.getDocument(), placement);
                } else {
                    // User unchecked the stamp option, place as regular image
                    imageManager.placeImage(currentDocument.getDocument(), placement);
                }

                // Record the edit operation
                currentDocument.recordEdit(ImageInsert.create(
                        placement.pageIndex(), placement));

                // Refresh display
                refreshDisplay(currentDocument);

                uiStateManager.updateStatus("Stamp inserted successfully - save document to persist changes");
                logger.info("Stamp inserted on page {}", placement.pageIndex());
            }
        } catch (IOException e) {
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
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/org/pdflite/watermark-dialog.fxml"));
            VBox dialogRoot = loader.load();

            // Get the controller and configure it
            WatermarkDialogController controller = loader.getController();

            // Create and show the dialog
            Stage dialogStage = new Stage();
            String dialogTitle = "Add Watermark";
            dialogStage.setTitle(dialogTitle); // Store title for controller to use
            dialogStage.initStyle(StageStyle.TRANSPARENT); // Transparent for rounded corners
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(rootPane.getScene().getWindow());
            Scene dialogScene = new Scene(dialogRoot);
            dialogScene.setFill(Color.TRANSPARENT); // Transparent background

            // Apply current theme to dialog
            if (themeManager != null) {
                themeManager.applyThemeToScene(dialogScene);
            }

            dialogStage.setScene(dialogScene);
            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();

            // If apply was clicked, add the watermark
            if (controller.isApplyClicked()) {
                WatermarkConfig config = controller.getConfig();
                WatermarkService watermarkService = new WatermarkService();

                watermarkService.applyWatermark(currentDocument, config);

                // Refresh display
                refreshDisplay(currentDocument);

                uiStateManager.updateStatus("Watermark applied successfully - save document to persist changes");
                logger.info("Watermark applied to document");
            }
        } catch (IOException e) {
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
        org.pdflite.dialog.CustomInfoDialog.show(
                "Text Editing",
                "How to Edit Text in PDF",
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
                        
                        For best results, use 'Insert Image' to add new content instead.""",
                themeManager
        );
    }

    /**
     * Refreshes the display after inserting content.
     *
     * @param currentDocument the current PDF document
     */
    private void refreshDisplay(PDFDocument currentDocument) {
        RenderingManager renderingManager = getRenderingManager();
        if (renderingManager == null) {
            logger.warn("No RenderingManager available for refresh");
            return;
        }

        // CRITICAL: Invalidate PDFRenderer cache first to ensure fresh render from modified PDDocument
        renderingManager.invalidateRendererCache();

        // Clear ALL caches to force re-render from modified PDDocument
        currentDocument.clearCache();  // Clear PDFDocument cache
        pageRenderer.clearCache();      // Clear PageRenderer cache
        pageRenderer.cancelAllPendingRenders();  // Cancel any pending renders

        // Refresh display to show the changes
        renderingManager.renderAllPages();
    }
}
