package org.pdflite.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.pdflite.dialog.CustomInfoDialog;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFReorderService;
import org.pdflite.service.PDFService;
import org.pdflite.util.DialogTitleBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the Page Reorder Dialog.
 * Allows users to reorder PDF pages using drag and drop.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class PageReorderDialogController {

    private static final Logger logger = LoggerFactory.getLogger(PageReorderDialogController.class);
    private static final double THUMBNAIL_SIZE = 120.0;
    private static final double PREVIEW_SCALE = 0.35;
    private static final DataFormat PAGE_INDEX_FORMAT = new DataFormat("application/pdf-page-index");

    @FXML
    private javafx.scene.layout.HBox dialogTitleBar;
    @FXML
    private Label fileNameLabel;
    @FXML
    private Label totalPagesLabel;
    @FXML
    private ScrollPane previewScrollPane;
    @FXML
    private FlowPane previewPane;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Button resetButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button applyButton;

    private File sourceFile;
    private PDDocument sourceDocument;
    private int totalPages;
    private List<Integer> currentOrder;
    private List<Integer> originalOrder;
    private boolean reorderApplied = false;
    private org.pdflite.manager.ThemeManager themeManager;

    private final PDFService pdfService = new PDFService();
    private final PDFReorderService reorderService = new PDFReorderService();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Stage dialogStage;
    private PDFDocument pdfDocumentWrapper;

    /**
     * Initializes the controller.
     */
    @FXML
    public void initialize() {
        logger.info("Initializing PageReorderDialogController");
        progressBar.setVisible(false);
        progressBar.setManaged(false);
    }

    /**
     * Sets the dialog stage.
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;

        String title = dialogStage.getTitle() != null ? dialogStage.getTitle() : "Reorder Pages";
        DialogTitleBar titleBar = new DialogTitleBar(title, dialogStage);
        if (dialogTitleBar != null) {
            dialogTitleBar.getChildren().setAll(titleBar.getTitleBar().getChildren());
        }

        Platform.runLater(() -> Platform.runLater(() -> {
            if (previewPane != null && previewScrollPane != null) {
                previewScrollPane.applyCss();
                previewScrollPane.layout();
                previewPane.applyCss();
                previewPane.layout();
            }
        }));
    }

    /**
     * Sets the theme manager.
     */
    public void setThemeManager(org.pdflite.manager.ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    /**
     * Sets the source PDF file.
     */
    public void setSourceFile(File file) {
        this.sourceFile = file;

        if (file == null) {
            return;
        }

        try {
            PDDocument doc = org.apache.pdfbox.Loader.loadPDF(file);
            this.sourceDocument = doc;
            this.pdfDocumentWrapper = new PDFDocument(doc, file);
            totalPages = doc.getNumberOfPages();
            initializeUI();
        } catch (IOException e) {
            logger.error("Error loading PDF", e);
            showError("Error", "Failed to load PDF: " + e.getMessage());
        }
    }

    /**
     * Sets the source PDF document (for already opened documents).
     */
    public void setSourceDocument(PDDocument document, File file) {
        this.sourceDocument = document;
        this.sourceFile = file;

        if (document == null) {
            return;
        }

        this.pdfDocumentWrapper = new PDFDocument(document, file);
        totalPages = document.getNumberOfPages();
        initializeUI();
    }

    /**
     * Initializes the UI with file information.
     */
    private void initializeUI() {
        fileNameLabel.setText(sourceFile != null ? sourceFile.getName() : "Document");
        totalPagesLabel.setText(String.format("Total Pages: %d", totalPages));

        // Initialize order lists
        originalOrder = new ArrayList<>();
        currentOrder = new ArrayList<>();
        for (int i = 0; i < totalPages; i++) {
            originalOrder.add(i);
            currentOrder.add(i);
        }

        loadThumbnails();
        updateStatus("Ready - Drag thumbnails to reorder");

        if (dialogStage != null) {
            Platform.runLater(() -> Platform.runLater(() -> dialogStage.sizeToScene()));
        }

        logger.info("Loaded PDF: {} ({} pages)", sourceFile != null ? sourceFile.getName() : "Document", totalPages);
    }

    /**
     * Loads thumbnail previews for all pages.
     */
    private void loadThumbnails() {
        previewPane.getChildren().clear();
        updateStatus("Loading thumbnails...");

        executorService.submit(() -> {
            try {
                for (int i = 0; i < totalPages; i++) {
                    final int pageIndex = i;
                    Image thumbnail = pdfService.renderPage(pdfDocumentWrapper, pageIndex, (float) PREVIEW_SCALE);

                    Platform.runLater(() -> {
                        VBox pageBox = createDraggableThumbnail(thumbnail, pageIndex);
                        previewPane.getChildren().add(pageBox);
                    });
                }

                Platform.runLater(() -> {
                    updateStatus("Ready - Drag thumbnails to reorder");
                    previewPane.applyCss();
                    previewPane.layout();
                });

            } catch (Exception e) {
                logger.error("Error loading thumbnails", e);
                Platform.runLater(() -> updateStatus("Error loading thumbnails"));
            }
        });
    }

    /**
     * Creates a draggable thumbnail box.
     */
    private VBox createDraggableThumbnail(Image thumbnail, int pageIndex) {
        ImageView imageView = new ImageView(thumbnail);
        imageView.setFitWidth(THUMBNAIL_SIZE);
        imageView.setFitHeight(THUMBNAIL_SIZE);
        imageView.setPreserveRatio(true);

        Label pageLabel = new Label("Page " + (pageIndex + 1));
        pageLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

        VBox box = new VBox(8, imageView, pageLabel);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-border-color: #ccc; -fx-border-width: 1; -fx-padding: 8; " +
                "-fx-background-color: white; -fx-cursor: hand;");
        box.setUserData(pageIndex);

        // Setup drag and drop
        setupDragAndDrop(box);

        return box;
    }

    /**
     * Sets up drag and drop handlers for a thumbnail box.
     */
    private void setupDragAndDrop(VBox box) {
        // Drag detected
        box.setOnDragDetected(event -> {
            Dragboard dragboard = box.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();

            Integer pageIndex = (Integer) box.getUserData();
            content.put(PAGE_INDEX_FORMAT, pageIndex);
            dragboard.setContent(content);

            // Visual feedback
            box.setStyle(box.getStyle() + "-fx-opacity: 0.5;");

            event.consume();
        });

        // Drag over
        box.setOnDragOver(event -> {
            if (event.getGestureSource() != box && event.getDragboard().hasContent(PAGE_INDEX_FORMAT)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        // Drag entered
        box.setOnDragEntered(event -> {
            if (event.getGestureSource() != box && event.getDragboard().hasContent(PAGE_INDEX_FORMAT)) {
                box.setStyle(box.getStyle() + "-fx-border-color: #2196F3; -fx-border-width: 2;");
            }
            event.consume();
        });

        // Drag exited
        box.setOnDragExited(event -> {
            box.setStyle("-fx-border-color: #ccc; -fx-border-width: 1; -fx-padding: 8; " +
                    "-fx-background-color: white; -fx-cursor: hand;");
            event.consume();
        });

        // Drag dropped
        box.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean success = false;

            if (dragboard.hasContent(PAGE_INDEX_FORMAT)) {
                Integer sourceIndex = (Integer) dragboard.getContent(PAGE_INDEX_FORMAT);
                Integer targetIndex = (Integer) box.getUserData();

                if (sourceIndex != null && targetIndex != null) {
                    reorderThumbnails(sourceIndex, targetIndex);
                    success = true;
                }
            }

            event.setDropCompleted(success);
            event.consume();
        });

        // Drag done
        box.setOnDragDone(event -> {
            box.setStyle("-fx-border-color: #ccc; -fx-border-width: 1; -fx-padding: 8; " +
                    "-fx-background-color: white; -fx-cursor: hand;");
            event.consume();
        });
    }

    /**
     * Reorders thumbnails in the UI and updates the current order.
     */
    private void reorderThumbnails(int sourceIndex, int targetIndex) {
        // Find the actual positions in the FlowPane
        int sourcePos = -1;
        int targetPos = -1;

        for (int i = 0; i < previewPane.getChildren().size(); i++) {
            VBox box = (VBox) previewPane.getChildren().get(i);
            Integer pageIndex = (Integer) box.getUserData();

            if (pageIndex == sourceIndex) {
                sourcePos = i;
            }
            if (pageIndex == targetIndex) {
                targetPos = i;
            }
        }

        if (sourcePos == -1 || targetPos == -1 || sourcePos == targetPos) {
            return;
        }

        // Move the thumbnail in the UI
        VBox sourceBox = (VBox) previewPane.getChildren().remove(sourcePos);
        previewPane.getChildren().add(targetPos, sourceBox);

        // Update current order
        Integer pageToMove = currentOrder.remove(sourcePos);
        currentOrder.add(targetPos, pageToMove);

        updateStatus("Pages reordered - Click Apply to save changes");
        logger.debug("Moved page {} to position {}", sourceIndex + 1, targetPos);
    }

    /**
     * Handles reset button click.
     */
    @FXML
    private void handleReset() {
        currentOrder.clear();
        currentOrder.addAll(originalOrder);

        // Reload thumbnails in original order
        loadThumbnails();
        updateStatus("Order reset to original");
    }

    /**
     * Handles apply button click.
     */
    @FXML
    private void handleApply() {
        if (currentOrder.equals(originalOrder)) {
            showInfo("No changes to apply");
            return;
        }

        try {
            setUIEnabled(false);
            progressBar.setVisible(true);
            progressBar.setManaged(true);
            progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            updateStatus("Applying new page order...");

            executorService.submit(() -> {
                try {
                    reorderService.reorderPages(pdfDocumentWrapper, currentOrder);

                    Platform.runLater(() -> {
                        progressBar.setProgress(1.0);
                        updateStatus("Pages reordered successfully!");
                        reorderApplied = true; // Set flag
                        showInfo("Pages have been reordered successfully.\nDon't forget to save the document.");

                        // Update original order to current
                        originalOrder.clear();
                        originalOrder.addAll(currentOrder);

                        handleCancel();
                    });

                } catch (Exception e) {
                    logger.error("Error applying page order", e);
                    Platform.runLater(() -> {
                        progressBar.setVisible(false);
                        progressBar.setManaged(false);
                        setUIEnabled(true);
                        updateStatus("Failed to reorder pages");
                        showError("Reorder Error", "Failed to reorder pages: " + e.getMessage());
                    });
                }
            });

        } catch (Exception e) {
            logger.error("Error initiating reorder", e);
            showError("Error", "Failed to initiate reorder: " + e.getMessage());
        }
    }

    /**
     * Handles cancel button click.
     */
    @FXML
    private void handleCancel() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * Enables or disables UI controls.
     */
    private void setUIEnabled(boolean enabled) {
        Platform.runLater(() -> {
            resetButton.setDisable(!enabled);
            applyButton.setDisable(!enabled);
            previewPane.setDisable(!enabled);
        });
    }

    /**
     * Updates status label.
     */
    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    /**
     * Shows an error dialog.
     */
    private void showError(String title, String message) {
        Platform.runLater(() -> CustomInfoDialog.show(
                title,
                "Error",
                message,
                themeManager
        ));
    }

    /**
     * Shows an information dialog.
     */
    private void showInfo(String message) {
        Platform.runLater(() -> CustomInfoDialog.show(
                "Page Reorder",
                "Success",
                message,
                themeManager
        ));
    }

    /**
     * Returns whether the reorder was applied.
     */
    public boolean isReorderApplied() {
        return reorderApplied;
    }

    /**
     * Cleanup resources.
     */
    public void shutdown() {
        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}
