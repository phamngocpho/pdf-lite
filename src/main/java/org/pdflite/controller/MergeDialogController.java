package org.pdflite.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.pdflite.service.PDFMergeService;
import org.pdflite.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the PDF Merge Dialog.
 * Allows users to upload multiple PDFs, reorder them via drag-and-drop, and merge them.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class MergeDialogController {

    private static final Logger logger = LoggerFactory.getLogger(MergeDialogController.class);
    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");

    @FXML
    private TableView<PDFFileItem> filesTable;
    @FXML
    private TableColumn<PDFFileItem, Integer> orderColumn;
    @FXML
    private TableColumn<PDFFileItem, String> fileNameColumn;
    @FXML
    private TableColumn<PDFFileItem, String> pagesColumn;
    @FXML
    private TableColumn<PDFFileItem, String> sizeColumn;
    @FXML
    private Button addFilesButton;
    @FXML
    private Button removeButton;
    @FXML
    private Button moveUpButton;
    @FXML
    private Button moveDownButton;
    @FXML
    private Button mergeButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;

    private final ObservableList<PDFFileItem> fileItems = FXCollections.observableArrayList();
    private final PDFMergeService mergeService = new PDFMergeService();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Stage dialogStage;

    /**
     * Initializes the controller.
     */
    @FXML
    public void initialize() {
        logger.info("Initializing MergeDialogController");

        // Setup table columns
        orderColumn.setCellValueFactory(new PropertyValueFactory<>("order"));
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        pagesColumn.setCellValueFactory(new PropertyValueFactory<>("pages"));
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("fileSize"));

        filesTable.setItems(fileItems);

        // Enable row selection
        filesTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Setup drag-and-drop for reordering
        setupDragAndDrop();

        // Setup button states
        updateButtonStates();
        filesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                updateButtonStates());

        // Hide progress bar initially
        progressBar.setVisible(false);
        progressBar.setManaged(false);
    }

    /**
     * Sets the dialog stage.
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Handles adding files button click.
     */
    @FXML
    private void handleAddFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select PDF Files to Merge");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(dialogStage);
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            addFiles(selectedFiles);
        }
    }

    /**
     * Adds files to the merge list.
     */
    private void addFiles(List<File> files) {
        int addedCount = 0;
        int skippedCount = 0;

        for (File file : files) {
            // Check if file is already in the list
            boolean alreadyAdded = fileItems.stream()
                    .anyMatch(item -> item.getFile().getAbsolutePath().equals(file.getAbsolutePath()));

            if (alreadyAdded) {
                logger.debug("File already in list: {}", file.getName());
                skippedCount++;
                continue;
            }

            // Validate PDF
            if (!mergeService.isValidPDF(file)) {
                logger.warn("Invalid PDF file: {}", file.getName());
                showError("Invalid PDF", "The file '" + file.getName() + "' is not a valid PDF.");
                skippedCount++;
                continue;
            }

            // Get page count
            int pageCount = mergeService.getPageCount(file);

            // Add to list
            PDFFileItem item = new PDFFileItem(
                    fileItems.size() + 1,
                    file,
                    file.getName(),
                    pageCount,
                    FileUtils.formatFileSize(file.length())
            );
            fileItems.add(item);
            addedCount++;
        }

        // Update order numbers
        updateOrderNumbers();
        updateButtonStates();

        // Update status
        if (addedCount > 0) {
            updateStatus(String.format("Added %d file(s)%s", addedCount,
                    skippedCount > 0 ? " (" + skippedCount + " skipped)" : ""));
        } else if (skippedCount > 0) {
            updateStatus(String.format("Skipped %d file(s)", skippedCount));
        }

        logger.info("Added {} files, skipped {}", addedCount, skippedCount);
    }

    /**
     * Handles remove button click.
     */
    @FXML
    private void handleRemove() {
        List<PDFFileItem> selectedItems = new ArrayList<>(
                filesTable.getSelectionModel().getSelectedItems()
        );

        if (selectedItems.isEmpty()) {
            return;
        }

        fileItems.removeAll(selectedItems);
        updateOrderNumbers();
        updateButtonStates();
        updateStatus("Removed " + selectedItems.size() + " file(s)");
    }

    /**
     * Handles move up button click.
     */
    @FXML
    private void handleMoveUp() {
        PDFFileItem selected = filesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        int index = fileItems.indexOf(selected);
        if (index > 0) {
            fileItems.remove(index);
            fileItems.add(index - 1, selected);
            updateOrderNumbers();
            filesTable.getSelectionModel().select(selected);
        }
    }

    /**
     * Handles move down button click.
     */
    @FXML
    private void handleMoveDown() {
        PDFFileItem selected = filesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        int index = fileItems.indexOf(selected);
        if (index < fileItems.size() - 1) {
            fileItems.remove(index);
            fileItems.add(index + 1, selected);
            updateOrderNumbers();
            filesTable.getSelectionModel().select(selected);
        }
    }

    /**
     * Handles merge button click.
     */
    @FXML
    private void handleMerge() {
        if (fileItems.size() < 2) {
            showError("Not Enough Files", "Please add at least 2 PDF files to merge.");
            return;
        }

        // Choose output file
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Merged PDF");
        fileChooser.setInitialFileName("merged.pdf");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        File outputFile = fileChooser.showSaveDialog(dialogStage);
        if (outputFile == null) {
            return; // User cancelled
        }

        // Perform merge in background
        performMerge(outputFile);
    }

    /**
     * Performs the merge operation in a background thread.
     */
    private void performMerge(File outputFile) {
        // Disable UI
        setUIEnabled(false);
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        updateStatus("Merging PDFs...");

        List<File> inputFiles = fileItems.stream()
                .map(PDFFileItem::getFile)
                .toList();

        executorService.submit(() -> {
            try {
                mergeService.mergePDFs(inputFiles, outputFile);

                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    updateStatus("Merge completed successfully!");
                    showInfo(
                            String.format("Successfully merged %d files into:\n%s",
                                    inputFiles.size(), outputFile.getName()));

                    // Close dialog after short delay
                    Platform.runLater(() -> {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        handleCancel();
                    });
                });

                logger.info("Merge completed: {}", outputFile.getAbsolutePath());

            } catch (IOException e) {
                logger.error("Error merging PDFs", e);
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    setUIEnabled(true);
                    updateStatus("Merge failed!");
                    showError("Merge Error", "Failed to merge PDFs: " + e.getMessage());
                });
            }
        });
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
     * Sets up drag-and-drop for table rows.
     */
    private void setupDragAndDrop() {
        filesTable.setRowFactory(tv -> {
            TableRow<PDFFileItem> row = new TableRow<>();

            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    Integer index = row.getIndex();
                    Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    db.setDragView(row.snapshot(null, null));
                    ClipboardContent cc = new ClipboardContent();
                    cc.put(SERIALIZED_MIME_TYPE, index);
                    db.setContent(cc);
                    event.consume();
                }
            });

            row.setOnDragOver(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                    if (row.getIndex() != (Integer) db.getContent(SERIALIZED_MIME_TYPE)) {
                        event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                        event.consume();
                    }
                }
            });

            row.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                    int draggedIndex = (Integer) db.getContent(SERIALIZED_MIME_TYPE);
                    PDFFileItem draggedItem = filesTable.getItems().remove(draggedIndex);

                    int dropIndex;
                    if (row.isEmpty()) {
                        dropIndex = filesTable.getItems().size();
                    } else {
                        dropIndex = row.getIndex();
                    }

                    filesTable.getItems().add(dropIndex, draggedItem);
                    updateOrderNumbers();
                    event.setDropCompleted(true);
                    filesTable.getSelectionModel().select(dropIndex);
                    event.consume();
                }
            });

            return row;
        });
    }

    /**
     * Updates order numbers for all items.
     */
    private void updateOrderNumbers() {
        for (int i = 0; i < fileItems.size(); i++) {
            fileItems.get(i).setOrder(i + 1);
        }
        filesTable.refresh();
    }

    /**
     * Updates button enabled states.
     */
    private void updateButtonStates() {
        boolean hasSelection = !filesTable.getSelectionModel().isEmpty();
        int selectedIndex = filesTable.getSelectionModel().getSelectedIndex();

        removeButton.setDisable(!hasSelection);
        moveUpButton.setDisable(!hasSelection || selectedIndex == 0);
        moveDownButton.setDisable(!hasSelection || selectedIndex == fileItems.size() - 1);
        mergeButton.setDisable(fileItems.size() < 2);
    }

    /**
     * Enables or disables UI controls.
     */
    private void setUIEnabled(boolean enabled) {
        addFilesButton.setDisable(!enabled);
        removeButton.setDisable(!enabled);
        moveUpButton.setDisable(!enabled);
        moveDownButton.setDisable(!enabled);
        mergeButton.setDisable(!enabled);
        filesTable.setDisable(!enabled);
    }

    /**
     * Updates status label.
     */
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    /**
     * Shows an error dialog.
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows an information dialog.
     */
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Merge Complete");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Represents a PDF file item in the merge list.
     */
    public void shutdown() {
        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    // ==================== Inner Class: PDFFileItem ====================

    /**
     * Represents a PDF file item in the merge list.
     */
    public static class PDFFileItem {
        private int order;
        private final File file;
        private final String fileName;
        private final int pages;
        private final String fileSize;

        public PDFFileItem(int order, File file, String fileName, int pages, String fileSize) {
            this.order = order;
            this.file = file;
            this.fileName = fileName;
            this.pages = pages;
            this.fileSize = fileSize;
        }

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }

        public File getFile() {
            return file;
        }

        public String getFileName() {
            return fileName;
        }

        public int getPages() {
            return pages;
        }

        public String getFileSize() {
            return fileSize;
        }
    }
}

