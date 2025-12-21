package org.pdflite.util;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.pdflite.manager.WindowControlIconManager;

/**
 * Utility class to create custom title bars for dialogs.
 * Provides a consistent look with the main window's title bar.
 */
public class DialogTitleBar {

    private final HBox titleBar;
    private final Label titleLabel;
    private double xOffset = 0;
    private double yOffset = 0;

    /**
     * Creates a new dialog title bar.
     *
     * @param title the dialog title
     * @param stage the dialog stage
     */
    public DialogTitleBar(String title, Stage stage) {
        // Create title bar container
        titleBar = new HBox();
        titleBar.getStyleClass().add("custom-title-bar");
        titleBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Create title label
        titleLabel = new Label(title);
        titleLabel.getStyleClass().add("title-bar-label");
        HBox.setMargin(titleLabel, new Insets(0, 0, 0, 12));

        // Create spacer to push close button to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Create close button
        Button closeButton = new Button("✕");
        closeButton.getStyleClass().addAll("title-bar-button", "close-button");
        closeButton.setFocusTraversable(false);
        closeButton.setOnAction(e -> stage.close());

        // Setup icon for close button
        WindowControlIconManager iconManager = new WindowControlIconManager();
        iconManager.setupWindowControlIcons(null, null, closeButton);

        // Add all elements to title bar: title, spacer, close button
        titleBar.getChildren().addAll(titleLabel, spacer, closeButton);

        // Make title bar draggable
        makeDraggable(stage);

        // Apply rounded corners to stage
        applyRoundedCorners(stage);
    }

    /**
     * Applies rounded corners to the dialog stage.
     *
     * @param stage the stage to apply rounded corners to
     */
    private void applyRoundedCorners(Stage stage) {
        stage.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setFill(javafx.scene.paint.Color.TRANSPARENT);

                // Apply clip to root for rounded corners
                newScene.rootProperty().addListener((obsRoot, oldRoot, newRoot) -> {
                    if (newRoot != null) {
                        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
                        clip.widthProperty().bind(newScene.widthProperty());
                        clip.heightProperty().bind(newScene.heightProperty());
                        clip.setArcWidth(24);  // 12 * 2 for radius
                        clip.setArcHeight(24);
                        newRoot.setClip(clip);
                    }
                });
            }
        });
    }

    /**
     * Makes the title bar draggable.
     *
     * @param stage the stage to make draggable
     */
    private void makeDraggable(Stage stage) {
        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        titleBar.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });
    }

    /**
     * Gets the title bar node.
     *
     * @return the title bar HBox
     */
    public HBox getTitleBar() {
        return titleBar;
    }

    /**
     * Sets the title text.
     *
     * @param title the new title
     */
    public void setTitle(String title) {
        titleLabel.setText(title);
    }
}
