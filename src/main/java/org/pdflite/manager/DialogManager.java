package org.pdflite.manager;

import java.io.IOException;

import javafx.application.Platform;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.pdflite.controller.ExtractDialogController;
import org.pdflite.controller.InsertDialogController;
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
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
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
     * @param rootPane       the root pane for the dialog owner
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

            // Check if the user clicked print
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
     * Creates and configures a dialog stage with standard settings and custom title bar.
     *
     * @param root  The dialog root node
     * @param title The dialog title
     * @return Configured Stage object
     */
    public Stage createDialogStage(Parent root, String title) {
        Stage dialogStage = new Stage();
        dialogStage.setTitle(title);
        dialogStage.initStyle(javafx.stage.StageStyle.TRANSPARENT); // Transparent for rounded corners
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initOwner(rootPane.getScene().getWindow());

        Scene dialogScene = new Scene(root);
        dialogScene.setFill(javafx.scene.paint.Color.TRANSPARENT); // Transparent background
        dialogStage.setScene(dialogScene);

        if (themeManager != null) {
            themeManager.applyThemeToScene(dialogScene);
        }

        // Apply CSS and layout to ensure accurate size calculation
        // This is important for cross-platform compatibility
        root.applyCss();
        root.layout();
        
        // Workaround for Ubuntu/Linux dialog sizing issue
        // See: https://stackoverflow.com/questions/55190380/javafx-creates-alert-dialog-which-is-too-small
        // Set resizable to true initially to allow proper sizing on Linux
        dialogStage.setResizable(true);
        
        // Size the stage to fit its content
        // minWidth/minHeight are set in FXML files to ensure the minimum size on Ubuntu
        // sizeToScene() will calculate the actual size based on content
        dialogStage.sizeToScene();
        
        // After the dialog is shown, set resizable back to false (if desired)
        // This ensures proper sizing on Ubuntu while maintaining non-resizable behavior
        dialogStage.setOnShown(e -> Platform.runLater(() -> dialogStage.setResizable(false)));

        return dialogStage;
    }

    /**
     * Opens the insert blank page dialog.
     *
     * @param currentDocument the current PDF document
     * @return InsertDialogController if the dialog was opened successfully, null otherwise
     */
    public InsertDialogController openInsertDialog(PDFDocument currentDocument) {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF", "Please open a PDF file first.");
            return null;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/insert-dialog.fxml"));
            Parent root = loader.load();

            InsertDialogController controller = loader.getController();

            // Get the current page size to use as default in the dialog
            int currentPageIndex = currentDocument.getCurrentPage();
            org.apache.pdfbox.pdmodel.PDPage currentPage = currentDocument.getDocument().getPage(currentPageIndex);
            org.apache.pdfbox.pdmodel.common.PDRectangle currentMediaBox = currentPage.getMediaBox();
            controller.setDefaultSize(currentMediaBox.getWidth(), currentMediaBox.getHeight());

            Stage dialogStage = createDialogStage(root, "Insert Blank Page");
            controller.setDialogStage(dialogStage);
            dialogStage.showAndWait();

            return controller;

        } catch (IOException e) {
            logger.error("Error opening insert dialog", e);
            uiStateManager.showError("Error", "Could not open insert dialog: " + e.getMessage());
            return null;
        }
    }

    /**
     * Displays the About dialog.
     */
    public void showAboutDialog() {
        org.pdflite.dialog.CustomInfoDialog.show(
            "About PDF Lite",
            "PDF Lite - PDF Viewer & Editor",
            """
                Version 1.0
                
                A lightweight PDF viewer with annotation features.
                
                Built with JavaFX and Apache PDFBox""",
            themeManager
        );
    }

    /**
     * Shows a warning dialog for saving encrypted PDFs.
     * Returns true if the user wants to continue, false if canceled.
     *
     * @return true if the user wants to continue saving, false if canceled
     */
    public boolean showEncryptedSaveWarning() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Cảnh báo");
        alert.setHeaderText("File có mật khẩu bảo vệ");
        alert.setContentText("""
                Lưu ý: File mới sẽ KHÔNG CÓ MẬT KHẨU.
                
                Nếu muốn giữ mật khẩu hoặc đặt mật khẩu mới,
                vui lòng sử dụng chức năng 'Encrypt PDF' sau khi lưu.""");

        ButtonType continueButton = new ButtonType("Tiếp tục lưu", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(continueButton, cancelButton);

        if (themeManager != null) {
            themeManager.applyThemeToScene(alert.getDialogPane().getScene());
        }

        var result = alert.showAndWait();
        return result.isPresent() && result.get() == continueButton;
    }
}

