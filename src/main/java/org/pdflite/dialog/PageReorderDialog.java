package org.pdflite.dialog;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.pdflite.controller.PageReorderDialogController;
import org.pdflite.manager.ThemeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Dialog for reordering PDF pages with drag and drop.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class PageReorderDialog {

    private static final Logger logger = LoggerFactory.getLogger(PageReorderDialog.class);

    /**
     * Shows the page reorder dialog for a PDF file.
     *
     * @param owner        the owner stage
     * @param file         the PDF file
     * @param themeManager the theme manager (optional)
     * @param onSuccess    callback to run when reorder is successful (optional)
     */
    public static void show(Stage owner, File file, ThemeManager themeManager, Runnable onSuccess) {
        PageReorderDialog dialog = new PageReorderDialog();
        dialog.showDialog(owner, file, null, themeManager, onSuccess);
    }

    /**
     * Shows the page reorder dialog for an already opened PDF document.
     *
     * @param owner        the owner stage
     * @param document     the PDF document
     * @param file         the PDF file
     * @param themeManager the theme manager (optional)
     * @param onSuccess    callback to run when reorder is successful (optional)
     */
    public static void show(Stage owner, PDDocument document, File file, ThemeManager themeManager, Runnable onSuccess) {
        PageReorderDialog dialog = new PageReorderDialog();
        dialog.showDialog(owner, file, document, themeManager, onSuccess);
    }

    /**
     * Shows the dialog.
     */
    private void showDialog(Stage owner, File file, PDDocument document, ThemeManager themeManager, Runnable onSuccess) {
        try {
            // Load FXML
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pdflite/page-reorder-dialog.fxml")
            );
            Parent root = loader.load();
            PageReorderDialogController controller = loader.getController();

            // Create stage
            Stage dialogStage = new Stage();
            dialogStage.initStyle(StageStyle.TRANSPARENT);
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(owner);
            dialogStage.setTitle("Reorder Pages");

            // Create the scene
            Scene scene = new Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            dialogStage.setScene(scene);

            // Apply theme
            if (themeManager != null) {
                themeManager.applyThemeToScene(scene);
            }

            // Set controller properties
            controller.setDialogStage(dialogStage);
            controller.setThemeManager(themeManager);

            if (document != null) {
                controller.setSourceDocument(document, file);
            } else {
                controller.setSourceFile(file);
            }

            // Show dialog
            dialogStage.showAndWait();

            // Check if reorder was applied and run callback
            if (controller.isReorderApplied() && onSuccess != null) {
                onSuccess.run();
            }

            // Cleanup
            controller.shutdown();

        } catch (IOException e) {
            logger.error("Error showing page reorder dialog", e);
            throw new RuntimeException("Failed to show page reorder dialog", e);
        }
    }
}
