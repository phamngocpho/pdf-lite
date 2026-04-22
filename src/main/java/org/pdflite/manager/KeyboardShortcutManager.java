package org.pdflite.manager;

import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BooleanSupplier;

/**
 * Manages keyboard shortcuts for the application.
 */
public class KeyboardShortcutManager {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardShortcutManager.class);

    private final UndoRedoManager undoRedoManager;
    private final Runnable openShortcutsHelpAction;
    private final Runnable previousPageAction;
    private final Runnable nextPageAction;
    private final Runnable zoomInAction;
    private final Runnable zoomOutAction;
    private final Runnable fitToWidthAction;
    private final Runnable fitToPageAction;
    private final Runnable searchAction;
    private final Runnable hideSearchAction;
    private final Runnable fullScreenAction;
    private final Runnable presentationModeAction;
    private final BooleanSupplier presentationModeActiveSupplier;
    private final Runnable exitPresentationModeAction;

    public KeyboardShortcutManager(UndoRedoManager undoRedoManager, Runnable openShortcutsHelpAction) {
        this(undoRedoManager, openShortcutsHelpAction, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    public KeyboardShortcutManager(UndoRedoManager undoRedoManager,
                                   Runnable openShortcutsHelpAction,
                                   Runnable previousPageAction,
                                   Runnable nextPageAction,
                                   Runnable zoomInAction,
                                   Runnable zoomOutAction,
                                   Runnable fitToWidthAction,
                                   Runnable fitToPageAction,
                                   Runnable searchAction,
                                   Runnable hideSearchAction,
                                   Runnable fullScreenAction,
                                   Runnable presentationModeAction,
                                   BooleanSupplier presentationModeActiveSupplier,
                                   Runnable exitPresentationModeAction) {
        this.undoRedoManager = undoRedoManager;
        this.openShortcutsHelpAction = openShortcutsHelpAction;
        this.previousPageAction = previousPageAction;
        this.nextPageAction = nextPageAction;
        this.zoomInAction = zoomInAction;
        this.zoomOutAction = zoomOutAction;
        this.fitToWidthAction = fitToWidthAction;
        this.fitToPageAction = fitToPageAction;
        this.searchAction = searchAction;
        this.hideSearchAction = hideSearchAction;
        this.fullScreenAction = fullScreenAction;
        this.presentationModeAction = presentationModeAction;
        this.presentationModeActiveSupplier = presentationModeActiveSupplier;
        this.exitPresentationModeAction = exitPresentationModeAction;
    }

    /**
     * Sets up keyboard shortcuts for the given scene.
     */
    public void setupKeyboardShortcuts(Scene scene) {
        if (scene == null) {
            return;
        }

        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyboardShortcuts);
        logger.info("Keyboard shortcuts initialized");
    }

    /**
     * Removes keyboard shortcuts from the given scene.
     */
    public void removeKeyboardShortcuts(Scene scene) {
        if (scene == null) {
            return;
        }

        scene.removeEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyboardShortcuts);
    }

    private void handleKeyboardShortcuts(KeyEvent event) {
        if (isHelpShortcut(event)) {
            runAndConsume(openShortcutsHelpAction, event);
            return;
        }

        if (isTextInputEvent(event)) {
            return;
        }

        if (!isPlainNavigationBlocked(event) && event.getCode() == KeyCode.PAGE_UP) {
            runAndConsume(previousPageAction, event);
            return;
        }
        if (!isPlainNavigationBlocked(event) && (event.getCode() == KeyCode.PAGE_DOWN || event.getCode() == KeyCode.SPACE)) {
            runAndConsume(nextPageAction, event);
            return;
        }
        if (event.getCode() == KeyCode.ESCAPE && isPresentationModeActive()) {
            runAndConsume(exitPresentationModeAction, event);
            return;
        }
        if (event.getCode() == KeyCode.ESCAPE) {
            runAndConsume(hideSearchAction, event);
            return;
        }
        if (event.getCode() == KeyCode.F11) {
            runAndConsume(fullScreenAction, event);
            return;
        }
        if (event.getCode() == KeyCode.F5) {
            runAndConsume(presentationModeAction, event);
            return;
        }

        if (!event.isShortcutDown()) {
            return;
        }

        if (event.getCode() == KeyCode.Z && !event.isShiftDown()) {
            runAndConsume(this::handleUndo, event);
            return;
        }
        if (event.getCode() == KeyCode.Y || (event.getCode() == KeyCode.Z && event.isShiftDown())) {
            runAndConsume(this::handleRedo, event);
            return;
        }
        if (event.getCode() == KeyCode.F) {
            runAndConsume(searchAction, event);
            return;
        }
        if (event.getCode() == KeyCode.PLUS || event.getCode() == KeyCode.EQUALS || event.getCode() == KeyCode.ADD) {
            runAndConsume(zoomInAction, event);
            return;
        }
        if (event.getCode() == KeyCode.MINUS || event.getCode() == KeyCode.SUBTRACT) {
            runAndConsume(zoomOutAction, event);
            return;
        }
        if (event.getCode() == KeyCode.DIGIT0 || event.getCode() == KeyCode.NUMPAD0) {
            runAndConsume(fitToWidthAction, event);
            return;
        }
        if (event.getCode() == KeyCode.DIGIT1 || event.getCode() == KeyCode.NUMPAD1) {
            runAndConsume(fitToPageAction, event);
        }
    }

    private boolean isHelpShortcut(KeyEvent event) {
        if (event.isShortcutDown() && (event.getCode() == KeyCode.K || event.getCode() == KeyCode.SLASH)) {
            return true;
        }
        return "?".equals(event.getText()) && !isTextInputEvent(event);
    }

    private boolean isTextInputEvent(KeyEvent event) {
        return getFocusOwner(event) instanceof TextInputControl || event.getTarget() instanceof TextInputControl;
    }

    private boolean isPlainNavigationBlocked(KeyEvent event) {
        Node focusOwner = getFocusOwner(event);
        return focusOwner instanceof TextInputControl || focusOwner instanceof ButtonBase;
    }

    private Node getFocusOwner(KeyEvent event) {
        if (event.getTarget() instanceof Node targetNode && targetNode.getScene() != null) {
            return targetNode.getScene().getFocusOwner();
        }
        return null;
    }

    private void runAndConsume(Runnable action, KeyEvent event) {
        if (action != null) {
            action.run();
            event.consume();
        }
    }

    private void handleUndo() {
        if (undoRedoManager != null) {
            undoRedoManager.handleUndo();
        }
    }

    private void handleRedo() {
        if (undoRedoManager != null) {
            undoRedoManager.handleRedo();
        }
    }

    private boolean isPresentationModeActive() {
        return presentationModeActiveSupplier != null && presentationModeActiveSupplier.getAsBoolean();
    }
}
