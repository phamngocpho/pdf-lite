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
    
    public KeyboardShortcutManager(UndoRedoManager undoRedoManager) {
        this.undoRedoManager = undoRedoManager;
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
    
    /**
     * Handles keyboard shortcuts for undo/redo operations.
     */
    private void handleKeyboardShortcuts(KeyEvent event) {
        if (event.isControlDown()) {
            if (event.getCode() == KeyCode.Z) {
                handleUndo();
                event.consume();
            } else if (event.getCode() == KeyCode.Y) {
                handleRedo();
                event.consume();
            }
        }
    }
    
    /**
     * Handles undo operation.
     */
    private void handleUndo() {
        if (undoRedoManager != null) {
            undoRedoManager.handleUndo();
        }
    }
    
    /**
     * Handles redo operation.
     */
    private void handleRedo() {
        if (undoRedoManager != null) {
            undoRedoManager.handleRedo();
        }
    }
}
