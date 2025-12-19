package org.pdflite.manager;

import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager for window control icons (minimize, maximize, close).
 * Handles creation and updating of SVG icons for custom title bar buttons.
 */
public class WindowControlIconManager {

    private static final Logger logger = LoggerFactory.getLogger(WindowControlIconManager.class);

    // SVG path data for icons
    private static final String MINIMIZE_PATH = "M266-172v-28h428v28H266Z";
    private static final String MAXIMIZE_PATH = "M232-172q-26 0-43-17t-17-43v-496q0-26 17-43t43-17h496q26 0 43 17t17 43v496q0 26-17 43t-43 17H232Zm0-28h496q14 0 23-9t9-23v-496q0-14-9-23t-23-9H232q-14 0-23 9t-9 23v496q0 14 9 23t23 9Zm-32 0v-560 560Z";
    private static final String RESTORE_PATH = "M232-172q-26 0-43-17t-17-43v-356h28v356q0 14 9 23t23 9h356v28H232Zm128-128q-26 0-43-17t-17-43v-368q0-26 17-43t43-17h368q26 0 43 17t17 43v368q0 26-17 43t-43 17H360Zm0-28h368q14 0 23-9t9-23v-368q0-14-9-23t-23-9H360q-14 0-23 9t-9 23v368q0 14 9 23t23 9Zm-32 0v-424 424Z";
    private static final String CLOSE_PATH = "m256-236-20-20 224-224-224-224 20-20 224 224 224-224 20 20-224 224 224 224-20 20-224-224-224 224Z";

    private static final int ICON_SIZE = 16;

    /**
     * Sets up icons for all window control buttons.
     *
     * @param minimizeButton the minimize button
     * @param maximizeButton the maximize button
     * @param closeButton    the close button
     */
    public void setupWindowControlIcons(Button minimizeButton, Button maximizeButton, Button closeButton) {
        try {
            if (minimizeButton != null) {
                setButtonIcon(minimizeButton, MINIMIZE_PATH);
            }

            if (maximizeButton != null) {
                setButtonIcon(maximizeButton, MAXIMIZE_PATH);
            }

            if (closeButton != null) {
                setButtonIcon(closeButton, CLOSE_PATH);
            }

            logger.debug("Window control icons setup successfully");
        } catch (Exception e) {
            logger.error("Failed to create window control icons", e);
        }
    }

    /**
     * Updates the maximize button icon based on window state.
     *
     * @param maximizeButton the maximize button
     * @param isMaximized    true if window is maximized, false otherwise
     */
    public void updateMaximizeIcon(Button maximizeButton, boolean isMaximized) {
        if (maximizeButton == null) {
            return;
        }

        try {
            String iconPath = isMaximized ? RESTORE_PATH : MAXIMIZE_PATH;
            setButtonIcon(maximizeButton, iconPath);
            logger.debug("Maximize icon updated: {}", isMaximized ? "restore" : "maximize");
        } catch (Exception e) {
            logger.error("Failed to update maximize icon", e);
        }
    }

    /**
     * Sets an SVG icon for a button.
     *
     * @param button   the button to set icon for
     * @param pathData the SVG path data
     */
    private void setButtonIcon(Button button, String pathData) {
        SVGPath icon = new SVGPath();
        icon.setContent(pathData);
        icon.getStyleClass().add("window-icon-svg");

        StackPane iconPane = new StackPane(icon);
        iconPane.setMinSize(ICON_SIZE, ICON_SIZE);
        iconPane.setPrefSize(ICON_SIZE, ICON_SIZE);
        iconPane.setMaxSize(ICON_SIZE, ICON_SIZE);
        iconPane.setPickOnBounds(false); // Allow clicks to pass through to button

        button.setGraphic(iconPane);
        button.setText(""); // Clear any default text
    }
}
