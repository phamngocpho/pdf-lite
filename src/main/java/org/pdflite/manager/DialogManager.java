package org.pdflite.manager;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.pdflite.controller.ExtractDialogController;
import org.pdflite.controller.MergeDialogController;
import org.pdflite.controller.PrintDialogController;
import org.pdflite.controller.SplitDialogController;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFPrintService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Manages dialog operations for PDF Lite.
 * Handles opening merge, split, extract, and print dialogs.
 */
public record DialogManager(BorderPane rootPane, ThemeManager themeManager, UIStateManager uiStateManager) {
    private static final Logger logger = LoggerFactory.getLogger(DialogManager.class);

    /**
     * Creates a new DialogManager.
     *
     * @param rootPane       the root pane for dialog owner
     * @param themeManager   the theme manager for applying themes to dialogs
     * @param uiStateManager the UI state manager for error messages
     */
    public DialogManager {
    }

    /**
     * Opens the merge PDF dialog.
     */
    public void openMergeDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/merge-dialog.fxml"));
            Parent root = loader.load();

            MergeDialogController controller = loader.getController();
            Stage dialogStage = createDialogStage(root, "Merge PDF Files");

            controller.setDialogStage(dialogStage);

            dialogStage.setOnCloseRequest(event -> controller.shutdown());
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error opening merge dialog", e);
            uiStateManager.showError("Error", "Could not open merge dialog: " + e.getMessage());
        }
    }

    /**
     * Opens the split PDF dialog.
     *
     * @param currentDocument the current PDF document
     */
    public void openSplitDialog(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF Loaded",
                    "Please open a PDF file first before splitting.");
            return;
        }

        // Check permissions for encrypted PDFs
        if (currentDocument.getDocument().isEncrypted()) {
            AccessPermission permission = currentDocument.getDocument().getCurrentAccessPermission();
            if (permission != null && !permission.canExtractContent() && !permission.isOwnerPermission()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Không có quyền");
                alert.setHeaderText("Không thể tách PDF");
                alert.setContentText("Bạn không có quyền trích xuất nội dung từ file PDF này.\n" +
                        "Cần quyền Owner hoặc quyền Extract Content.");

                if (themeManager != null) {
                    themeManager.applyThemeToScene(alert.getDialogPane().getScene());
                }

                alert.showAndWait();
                return;
            }
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/split-dialog.fxml"));
            Parent root = loader.load();

            SplitDialogController controller = loader.getController();
            Stage dialogStage = createDialogStage(root, "Split PDF File");

            controller.setDialogStage(dialogStage);

            // Use PDDocument for encrypted PDFs, File for regular PDFs
            if (currentDocument.getDocument().isEncrypted()) {
                controller.setSourceDocument(currentDocument.getDocument(), currentDocument.getFile());
            } else {
                controller.setSourceFile(currentDocument.getFile());
            }

            dialogStage.setOnCloseRequest(event -> controller.shutdown());
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error opening split dialog", e);
            uiStateManager.showError("Error", "Could not open split dialog: " + e.getMessage());
        }
    }

    /**
     * Opens the extract pages dialog.
     *
     * @param currentDocument the current PDF document
     */
    public void openExtractDialog(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF Loaded",
                    "Please open a PDF file first before extracting pages.");
            return;
        }

        // Check permissions for encrypted PDFs
        if (currentDocument.getDocument().isEncrypted()) {
            AccessPermission permission = currentDocument.getDocument().getCurrentAccessPermission();
            if (permission != null && !permission.canExtractContent() && !permission.isOwnerPermission()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Không có quyền");
                alert.setHeaderText("Không thể trích xuất trang");
                alert.setContentText("Bạn không có quyền trích xuất nội dung từ file PDF này.\n" +
                        "Cần quyền Owner hoặc quyền Extract Content.");

                if (themeManager != null) {
                    themeManager.applyThemeToScene(alert.getDialogPane().getScene());
                }

                alert.showAndWait();
                return;
            }
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/extract-dialog.fxml"));
            Parent root = loader.load();

            ExtractDialogController controller = loader.getController();
            Stage dialogStage = createDialogStage(root, "Extract PDF Pages");

            controller.setDialogStage(dialogStage);

            // Use PDDocument for encrypted PDFs, File for regular PDFs
            if (currentDocument.getDocument().isEncrypted()) {
                controller.setSourceDocument(currentDocument.getDocument(), currentDocument.getFile());
            } else {
                controller.setSourceFile(currentDocument.getFile());
            }

            dialogStage.setOnCloseRequest(event -> controller.shutdown());
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error opening extract dialog", e);
            uiStateManager.showError("Error", "Could not open extract dialog: " + e.getMessage());
        }
    }

    /**
     * Opens the print dialog.
     *
     * @param currentDocument the current PDF document
     * @param printService    the print service
     * @param currentPage     the current page index
     */
    public void openPrintDialog(PDFDocument currentDocument, PDFPrintService printService, int currentPage) {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF Loaded", "Please open a PDF file first before printing.");
            return;
        }

        // Check if printing is available
        if (!printService.isPrintingAvailable()) {
            uiStateManager.showError("No Printer Available",
                    "No printer is available on this system. Please install a printer and try again.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/print-dialog.fxml"));
            Parent root = loader.load();

            PrintDialogController controller = loader.getController();
            Stage dialogStage = createDialogStage(root, "Print PDF");

            controller.setDialogStage(dialogStage);
            controller.setDocument(currentDocument, printService, currentPage);

            dialogStage.showAndWait();

            // Check if user clicked print
            if (controller.isPrintClicked()) {
                uiStateManager.updateStatus("Print job sent successfully");
                logger.info("Print job completed");
            }

        } catch (IOException e) {
            logger.error("Error opening print dialog", e);
            uiStateManager.showError("Error", "Could not open print dialog: " + e.getMessage());
        }
    }

    /**
     * Creates and configures a dialog stage with standard settings.
     *
     * @param root  The dialog root node
     * @param title The dialog title
     * @return Configured Stage object
     */
    public Stage createDialogStage(Parent root, String title) {
        Stage dialogStage = new Stage();
        dialogStage.setTitle(title);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initOwner(rootPane.getScene().getWindow());

        Scene dialogScene = new Scene(root);
        dialogStage.setScene(dialogScene);

        if (themeManager != null) {
            themeManager.applyThemeToScene(dialogScene);
        }

        return dialogStage;
    }
}

