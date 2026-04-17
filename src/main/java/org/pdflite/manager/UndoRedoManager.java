package org.pdflite.manager;

import javafx.scene.control.Button;
import org.pdflite.command.CommandManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages undo/redo operations for the application.
 * Handles command execution, undo, redo, and UI state updates.
 */
public class UndoRedoManager {
    private static final Logger logger = LoggerFactory.getLogger(UndoRedoManager.class);

    private final CommandManager commandManager;
    private final UIStateManager uiStateManager;
    private Button undoButton;
    private Button redoButton;

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    /**
     * Creates a new UndoRedoManager.
     *
     * @param uiStateManager the UI state manager for status updates
     */
    public UndoRedoManager(UIStateManager uiStateManager) {
        this.uiStateManager = uiStateManager;
        this.commandManager = new CommandManager();

        // Setup listeners for button states
        commandManager.setUndoStateListener(canUndo -> {
            if (undoButton != null) {
                undoButton.setDisable(!canUndo);
            }
        });

        commandManager.setRedoStateListener(canRedo -> {
            if (redoButton != null) {
                redoButton.setDisable(!canRedo);
            }
        });

        logger.info("UndoRedoManager initialized");
    }

    /**
     * Sets the undo and redo buttons for state management.
     *
     * @param undoButton the undo button
     * @param redoButton the redo button
     */
    public void setButtons(Button undoButton, Button redoButton) {
        this.undoButton = undoButton;
        this.redoButton = redoButton;

        // Update initial button states
        if (undoButton != null) {
            undoButton.setDisable(!commandManager.canUndo());
        }
        if (redoButton != null) {
            redoButton.setDisable(!commandManager.canRedo());
        }
    }

    /**
     * Gets the command manager.
     *
     * @return the command manager
     */
    public CommandManager getCommandManager() {
        return commandManager;
    }

    /**
     * Handles undo operation.
     */
    public void handleUndo() {
        if (commandManager.canUndo()) {
            try {
                commandManager.undo();
                String desc = commandManager.getUndoDescription();
                if (uiStateManager != null) {
                    uiStateManager.updateStatus(lang().getString("status.undone", desc != null ? desc : ""));
                }
                logger.debug("Undo successful");
            } catch (Exception e) {
                logger.error("Error during undo", e);
                if (uiStateManager != null) {
                    uiStateManager.showError(lang().getString("error.title"), lang().getString("error.undo") + ": " + e.getMessage());
                }
            }
        } else {
            if (uiStateManager != null) {
                uiStateManager.updateStatus(lang().getString("status.nothingToUndo"));
            }
            logger.debug("Nothing to undo");
        }
    }

    /**
     * Handles redo operation.
     */
    public void handleRedo() {
        if (commandManager.canRedo()) {
            try {
                commandManager.redo();
                String desc = commandManager.getRedoDescription();
                if (uiStateManager != null) {
                    uiStateManager.updateStatus(lang().getString("status.redone", desc != null ? desc : ""));
                }
                logger.debug("Redo successful");
            } catch (Exception e) {
                logger.error("Error during redo", e);
                if (uiStateManager != null) {
                    uiStateManager.showError(lang().getString("error.title"), lang().getString("error.redo") + ": " + e.getMessage());
                }
            }
        } else {
            if (uiStateManager != null) {
                uiStateManager.updateStatus(lang().getString("status.nothingToRedo"));
            }
            logger.debug("Nothing to redo");
        }
    }

    /**
     * Clears all command history.
     */
    public void clear() {
        commandManager.clear();
        logger.info("Command history cleared");
    }
}
