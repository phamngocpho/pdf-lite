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
    private static final String FREEHAND_PATH = "M232-172 44-360l21-21 167 167v-44h28v84H260l-28-28 28-28ZM716-84q-8 0-14-6t-6-14 6-14 14-6 14 6 6 14-6 14-14 6Zm96-28v-96l-28-28-21 21 28 28h-21v28h21l-28 28 21 21 28-28v28h-28Zm-224-56-21-21-28 28v-64l21-21 28 28v-64h28v84l-28 28v64l28 28-28 28v28l-28-28-21 21 49 49-12 12q-2 2-5 2t-5-2l-44-44-21 21 44 44q4 4 9 4t9-2l12-12-49-49-21 21 28 28-21 21-28-28v64l-28-28-21 21 28 28h-28v28h28l-28 28 21 21 28-28v64l-28-28-21 21 28 28v84h28v-64l28 28 21-21-28-28v-64l28 28 21-21-28-28h28v-28h-28l28-28-21-21-28 28v-64l28 28 21-21-28-28v-84h-28v64l-28-28-21 21 28 28v64l-28-28-21 21 28 28h-28v28h28l-28 28 21 21 28-28v64l28 28 21-21-28-28v-64l28-28Z";
    private static final String UNDO_PATH = "M301-252v-28h292q62 0 105-43.5T741-429q0-62-43-105t-105-43H266l123 123-20 20-157-157 157-157 20 20-123 123h327q73 0 124.5 51.5T769-429q0 73-51.5 125T593-252H301Z";
    private static final String REDO_PATH = "M367-252q-73 0-124.5-52T191-429q0-73 51.5-124.5T367-605h327L571-728l20-20 157 157-157 157-20-20 123-123H367q-62 0-105 43t-43 105q0 62 43.5 105.5T367-280h292v28H367Z";
    private static final String SETTINGS_PATH = "m416-132-14-112q-21-6-46.5-20T313-294l-103 44-64-112 89-67q-2-12-3.5-25t-1.5-25q0-11 1.5-23.5T235-531l-89-67 64-110 102 43q20-17 43.5-30.5T401-716l15-112h128l14 113q26 9 45.5 20.5T644-665l106-43 64 110-93 70q4 14 4.5 25.5t.5 22.5q0 10-1 21.5t-4 28.5l91 68-64 112-104-45q-21 18-42 30.5T558-245l-14 113H416Zm24-28h78l15-109q30-8 53.5-21.5T636-329l100 43 40-68-88-66q5-18 6.5-32t1.5-28q0-15-1.5-28t-6.5-30l90-68-40-68-103 43q-17-19-47.5-37T532-691l-12-109h-80l-12 108q-30 6-55 20t-51 40l-100-42-40 68 87 65q-5 13-7 29t-2 33q0 15 2 30t6 29l-86 66 40 68 99-42q24 24 49 38t57 22l13 108Zm38-232q37 0 62.5-25.5T566-480q0-37-25.5-62.5T478-568q-37 0-62.5 25.5T390-480q0 37 25.5 62.5T478-392Zm2-88Z";

    private static final int ICON_SIZE = 20;

    /**
     * Sets up icons for all drawing tool buttons.
     *
     * @param btnDrawRect   the rectangle button
     * @param btnDrawCircle the circle button
     * @param btnDrawArrow  the arrow button
     * @param btnDrawFreehand the freehand button
     */
    public void setupDrawingToolIcons(ToggleButton btnDrawRect, ToggleButton btnDrawCircle, ToggleButton btnDrawArrow, ToggleButton btnDrawFreehand) {
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

            if (btnDrawFreehand != null) {
                setButtonIcon(btnDrawFreehand, FREEHAND_PATH);
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
     * Sets up redo button icon.
     *
     * @param redoButton the redo button
     */
    public void setupRedoIcon(javafx.scene.control.Button redoButton) {
        try {
            if (redoButton != null) {
                setRegularButtonIcon(redoButton, REDO_PATH);
            }

            logger.debug("Redo icon setup successfully");
        } catch (Exception e) {
            logger.error("Failed to create redo icon", e);
        }
    }

    /**
     * Sets up settings button icon.
     *
     * @param settingsButton the settings button
     */
    public void setupSettingsIcon(javafx.scene.control.Button settingsButton) {
        try {
            if (settingsButton != null) {
                setRegularButtonIcon(settingsButton, SETTINGS_PATH);
            }

            logger.debug("Settings icon setup successfully");
        } catch (Exception e) {
            logger.error("Failed to create settings icon", e);
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
