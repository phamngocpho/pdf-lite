package org.pdflite.manager;

import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager for window resizing functionality.
 * Allows resizing undecorated windows by dragging edges and corners.
 */
public class WindowResizeManager {

    private static final Logger logger = LoggerFactory.getLogger(WindowResizeManager.class);
    private static final int RESIZE_MARGIN = 5;

    private final BorderPane rootPane;
    private double startX, startY, startWidth, startHeight;
    private double startStageX, startStageY; // Store initial stage position
    private ResizeDirection resizeDirection = ResizeDirection.NONE;

    private enum ResizeDirection {
        NONE, N, S, E, W, NE, NW, SE, SW
    }

    /**
     * Creates a new WindowResizeManager.
     *
     * @param rootPane the root pane of the window
     */
    public WindowResizeManager(BorderPane rootPane) {
        this.rootPane = rootPane;
    }

    /**
     * Initializes resize functionality.
     */
    public void initialize() {
        // Use event filters to handle resize before other handlers
        rootPane.addEventFilter(MouseEvent.MOUSE_MOVED, this::handleMouseMoved);
        rootPane.addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleMousePressed);
        rootPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleMouseDragged);
        rootPane.addEventFilter(MouseEvent.MOUSE_RELEASED, this::handleMouseReleased);
        logger.info("Window resize manager initialized");
    }

    /**
     * Handles mouse moved events to update cursor.
     */
    private void handleMouseMoved(MouseEvent event) {
        Stage stage = getStage();
        if (stage == null) return;

        // Don't handle if event is on a button or other control
        if (isEventOnControl(event)) {
            rootPane.setCursor(javafx.scene.Cursor.DEFAULT);
            return;
        }

        ResizeDirection direction = getResizeDirection(event);
        updateCursor(direction);
    }

    /**
     * Handles mouse pressed events to start resizing.
     */
    private void handleMousePressed(MouseEvent event) {
        Stage stage = getStage();
        if (stage == null) return;

        // Don't handle if event is on a button or other control
        if (isEventOnControl(event)) {
            return;
        }

        resizeDirection = getResizeDirection(event);
        if (resizeDirection != ResizeDirection.NONE) {
            startX = event.getScreenX();
            startY = event.getScreenY();
            startWidth = stage.getWidth();
            startHeight = stage.getHeight();
            startStageX = stage.getX();
            startStageY = stage.getY();
            event.consume(); // Prevent other handlers from processing this event
        }
    }

    /**
     * Handles mouse dragged events to perform resizing.
     */
    private void handleMouseDragged(MouseEvent event) {
        Stage stage = getStage();
        if (stage == null || resizeDirection == ResizeDirection.NONE) return;

        event.consume(); // Prevent other handlers from processing this event

        double deltaX = event.getScreenX() - startX;
        double deltaY = event.getScreenY() - startY;

        double newWidth = startWidth;
        double newHeight = startHeight;
        double newX = startStageX;
        double newY = startStageY;

        switch (resizeDirection) {
            case E:
                newWidth = startWidth + deltaX;
                break;
            case W:
                newWidth = startWidth - deltaX;
                newX = startStageX + deltaX;
                break;
            case S:
                newHeight = startHeight + deltaY;
                break;
            case N:
                newHeight = startHeight - deltaY;
                newY = startStageY + deltaY;
                break;
            case SE:
                newWidth = startWidth + deltaX;
                newHeight = startHeight + deltaY;
                break;
            case SW:
                newWidth = startWidth - deltaX;
                newHeight = startHeight + deltaY;
                newX = startStageX + deltaX;
                break;
            case NE:
                newWidth = startWidth + deltaX;
                newHeight = startHeight - deltaY;
                newY = startStageY + deltaY;
                break;
            case NW:
                newWidth = startWidth - deltaX;
                newHeight = startHeight - deltaY;
                newX = startStageX + deltaX;
                newY = startStageY + deltaY;
                break;
        }

        // Apply minimum size constraints
        if (newWidth >= stage.getMinWidth()) {
            stage.setWidth(newWidth);
            if (resizeDirection == ResizeDirection.W || resizeDirection == ResizeDirection.NW || resizeDirection == ResizeDirection.SW) {
                stage.setX(newX);
            }
        }

        if (newHeight >= stage.getMinHeight()) {
            stage.setHeight(newHeight);
            if (resizeDirection == ResizeDirection.N || resizeDirection == ResizeDirection.NE || resizeDirection == ResizeDirection.NW) {
                stage.setY(newY);
            }
        }
    }

    /**
     * Handles mouse released events to stop resizing.
     */
    private void handleMouseReleased(MouseEvent event) {
        resizeDirection = ResizeDirection.NONE;
        updateCursor(ResizeDirection.NONE);
    }

    /**
     * Determines the resize direction based on mouse position.
     */
    private ResizeDirection getResizeDirection(MouseEvent event) {
        double x = event.getX();
        double y = event.getY();
        double width = rootPane.getWidth();
        double height = rootPane.getHeight();

        boolean left = x < RESIZE_MARGIN;
        boolean right = x > width - RESIZE_MARGIN;
        boolean top = y < RESIZE_MARGIN;
        boolean bottom = y > height - RESIZE_MARGIN;

        if (left && top) return ResizeDirection.NW;
        if (right && top) return ResizeDirection.NE;
        if (left && bottom) return ResizeDirection.SW;
        if (right && bottom) return ResizeDirection.SE;
        if (left) return ResizeDirection.W;
        if (right) return ResizeDirection.E;
        if (top) return ResizeDirection.N;
        if (bottom) return ResizeDirection.S;

        return ResizeDirection.NONE;
    }

    /**
     * Updates the cursor based on resize direction.
     */
    private void updateCursor(ResizeDirection direction) {
        Cursor cursor = switch (direction) {
            case N, S -> Cursor.V_RESIZE;
            case E, W -> Cursor.H_RESIZE;
            case NE, SW -> Cursor.NE_RESIZE;
            case NW, SE -> Cursor.NW_RESIZE;
            default -> Cursor.DEFAULT;
        };
        rootPane.setCursor(cursor);
    }

    /**
     * Gets the stage from the root pane.
     */
    private Stage getStage() {
        if (rootPane == null || rootPane.getScene() == null || rootPane.getScene().getWindow() == null) {
            return null;
        }
        return (Stage) rootPane.getScene().getWindow();
    }

    /**
     * Checks if the event target is a control (button, etc.) that should not be intercepted.
     */
    private boolean isEventOnControl(MouseEvent event) {
        return event.getTarget() instanceof javafx.scene.control.Control ||
                event.getTarget() instanceof javafx.scene.control.Labeled;
    }
}
