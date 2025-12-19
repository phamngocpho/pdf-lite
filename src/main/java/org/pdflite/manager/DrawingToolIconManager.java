package org.pdflite.manager;

import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager for drawing tool icons.
 * Handles creation and updating of SVG icons for drawing tool buttons.
 */
public class DrawingToolIconManager {

    private static final Logger logger = LoggerFactory.getLogger(DrawingToolIconManager.class);

    // SVG path data for drawing tools (extracted from SVG files)
    private static final String RECTANGLE_PATH = "M192-212q-26 0-43-17t-17-43v-416q0-26 17-43t43-17h576q26 0 43 17t17 43v416q0 26-17 43t-43 17H192Zm0-28h576q12 0 22-10t10-22v-416q0-12-10-22t-22-10H192q-12 0-22 10t-10 22v416q0 12 10 22t22 10Zm-32 0v-480 480Z";
    private static final String CIRCLE_PATH = "M480.17-132q-72.17 0-135.73-27.39-63.56-27.39-110.57-74.35-47.02-46.96-74.44-110.43Q132-407.65 132-479.83q0-72.17 27.39-135.73 27.39-63.56 74.35-110.57 46.96-47.02 110.43-74.44Q407.65-828 479.83-828q72.17 0 135.73 27.39 63.56 27.39 110.57 74.35 47.02 46.96 74.44 110.43Q828-552.35 828-480.17q0 72.17-27.39 135.73-27.39 63.56-74.35 110.57-46.96 47.02-110.43 74.44Q552.35-132 480.17-132Zm-.17-28q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z";
    private static final String ARROW_PATH = "m502-313 84-153H106v-28h480l-84-153 263 167-263 167Z";
    private static final String UNDO_PATH = "M301-252v-28h292q62 0 105-43.5T741-429q0-62-43-105t-105-43H266l123 123-20 20-157-157 157-157 20 20-123 123h327q73 0 124.5 51.5T769-429q0 73-51.5 125T593-252H301Z";

    private static final int ICON_SIZE = 20;

    /**
     * Sets up icons for all drawing tool buttons.
     *
     * @param btnDrawRect   the rectangle button
     * @param btnDrawCircle the circle button
     * @param btnDrawArrow  the arrow button
     */
    public void setupDrawingToolIcons(ToggleButton btnDrawRect, ToggleButton btnDrawCircle, ToggleButton btnDrawArrow) {
        try {
            if (btnDrawRect != null) {
                setButtonIcon(btnDrawRect, RECTANGLE_PATH);
            }

            if (btnDrawCircle != null) {
                setButtonIcon(btnDrawCircle, CIRCLE_PATH);
            }

            if (btnDrawArrow != null) {
                setButtonIcon(btnDrawArrow, ARROW_PATH);
            }

            logger.debug("Drawing tool icons setup successfully");
        } catch (Exception e) {
            logger.error("Failed to create drawing tool icons", e);
        }
    }

    /**
     * Sets up undo button icon.
     *
     * @param undoButton the undo button
     */
    public void setupUndoIcon(javafx.scene.control.Button undoButton) {
        try {
            if (undoButton != null) {
                setRegularButtonIcon(undoButton, UNDO_PATH);
            }

            logger.debug("Undo icon setup successfully");
        } catch (Exception e) {
            logger.error("Failed to create undo icon", e);
        }
    }

    /**
     * Sets an SVG icon for a toggle button.
     *
     * @param button   the button to set icon for
     * @param pathData the SVG path data
     */
    private void setButtonIcon(ToggleButton button, String pathData) {
        SVGPath icon = new SVGPath();
        icon.setContent(pathData);
        icon.getStyleClass().add("drawing-tool-icon-svg");

        StackPane iconPane = new StackPane(icon);
        iconPane.setMinSize(ICON_SIZE, ICON_SIZE);
        iconPane.setPrefSize(ICON_SIZE, ICON_SIZE);
        iconPane.setMaxSize(ICON_SIZE, ICON_SIZE);
        iconPane.setPickOnBounds(false); // Allow clicks to pass through to button

        button.setGraphic(iconPane);
        button.setText(""); // Clear any default text
    }

    /**
     * Sets an SVG icon for a regular button.
     *
     * @param button   the button to set icon for
     * @param pathData the SVG path data
     */
    private void setRegularButtonIcon(javafx.scene.control.Button button, String pathData) {
        SVGPath icon = new SVGPath();
        icon.setContent(pathData);
        icon.getStyleClass().add("drawing-tool-icon-svg");

        StackPane iconPane = new StackPane(icon);
        iconPane.setMinSize(ICON_SIZE, ICON_SIZE);
        iconPane.setPrefSize(ICON_SIZE, ICON_SIZE);
        iconPane.setMaxSize(ICON_SIZE, ICON_SIZE);
        iconPane.setPickOnBounds(false); // Allow clicks to pass through to button

        button.setGraphic(iconPane);
        button.setText(""); // Clear any default text
    }
}
