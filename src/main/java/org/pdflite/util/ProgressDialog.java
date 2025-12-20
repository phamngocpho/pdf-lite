package org.pdflite.util;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.manager.ThemeManager;

/**
 * Utility class for showing progress dialogs during long-running operations.
 */
public class ProgressDialog {

    /**
     * Runs a task in background with a progress dialog.
     *
     * @param task          the task to run
     * @param title         the dialog title
     * @param message       the progress message
     * @param onSuccess     callback when task succeeds
     * @param onFailure     callback when task fails
     * @param themeManager  theme manager for styling (can be null)
     * @param <T>           the task result type
     */
    public static <T> void runWithProgress(
            Task<T> task,
            String title,
            String message,
            SuccessCallback<T> onSuccess,
            FailureCallback onFailure,
            ThemeManager themeManager) {

        Stage progressStage = new Stage();
        progressStage.initStyle(StageStyle.TRANSPARENT);
        progressStage.initModality(Modality.APPLICATION_MODAL);
        progressStage.setTitle(title);

        // Main container
        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("custom-confirm-dialog");

        // Custom title bar
        DialogTitleBar titleBar = new DialogTitleBar(title, progressStage);
        mainContainer.getChildren().add(titleBar.getTitleBar());

        // Progress content
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setProgress(-1); // Indeterminate

        Label progressLabel = new Label(message);
        progressLabel.setStyle("-fx-font-size: 14px;");

        VBox contentBox = new VBox(15);
        contentBox.setAlignment(Pos.CENTER);
        contentBox.setPadding(new Insets(30));
        contentBox.getChildren().addAll(progressIndicator, progressLabel);

        mainContainer.getChildren().add(contentBox);

        Scene scene = new Scene(mainContainer, 300, 180);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        progressStage.setScene(scene);
        progressStage.setResizable(false);

        // Apply theme
        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }

        // Handle task completion
        task.setOnSucceeded(e -> {
            progressStage.close();
            if (onSuccess != null) {
                onSuccess.handle(task.getValue());
            }
        });

        task.setOnFailed(e -> {
            progressStage.close();
            if (onFailure != null) {
                onFailure.handle(task.getException());
            }
        });

        // Start task
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();

        progressStage.show();
    }

    /**
     * Callback interface for successful task completion.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    public interface SuccessCallback<T> {
        void handle(T result);
    }

    /**
     * Callback interface for task failure.
     */
    @FunctionalInterface
    public interface FailureCallback {
        void handle(Throwable exception);
    }
}
