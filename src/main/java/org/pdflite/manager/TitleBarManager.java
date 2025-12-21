package org.pdflite.manager;

import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager for custom title bar functionality.
 * Handles dragging, double-click to maximize, and window control buttons.
 */
public class TitleBarManager {

    private static final Logger logger = LoggerFactory.getLogger(TitleBarManager.class);

    private final WindowControlIconManager iconManager;
    private final HBox customTitleBar;
    private final Button minimizeButton;
    private final Button maximizeButton;
    private final Button closeButton;
    private final Runnable onCloseAction;
    private final Runnable onMaximizeAction;

    private double xOffset = 0;
    private double yOffset = 0;

    /**
     * Creates a new TitleBarManager.
     *
     * @param customTitleBar   the custom title bar HBox
     * @param minimizeButton   the minimize button
     * @param maximizeButton   the maximize button
     * @param closeButton      the close button
     * @param onCloseAction    action to run when close is clicked
     * @param onMaximizeAction action to run when maximize is clicked
     */
    public TitleBarManager(HBox customTitleBar, Button minimizeButton, Button maximizeButton,
                           Button closeButton, Runnable onCloseAction, Runnable onMaximizeAction) {
        this.customTitleBar = customTitleBar;
        this.minimizeButton = minimizeButton;
        this.maximizeButton = maximizeButton;
        this.closeButton = closeButton;
        this.onCloseAction = onCloseAction;
        this.onMaximizeAction = onMaximizeAction;
        this.iconManager = new WindowControlIconManager();
    }

    /**
     * Initializes the title bar with all functionality.
     */
    public void initialize() {
        setupIcons();
        makeDraggable();
        setupMaximizeListener();
        logger.info("Title bar initialized successfully");
    }

    /**
     * Sets up a listener to update the maximize icon when window state changes.
     */
    private void setupMaximizeListener() {
        // Not needed anymore since we manually track maximize state
    }

    /**
     * Sets up icons for window control buttons.
     */
    private void setupIcons() {
        iconManager.setupWindowControlIcons(minimizeButton, maximizeButton, closeButton);
    }

    /**
     * Makes the title bar draggable and adds double-click to maximize.
     */
    private void makeDraggable() {
        if (customTitleBar == null) {
            return;
        }

        customTitleBar.setOnMousePressed(event -> {
            if (customTitleBar.getScene() != null && customTitleBar.getScene().getWindow() != null) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });

        customTitleBar.setOnMouseDragged(event -> {
            if (customTitleBar.getScene() != null && customTitleBar.getScene().getWindow() != null) {
                Stage stage = (Stage) customTitleBar.getScene().getWindow();
                if (!isMaximized) {
                    stage.setX(event.getScreenX() - xOffset);
                    stage.setY(event.getScreenY() - yOffset);
                }
            }
        });

        customTitleBar.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && onMaximizeAction != null) {
                onMaximizeAction.run();
            }
        });

        logger.debug("Title bar made draggable");
    }

    /**
     * Handles minimize button click.
     *
     * @param stage the stage to minimize
     */
    public void handleMinimize(Stage stage) {
        logger.info("handleMinimize called");
        try {
            stage.setIconified(true);
            logger.info("Window minimized successfully");
        } catch (Exception e) {
            logger.error("Error minimizing window", e);
        }
    }

    private double restoreX, restoreY, restoreWidth, restoreHeight;
    private boolean isMaximized = false;

    /**
     * Handles maximize/restore button click.
     *
     * @param stage the stage to maximize or restore
     */
    public void handleMaximize(Stage stage) {
        logger.info("handleMaximize called");
        try {
            if (isMaximized) {
                // Restore to previous size
                stage.setX(restoreX);
                stage.setY(restoreY);
                stage.setWidth(restoreWidth);
                stage.setHeight(restoreHeight);
                isMaximized = false;
                iconManager.updateMaximizeIcon(maximizeButton, false);
                logger.info("Window restored");
            } else {
                // Save current size
                restoreX = stage.getX();
                restoreY = stage.getY();
                restoreWidth = stage.getWidth();
                restoreHeight = stage.getHeight();

                // Maximize to visual bounds (excludes taskbar)
                maximizeToScreen(stage);

                isMaximized = true;
                iconManager.updateMaximizeIcon(maximizeButton, true);
                logger.info("Window maximized");
            }
        } catch (Exception e) {
            logger.error("Error maximizing/restoring window", e);
        }
    }

    /**
     * Maximizes the stage to fill the screen (excluding taskbar).
     *
     * @param stage the stage to maximize
     */
    public static void maximizeToScreen(javafx.stage.Stage stage) {
        javafx.stage.Screen screen = javafx.stage.Screen.getPrimary();
        javafx.geometry.Rectangle2D bounds = screen.getVisualBounds();
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
    }

    /**
     * Handles close button click.
     */
    public void handleClose() {
        logger.info("handleClose called");
        if (onCloseAction != null) {
            onCloseAction.run();
        }
    }

    /**
     * Sets the maximized state and updates the icon.
     * Used when the window is maximized programmatically on startup.
     *
     * @param maximized true if window is maximized, false otherwise
     */
    public void setMaximizedState(boolean maximized) {
        this.isMaximized = maximized;
        iconManager.updateMaximizeIcon(maximizeButton, maximized);

        // Set default restore size (centered on screen)
        if (maximized) {
            javafx.stage.Screen screen = javafx.stage.Screen.getPrimary();
            javafx.geometry.Rectangle2D bounds = screen.getVisualBounds();

            // Default size: 1200x800 (from FXML)
            restoreWidth = 1200;
            restoreHeight = 800;

            // Center position
            restoreX = bounds.getMinX() + (bounds.getWidth() - restoreWidth) / 2;
            restoreY = bounds.getMinY() + (bounds.getHeight() - restoreHeight) / 2;
        }

        logger.info("Maximized state set to: {}", maximized);
    }
}
