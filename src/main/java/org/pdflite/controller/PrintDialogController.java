package org.pdflite.controller;

import javafx.fxml.FXML;
import javafx.print.Printer;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.pdflite.manager.LanguageManager;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFPrintService;
import org.pdflite.util.DialogTitleBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Print Dialog.
 * Allows users to select print options including page range and printer.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class PrintDialogController {

    private static final Logger logger = LoggerFactory.getLogger(PrintDialogController.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    @FXML
    private javafx.scene.layout.HBox dialogTitleBar;
    @FXML
    private RadioButton rbAllPages;
    @FXML
    private RadioButton rbCurrentPage;
    @FXML
    private RadioButton rbPageRange;
    @FXML
    private TextField tfPageRange;
    @FXML
    private Label lblTotalPages;
    @FXML
    private ComboBox<String> cbPrinters;
    @FXML
    private Button btnPrint;
    @FXML
    private Button btnCancel;

    private Stage dialogStage;
    private PDFDocument document;
    private PDFPrintService printService;
    private boolean printClicked = false;
    private int currentPage;

    /**
     * Initialize the controller.
     */
    @FXML
    public void initialize() {
        // Create a toggle group for radio buttons
        ToggleGroup pageRangeGroup = new ToggleGroup();
        rbAllPages.setToggleGroup(pageRangeGroup);
        rbCurrentPage.setToggleGroup(pageRangeGroup);
        rbPageRange.setToggleGroup(pageRangeGroup);

        // Select "All Pages" by default
        rbAllPages.setSelected(true);

        // Disable text field initially
        tfPageRange.setDisable(true);

        // Enable/disable text field based on radio button selection
        pageRangeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            tfPageRange.setDisable(newVal != rbPageRange);
            if (newVal == rbPageRange) {
                tfPageRange.requestFocus();
            }
        });

        // Validate page range input
        tfPageRange.textProperty().addListener((obs, oldVal, newVal) -> {
            // Allow only numbers, commas, and hyphens
            if (!newVal.matches("[0-9,\\-\\s]*")) {
                tfPageRange.setText(oldVal);
            }
        });
    }

    /**
     * Set the dialog stage.
     *
     * @param dialogStage the dialog stage
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;

        // Create and add a custom title bar
        String title = dialogStage.getTitle() != null ? dialogStage.getTitle() : "Print PDF";
        DialogTitleBar titleBar = new org.pdflite.util.DialogTitleBar(title, dialogStage);
        if (dialogTitleBar != null) {
            dialogTitleBar.getChildren().setAll(titleBar.getTitleBar().getChildren());
        }
    }

    /**
     * Set the PDF document and print service.
     *
     * @param document     the PDF document
     * @param printService the print service
     * @param currentPage  the current page number (0-based)
     */
    public void setDocument(PDFDocument document, PDFPrintService printService, int currentPage) {
        this.document = document;
        this.printService = printService;
        this.currentPage = currentPage;

        if (document != null) {
            lblTotalPages.setText(java.text.MessageFormat.format(lang().getString("page.total"), document.getTotalPages()));
        }

        // Load available printers
        loadPrinters();
    }

    /**
     * Load available printers into the combo box.
     */
    private void loadPrinters() {
        if (printService == null) {
            return;
        }

        cbPrinters.getItems().clear();

        // Get default printer
        Printer defaultPrinter = printService.getDefaultPrinter();

        // Get all printers
        for (Printer printer : printService.getAvailablePrinters()) {
            cbPrinters.getItems().add(printer.getName());
        }

        // Select default printer
        if (defaultPrinter != null) {
            cbPrinters.getSelectionModel().select(defaultPrinter.getName());
        } else if (!cbPrinters.getItems().isEmpty()) {
            cbPrinters.getSelectionModel().selectFirst();
        }
    }

    /**
     * Handle print button click.
     */
    @FXML
    private void handlePrint() {
        if (document == null || printService == null) {
            logger.error("Cannot print: document or print service is null");
            return;
        }

        try {
            boolean success = false;

            if (rbAllPages.isSelected()) {
                // Print all pages
                success = printService.printDocument(document);
            } else if (rbCurrentPage.isSelected()) {
                // Print current page
                success = printService.printPage(document, currentPage);
            } else if (rbPageRange.isSelected()) {
                // Print page range
                String rangeText = tfPageRange.getText().trim();
                if (rangeText.isEmpty()) {
                    showError(lang().getString("error.title"), lang().getString("print.error.noPageRange"));
                    return;
                }

                success = printPageRange(rangeText);
            }

            if (success) {
                printClicked = true;
                dialogStage.close();
            }

        } catch (Exception e) {
            logger.error("Error during print", e);
            showError(lang().getString("error.title"), lang().getString("print.error.print") + ": " + e.getMessage());
        }
    }

    /**
     * Print a range of pages based on user input.
     *
     * @param rangeText the page range text (e.g., "1-5, 7, 9-12")
     * @return true if printing was successful
     */
    private boolean printPageRange(String rangeText) {
        try {
            // Parse page range
            String[] parts = rangeText.split(",");
            boolean success = true;

            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) {
                    continue;
                }

                if (part.contains("-")) {
                    // Range (e.g., "1-5")
                    String[] range = part.split("-");
                    if (range.length != 2) {
                        showError(lang().getString("error.title"), lang().getString("print.error.invalidRange") + ": " + part);
                        return false;
                    }

                    int start = Integer.parseInt(range[0].trim()) - 1; // Convert to 0-based
                    int end = Integer.parseInt(range[1].trim()) - 1;

                    if (start < 0 || end >= document.getTotalPages() || start > end) {
                        showError(lang().getString("error.title"),
                                lang().getString("print.error.invalidPageRange") + ": " + (start + 1) + "-" + (end + 1));
                        return false;
                    }

                    if (!printService.printPages(document, start, end)) {
                        success = false;
                        break;
                    }
                } else {
                    // Single page
                    int page = Integer.parseInt(part.trim()) - 1; // Convert to 0-based

                    if (page < 0 || page >= document.getTotalPages()) {
                        showError(lang().getString("error.title"),
                                lang().getString("print.error.invalidPageNumber") + ": " + (page + 1));
                        return false;
                    }

                    if (!printService.printPage(document, page)) {
                        success = false;
                        break;
                    }
                }
            }

            return success;

        } catch (NumberFormatException e) {
            showError(lang().getString("error.title"), lang().getString("print.error.invalidNumbers"));
            return false;
        }
    }

    /**
     * Handle cancel button click.
     */
    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    /**
     * Check if the print was clicked.
     *
     * @return true if print was clicked
     */
    public boolean isPrintClicked() {
        return printClicked;
    }

    /**
     * Show error dialog.
     *
     * @param title   the error title
     * @param message the error message
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(dialogStage);
        alert.showAndWait();
    }
}

