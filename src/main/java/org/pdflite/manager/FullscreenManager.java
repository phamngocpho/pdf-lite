package org.pdflite.manager;

import javafx.animation.PauseTransition;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Manages fullscreen mode for the application.
 * Handles toolbar auto-hide, keyboard shortcuts, and fullscreen toggling.
 */
public class FullscreenManager {

    private final BorderPane rootPane;
    private final ToolBar toolbar;
    private final FullscreenListener fullscreenListener;

    private boolean isFullScreen = false;
    private PauseTransition toolbarHideTimer;
    private Stage primaryStage;
    private javafx.beans.value.ChangeListener<Boolean> fullScreenListener;

    /**
     * Interface for listening to fullscreen events.
     */
    public interface FullscreenListener {
        void onFullscreenChanged(boolean isFullscreen);
        void updateStatus(String message);
    }

    /**
     * Creates a new FullscreenManager.
     *
     * @param rootPane the root pane
     * @param toolbar the toolbar to hide/show
     * @param fullscreenListener listener for fullscreen events
     */
    public FullscreenManager(BorderPane rootPane, ToolBar toolbar, FullscreenListener fullscreenListener) {
        this.rootPane = rootPane;
        this.toolbar = toolbar;
        this.fullscreenListener = fullscreenListener;
        initialize();
    }

    /**
     * Initializes the fullscreen manager.
     */
    private void initialize() {
        toolbarHideTimer = new PauseTransition(Duration.seconds(3));
        toolbarHideTimer.setOnFinished(e -> {
            if (isFullScreen) {
                hideToolbar();
            }
        });

        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
                oldScene.removeEventFilter(MouseEvent.MOUSE_MOVED, this::handleMouseMove);
            }
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
                newScene.addEventFilter(MouseEvent.MOUSE_MOVED, this::handleMouseMove);

                if (newScene.getWindow() != null) {
                    attachStageListeners((Stage) newScene.getWindow());
                } else {
                    newScene.windowProperty().addListener((winObs, oldWin, newWin) -> {
                        if (newWin instanceof Stage stage) {
                            attachStageListeners(stage);
                        }
                    });
                }
            }
        });
    }

    /**
     * Attaches listeners to the stage.
     *
     * @param stage the stage
     */
    private void attachStageListeners(Stage stage) {
        if (stage == null || stage == primaryStage) {
            return;
        }
        if (primaryStage != null && fullScreenListener != null) {
            primaryStage.fullScreenProperty().removeListener(fullScreenListener);
        }
        primaryStage = stage;
        stage.setFullScreenExitHint("");
        fullScreenListener = (obs, wasFull, isNowFull) -> {
            isFullScreen = isNowFull;
            if (isNowFull) {
                if (!rootPane.getStyleClass().contains("full-screen-mode")) {
                    rootPane.getStyleClass().add("full-screen-mode");
                }
                showToolbar();
                scheduleToolbarHide();
                if (fullscreenListener != null) {
                    fullscreenListener.updateStatus("Full screen mode");
                }
            } else {
                rootPane.getStyleClass().remove("full-screen-mode");
                toolbarHideTimer.stop();
                showToolbar();
                if (fullscreenListener != null) {
                    fullscreenListener.updateStatus("Exited full screen");
                }
            }
            if (fullscreenListener != null) {
                fullscreenListener.onFullscreenChanged(isNowFull);
            }
        };
        stage.fullScreenProperty().addListener(fullScreenListener);
    }

    /**
     * Handles key press events.
     *
     * @param event the key event
     */
    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.F11) {
            toggleFullScreen();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE && isFullScreen) {
            exitFullScreen();
            event.consume();
        }
    }

    /**
     * Handles mouse move events.
     *
     * @param event the mouse event
     */
    private void handleMouseMove(MouseEvent event) {
        if (isFullScreen) {
            showToolbar();
            scheduleToolbarHide();
        }
    }

    /**
     * Toggles fullscreen mode.
     */
    public void toggleFullScreen() {
        Stage stage = getStage();
        if (stage == null) return;
        stage.setFullScreen(!stage.isFullScreen());
    }

    /**
     * Exits fullscreen mode.
     */
    public void exitFullScreen() {
        Stage stage = getStage();
        if (stage == null) return;
        stage.setFullScreen(false);
    }

    /**
     * Gets the stage from the root pane.
     *
     * @return the stage, or null if not available
     */
    private Stage getStage() {
        if (rootPane == null || rootPane.getScene() == null) {
            return null;
        }
        Window window = rootPane.getScene().getWindow();
        return window instanceof Stage ? (Stage) window : null;
    }

    /**
     * Schedules toolbar hide.
     */
    private void scheduleToolbarHide() {
        if (isFullScreen && toolbarHideTimer != null) {
            toolbarHideTimer.playFromStart();
        }
    }

    /**
     * Shows the toolbar.
     */
    private void showToolbar() {
        if (toolbar != null) {
            toolbar.setVisible(true);
            toolbar.setManaged(true);
            toolbar.setOpacity(1.0);
        }
    }

    /**
     * Hides the toolbar.
     */
    private void hideToolbar() {
        if (toolbar != null) {
            toolbar.setVisible(false);
            toolbar.setManaged(false);
        }
    }

    /**
     * Checks if fullscreen is active.
     *
     * @return true if fullscreen is active
     */
    public boolean isFullScreen() {
        return isFullScreen;
    }
}

