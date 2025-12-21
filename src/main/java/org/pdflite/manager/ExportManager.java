package org.pdflite.manager;

import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Manager for PDF export operations.
 * Handles exporting PDF pages to images or text.
 */
public class ExportManager {

    private static final Logger logger = LoggerFactory.getLogger(ExportManager.class);

    private final BorderPane rootPane;
    private final UIStateManager uiStateManager;
    private final PDFExportService exportService;
    private ThemeManager themeManager;

    /**
     * Creates a new ExportManager.
     *
     * @param rootPane       the root pane for dialog ownership
     * @param uiStateManager the UI state manager for status updates
     */
    public ExportManager(BorderPane rootPane, UIStateManager uiStateManager) {
        this.rootPane = rootPane;
        this.uiStateManager = uiStateManager;
        this.exportService = new PDFExportService();
    }

    /**
     * Sets the theme manager for dialog theming.
     *
     * @param themeManager the theme manager
     */
    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    /**
     * Opens the export dialog and handles the export operation.
     *
     * @param currentDocument the current PDF document
     */
    public void openExportDialog(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF", "Please open a PDF file first.");
            return;
        }

        try {
            // Load the FXML file
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader();
            loader.setLocation(getClass().getResource("/org/pdflite/export-dialog.fxml"));
            javafx.scene.layout.VBox dialogContent = loader.load();

            // Get the controller and configure it
            org.pdflite.dialog.ExportDialogController controller = loader.getController();
            controller.setTotalPages(currentDocument.getTotalPages());

            // Create main container with title bar
            javafx.scene.layout.VBox mainContainer = new javafx.scene.layout.VBox();
            mainContainer.getStyleClass().add("custom-confirm-dialog");

            // Create and show the dialog
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Export PDF");
            dialogStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.initOwner(rootPane.getScene().getWindow());

            // Add title bar
            org.pdflite.util.DialogTitleBar titleBar = new org.pdflite.util.DialogTitleBar("Export PDF", dialogStage);
            mainContainer.getChildren().add(titleBar.getTitleBar());

            // Add dialog content
            mainContainer.getChildren().add(dialogContent);

            javafx.scene.Scene scene = new javafx.scene.Scene(mainContainer);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            dialogStage.setScene(scene);

            // Apply theme
            if (themeManager != null) {
                themeManager.applyThemeToScene(scene);
            }

            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();

            // If export was clicked, perform the export
            if (controller.isExportClicked()) {
                performExport(currentDocument, controller.getConfig());
            }
        } catch (java.io.IOException e) {
            logger.error("Error showing export dialog", e);
            uiStateManager.showError("Error", "Could not open export dialog: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error exporting", e);
            uiStateManager.showError("Error", "Could not export: " + e.getMessage());
        }
    }

    /**
     * Performs the actual export operation.
     *
     * @param currentDocument the current PDF document
     * @param config          the export configuration
     */
    private void performExport(PDFDocument currentDocument,
                               org.pdflite.dialog.ExportDialogController.ExportConfig config) {
        try {
            // Determine which pages to export
            List<Integer> pageIndices = getPageIndices(currentDocument, config);

            if (config.exportToImage) {
                exportToImage(currentDocument, config, pageIndices);
            } else {
                exportToText(currentDocument, config, pageIndices);
            }

            logger.info("Export completed successfully");
        } catch (Exception e) {
            logger.error("Error during export", e);
            uiStateManager.showError("Export Error", "Failed to export: " + e.getMessage());
        }
    }

    /**
     * Gets the list of page indices to export based on configuration.
     *
     * @param currentDocument the current PDF document
     * @param config          the export configuration
     * @return list of page indices (0-based)
     */
    private List<Integer> getPageIndices(PDFDocument currentDocument,
                                         org.pdflite.dialog.ExportDialogController.ExportConfig config) {
        List<Integer> pageIndices = new ArrayList<>();

        switch (config.pageRange) {
            case CURRENT:
                pageIndices.add(currentDocument.getCurrentPage());
                break;
            case ALL:
                for (int i = 0; i < currentDocument.getTotalPages(); i++) {
                    pageIndices.add(i);
                }
                break;
            case SPECIFIC:
                pageIndices = parsePageRange(config.specificPages, currentDocument.getTotalPages());
                break;
        }

        return pageIndices;
    }

    /**
     * Exports pages to image format.
     *
     * @param currentDocument the current PDF document
     * @param config          the export configuration
     * @param pageIndices     the page indices to export
     */
    private void exportToImage(PDFDocument currentDocument,
                               org.pdflite.dialog.ExportDialogController.ExportConfig config,
                               List<Integer> pageIndices) throws Exception {
        org.pdflite.service.PDFExportService.ImageFormat format =
                config.imageFormat.equals("PNG") ?
                        org.pdflite.service.PDFExportService.ImageFormat.PNG :
                        org.pdflite.service.PDFExportService.ImageFormat.JPG;

        if (pageIndices.size() == 1) {
            // Single page export
            File outputFile = new File(config.outputPath);
            exportService.exportPageToImage(currentDocument, pageIndices.getFirst(),
                    outputFile, format, config.dpi);
            uiStateManager.updateStatus("Page exported to: " + outputFile.getName());
        } else {
            // Multiple pages export
            File outputDir = new File(config.outputPath);
            String prefix = currentDocument.getFile() != null ?
                    currentDocument.getFile().getName().replaceFirst("[.][^.]+$", "") : "page";
            List<File> files = exportService.exportPagesToImages(
                    currentDocument, pageIndices, outputDir, prefix, format, config.dpi);
            uiStateManager.updateStatus("Exported " + files.size() + " pages to: " + outputDir.getName());
        }
    }

    /**
     * Exports pages to text format.
     *
     * @param currentDocument the current PDF document
     * @param config          the export configuration
     * @param pageIndices     the page indices to export
     */
    private void exportToText(PDFDocument currentDocument,
                              org.pdflite.dialog.ExportDialogController.ExportConfig config,
                              List<Integer> pageIndices) throws Exception {
        File outputFile = new File(config.outputPath);

        if (pageIndices.size() == currentDocument.getTotalPages()) {
            exportService.exportAllToText(currentDocument, outputFile);
        } else if (pageIndices.size() == 1) {
            int page = pageIndices.getFirst() + 1; // Convert to 1-based
            exportService.exportToText(currentDocument, outputFile, page, page);
        } else {
            int startPage = pageIndices.getFirst() + 1;
            int endPage = pageIndices.getLast() + 1;
            exportService.exportToText(currentDocument, outputFile, startPage, endPage);
        }

        uiStateManager.updateStatus("Text exported to: " + outputFile.getName());
    }

    /**
     * Parses a page range string into a list of page indices.
     * Supports formats like "1,3,5-7,10"
     *
     * @param rangeStr   the range string
     * @param totalPages the total number of pages in the document
     * @return list of page indices (0-based)
     */
    public static List<Integer> parsePageRange(String rangeStr, int totalPages) {
        List<Integer> pages = new ArrayList<>();
        String[] ranges = rangeStr.split(",");

        for (String range : ranges) {
            range = range.trim();
            if (range.contains("-")) {
                String[] parts = range.split("-");
                try {
                    int start = Integer.parseInt(parts[0].trim()) - 1;
                    int end = Integer.parseInt(parts[1].trim()) - 1;
                    for (int i = Math.max(0, start); i <= Math.min(totalPages - 1, end); i++) {
                        if (!pages.contains(i)) {
                            pages.add(i);
                        }
                    }
                } catch (NumberFormatException e) {
                    LoggerFactory.getLogger(ExportManager.class).warn("Invalid page range: {}", range);
                }
            } else {
                try {
                    int page = Integer.parseInt(range) - 1;
                    if (page >= 0 && page < totalPages && !pages.contains(page)) {
                        pages.add(page);
                    }
                } catch (NumberFormatException e) {
                    LoggerFactory.getLogger(ExportManager.class).warn("Invalid page number: {}", range);
                }
            }
        }

        return pages;
    }
}
