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

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

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
            uiStateManager.showError(lang().getString("error.noPdfLoaded"), lang().getString("error.noPdfLoadedMsg"));
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
            controller.setStampDefault(false);

            // Set page height for coordinate conversion
            int currentPageIndex = currentDocument.getCurrentPage();
            double pageHeight = currentDocument.getDocument().getPage(currentPageIndex).getMediaBox().getHeight();
            controller.setPageHeight(pageHeight);

            // Create and show the dialog
            Stage dialogStage = new Stage();
            String dialogTitle = lang().getString("menu.edit.insertImage");
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

                uiStateManager.updateStatus(lang().getString("success.title"));
                logger.info("Image inserted on page {}", placement.pageIndex());
            }
        } catch (IOException e) {
            logger.error("Error showing image placement dialog", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.insertImage") + ": " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error inserting image", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.insertImage") + ": " + e.getMessage());
        }
    }

    /**
     * Opens the insert stamp dialog and handles stamp insertion.
     *
     * @param currentDocument the current PDF document
     */
    public void openInsertStampDialog(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError(lang().getString("error.noPdfLoaded"), lang().getString("error.noPdfLoadedMsg"));
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
            controller.setStampDefault(true);

            // Set page height for coordinate conversion
            int currentPageIndex = currentDocument.getCurrentPage();
            double pageHeight = currentDocument.getDocument().getPage(currentPageIndex).getMediaBox().getHeight();
            controller.setPageHeight(pageHeight);

            // Create and show the dialog
            Stage dialogStage = new Stage();
            String dialogTitle = lang().getString("menu.edit.insertStamp");
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

                uiStateManager.updateStatus(lang().getString("success.title"));
                logger.info("Stamp inserted on page {}", placement.pageIndex());
            }
        } catch (IOException e) {
            logger.error("Error showing stamp placement dialog", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.insertImage") + ": " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error inserting stamp", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.insertImage") + ": " + e.getMessage());
        }
    }

    /**
     * Opens the watermark dialog and handles watermark application.
     *
     * @param currentDocument the current PDF document
     */
    public void openWatermarkDialog(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError(lang().getString("error.noPdfLoaded"), lang().getString("error.noPdfLoadedMsg"));
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
            String dialogTitle = lang().getString("menu.tools.watermark");
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

                uiStateManager.updateStatus(lang().getString("success.watermark"));
                logger.info("Watermark applied to document");
            }
        } catch (IOException e) {
            logger.error("Error showing watermark dialog", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.watermark") + ": " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error applying watermark", e);
            uiStateManager.showError(lang().getString("error.title"), lang().getString("error.watermark") + ": " + e.getMessage());
        }
    }

    /**
     * Shows information about text editing feature.
     *
     * @param currentDocument the current PDF document
     */
    public void showTextEditingInfo(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError(lang().getString("error.noPdfLoaded"), lang().getString("error.noPdfLoadedMsg"));
            return;
        }

        uiStateManager.updateStatus(lang().getString("menu.edit.editText"));

        // Show info dialog explaining how to use text editing
        org.pdflite.dialog.CustomInfoDialog.show(
                lang().getString("menu.edit.editText"),
                lang().getString("menu.edit.editText"),
                lang().getString("textEdit.info"),
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
