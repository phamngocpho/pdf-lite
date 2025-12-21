package org.pdflite.manager;

import javafx.application.Platform;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager for save status indicator in the title bar.
 * Handles displaying save status (saved/unsaved) and triggering auto-save.
 */
public class SaveStatusManager {
    private static final Logger logger = LoggerFactory.getLogger(SaveStatusManager.class);
    
    private final StackPane saveStatusIndicator;
    private final AutoSaveManager autoSaveManager;
    private PDFDocument currentDocument;

    public SaveStatusManager(StackPane saveStatusIndicator, 
                           AutoSaveManager autoSaveManager,
                           UIStateManager uiStateManager) {
        this.saveStatusIndicator = saveStatusIndicator;
        this.autoSaveManager = autoSaveManager;
    }

    /**
     * Sets the current document.
     *
     * @param document the current document
     */
    public void setCurrentDocument(PDFDocument document) {
        this.currentDocument = document;
    }

    /**
     * Updates the save status indicator in the title bar.
     *
     * @param saved true if document is saved, false if unsaved
     */
    public void updateSaveStatusIndicator(boolean saved) {
        if (saveStatusIndicator == null || currentDocument == null) {
            return;
        }

        Platform.runLater(() -> {
            saveStatusIndicator.getChildren().clear();
            saveStatusIndicator.setVisible(true);
            saveStatusIndicator.setManaged(true);
            
            // Set fixed size for the container
            saveStatusIndicator.setMinSize(20, 20);
            saveStatusIndicator.setMaxSize(20, 20);
            saveStatusIndicator.setPrefSize(20, 20);

            javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
            
            if (saved) {
                // Cloud done icon (green)
                icon.setContent("m414-316 190-190-20-20-170 170-86-86-20 20 106 106ZM260-212q-70 0-119-49T92-380q0-66 47-117t115-51q10-86 74.5-143T480-748q95 0 161.5 66.5T708-520v52h32q54 0 91 37t37 91q0 54-37 91t-91 37H260Z");
                icon.setFill(javafx.scene.paint.Color.web("#4caf50"));
            } else {
                // Cloud alert icon (orange)
                icon.setContent("M260-212q-70 0-119-48.77Q92-309.55 92-380q0-65.52 47-116.76Q186-548 254-548q10-86 74.5-143T480-748q95.27 0 161.64 66.36Q708-615.27 708-520v52h32q54 0 91 37t37 91q0 54-37 91t-91 37H260Zm220.04-161q5.96 0 9.96-4.04 4-4.03 4-10 0-5.96-4.04-9.96-4.03-4-10-4-5.96 0-9.96 4.04-4 4.03-4 10 0 5.96 4.04 9.96 4.03 4 10 4ZM466-461h28v-153h-28v153Z");
                icon.setFill(javafx.scene.paint.Color.web("#ff9800"));
            }

            // Scale icon to fit container
            double scale = 20.0 / 960.0;
            icon.setScaleX(scale);
            icon.setScaleY(scale);

            saveStatusIndicator.getChildren().add(icon);
            
            // Add tooltip
            Tooltip tooltip = new Tooltip(saved ? "All changes saved" : "Unsaved changes");
            Tooltip.install(saveStatusIndicator, tooltip);
        });
    }
    
    /**
     * Hides the save status indicator.
     */
    public void hideSaveStatusIndicator() {
        if (saveStatusIndicator != null) {
            Platform.runLater(() -> {
                saveStatusIndicator.setVisible(false);
                saveStatusIndicator.setManaged(false);
            });
        }
    }
    
    /**
     * Triggers auto-save for the current document.
     * Should be called after any edit operation.
     */
    public void triggerAutoSave() {
        if (autoSaveManager != null && currentDocument != null && currentDocument.hasUnsavedEdits()) {
            autoSaveManager.scheduleAutoSave();
            updateSaveStatusIndicator(false); // Show unsaved
        }
    }
}
