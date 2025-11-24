package org.pdflite.manager;

import java.io.IOException;

import org.pdflite.command.CommandManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

/**
 * Manages undo and redo operations for the PDF viewer.
 * <p>
 * This manager acts as a facade between the UI (MainController) and the CommandManager,
 * handling the undo/redo logic and coordinating with other managers for status updates
 * and document reloading.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 */
public class UndoRedoManager {

    private static final Logger logger = LoggerFactory.getLogger(UndoRedoManager.class);

    private final CommandManager commandManager;
    private final UIStateManager uiStateManager;
    private final AnnotationManager annotationManager;
    private final ReloadCallback reloadCallback;

    /**
     * Callback interface for reloading the document.
     */
    @FunctionalInterface
    public interface ReloadCallback {
        void reloadCurrentDocument() throws IOException;
    }

    /**
     * Creates a new UndoRedoManager.
     *
     * @param commandManager    the command manager that handles command history
     * @param uiStateManager    the UI state manager for status updates
     * @param annotationManager the annotation manager for annotation-specific undo (can be null)
     * @param reloadCallback    callback to reload the document after undo/redo operations
     */
    public UndoRedoManager(CommandManager commandManager,
                           UIStateManager uiStateManager,
                           AnnotationManager annotationManager,
                           ReloadCallback reloadCallback) {
        this.commandManager = commandManager;
        this.uiStateManager = uiStateManager;
        this.annotationManager = annotationManager;
        this.reloadCallback = reloadCallback;
    }

    /**
     * Handles the undo operation.
     * <p>
     * First attempts annotation-specific undo (if annotation manager is available),
     * then performs command-based undo if available.
     * </p>
     */
    public void handleUndo() {
        // Part 1: Annotation manager undo (if available)
        if (annotationManager != null) {
            annotationManager.handleUndo();
        }

        // Part 2: Command manager undo
        if (!commandManager.canUndo()) {
            return;
        }

        try {
            String description = commandManager.getUndoDescription();
            logger.info("Performing undo: {}", description);

            boolean success = commandManager.undo(() -> {
                if (reloadCallback != null) {
                    reloadCallback.reloadCurrentDocument();
                }
            });

            if (success) {
                uiStateManager.updateStatus("Undone: " + description);
            }
        } catch (IOException e) {
            logger.error("Error performing undo", e);
            uiStateManager.showError("Undo Error", "Could not undo the action: " + e.getMessage());
        }
    }

    /**
     * Handles the redo operation.
     * <p>
     * Redoes the most recently undone command if available.
     * </p>
     */
    public void handleRedo() {
        if (!commandManager.canRedo()) {
            return;
        }

        try {
            String description = commandManager.getRedoDescription();
            logger.info("Performing redo: {}", description);

            boolean success = commandManager.redo(() -> {
                if (reloadCallback != null) {
                    reloadCallback.reloadCurrentDocument();
                }
            });

            if (success) {
                uiStateManager.updateStatus("Redone: " + description);
            }
        } catch (IOException e) {
            logger.error("Error performing redo", e);
            uiStateManager.showError("Redo Error", "Could not redo the action: " + e.getMessage());
        }
    }

    /**
     * Checks if undo is available.
     *
     * @return true if there are commands that can be undone
     */
    public boolean canUndo() {
        return commandManager.canUndo();
    }

    /**
     * Checks if redo is available.
     *
     * @return true if there are commands that can be redone
     */
    public boolean canRedo() {
        return commandManager.canRedo();
    }

    /**
     * Gets the description of the next command to be undone.
     *
     * @return description string, or null if undo is not available
     */
    public String getUndoDescription() {
        return commandManager.getUndoDescription();
    }

    /**
     * Gets the description of the next command to be redone.
     *
     * @return description string, or null if redo is not available
     */
    public String getRedoDescription() {
        return commandManager.getRedoDescription();
    }

    /**
     * Sets up keyboard shortcuts for undo/redo operations.
     * <p>
     * Registers the following shortcuts:
     * - Ctrl+Z: Undo
     * - Ctrl+Y: Redo
     * - Ctrl+Shift+Z: Redo (alternative)
     * </p>
     *
     * @param scene the scene to register shortcuts on
     */
    public void setupKeyboardShortcuts(Scene scene) {
        if (scene == null) {
            logger.warn("Cannot setup keyboard shortcuts: scene is null");
            return;
        }

        // Ctrl+Z for Undo
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN),
            this::handleUndo
        );

        // Ctrl+Y for Redo
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN),
            this::handleRedo
        );

        // Ctrl+Shift+Z for Redo (alternative)
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
            this::handleRedo
        );

        logger.info("Keyboard shortcuts registered: Ctrl+Z (Undo), Ctrl+Y (Redo), Ctrl+Shift+Z (Redo)");
    }
}

