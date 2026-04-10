package org.pdflite.manager;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages keyboard shortcuts for the application.
 */
public class KeyboardShortcutManager {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardShortcutManager.class);

    private final UndoRedoManager undoRedoManager;
    private final Runnable openShortcutsHelpAction;

    public KeyboardShortcutManager(UndoRedoManager undoRedoManager, Runnable openShortcutsHelpAction) {
        this.undoRedoManager = undoRedoManager;
        this.openShortcutsHelpAction = openShortcutsHelpAction;
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
        if (!event.isShortcutDown()) {
            return;
        }

        if (event.getCode() == KeyCode.Z) {
            handleUndo();
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.Y) {
            handleRedo();
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.SLASH) {
            if (openShortcutsHelpAction != null) {
                openShortcutsHelpAction.run();
                event.consume();
            }
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
}
