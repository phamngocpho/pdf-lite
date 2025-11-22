package org.pdflite.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Manages the history of commands for undo/redo functionality.
 * <p>
 * This class maintains two stacks: one for undo and one for redo.
 * It supports a maximum history size to limit memory usage.
 * </p>
 * 
 * @author PDF Lite Team
 * @version 1.0.0
 */
public class CommandManager {
    private static final Logger logger = LoggerFactory.getLogger(CommandManager.class);

    private static final int MAX_HISTORY_SIZE = 50;

    private final Stack<Command> undoStack;
    private final Stack<Command> redoStack;
    private final List<CommandHistoryListener> listeners;

    /**
     * Creates a new CommandManager.
     */
    public CommandManager() {
        this.undoStack = new Stack<>();
        this.redoStack = new Stack<>();
        this.listeners = new ArrayList<>();
        logger.info("CommandManager initialized with max history size: {}", MAX_HISTORY_SIZE);
    }

    /**
     * Executes a command and adds it to the undo stack.
     * This clears the redo stack.
     * 
     * @param command        the command to execute
     * @param reloadCallback callback to reload document after execution (can be
     *                       null)
     * @throws IOException if command execution fails
     */
    public void executeCommand(Command command, ReloadCallback reloadCallback) throws IOException {
        logger.info("Executing command: {}", command.getDescription());

        // Execute the command
        command.execute();

        // Reload document if callback provided
        if (reloadCallback != null) {
            reloadCallback.reload();
        }

        // Add to undo stack
        undoStack.push(command);

        // Clear redo stack (once a new command is executed, redo history is lost)
        clearRedoStack();

        // Enforce max history size
        enforceHistoryLimit();

        // Notify listeners
        notifyListeners();

        logger.info("Command executed. Undo stack size: {}", undoStack.size());
    }

    /**
     * Callback interface for reloading document after command execution.
     */
    @FunctionalInterface
    public interface ReloadCallback {
        void reload() throws IOException;
    }

    /**
     * Undoes the last command.
     * 
     * @param reloadCallback callback to reload document after undo (can be null)
     * @return true if undo was successful, false if undo stack is empty
     * @throws IOException if undo operation fails
     */
    public boolean undo(ReloadCallback reloadCallback) throws IOException {
        if (undoStack.isEmpty()) {
            logger.debug("Cannot undo: undo stack is empty");
            return false;
        }

        Command command = undoStack.pop();
        logger.info("Undoing command: {}", command.getDescription());

        // Perform undo
        command.undo();

        // Reload document if callback provided
        if (reloadCallback != null) {
            reloadCallback.reload();
        }

        // Add to redo stack
        redoStack.push(command);

        // Notify listeners
        notifyListeners();

        logger.info("Undo completed. Undo stack size: {}, Redo stack size: {}",
                undoStack.size(), redoStack.size());

        return true;
    }

    /**
     * Redoes the last undone command.
     * 
     * @param reloadCallback callback to reload document after redo (can be null)
     * @return true if redo was successful, false if redo stack is empty
     * @throws IOException if redo operation fails
     */
    public boolean redo(ReloadCallback reloadCallback) throws IOException {
        if (redoStack.isEmpty()) {
            logger.debug("Cannot redo: redo stack is empty");
            return false;
        }

        Command command = redoStack.pop();
        logger.info("Redoing command: {}", command.getDescription());

        // Perform redo (execute again)
        command.execute();

        // Reload document if callback provided
        if (reloadCallback != null) {
            reloadCallback.reload();
        }

        // Add back to undo stack
        undoStack.push(command);

        // Notify listeners
        notifyListeners();

        logger.info("Redo completed. Undo stack size: {}, Redo stack size: {}",
                undoStack.size(), redoStack.size());

        return true;
    }

    /**
     * Checks if undo is available.
     * 
     * @return true if there are commands in the undo stack
     */
    public boolean canUndo() {
        return !undoStack.isEmpty() && (undoStack.peek().canUndo());
    }

    /**
     * Checks if redo is available.
     * 
     * @return true if there are commands in the redo stack
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Gets the description of the next command to be undone.
     * 
     * @return description string, or null if undo stack is empty
     */
    public String getUndoDescription() {
        return undoStack.isEmpty() ? null : undoStack.peek().getDescription();
    }

    /**
     * Gets the description of the next command to be redone.
     * 
     * @return description string, or null if redo stack is empty
     */
    public String getRedoDescription() {
        return redoStack.isEmpty() ? null : redoStack.peek().getDescription();
    }

    /**
     * Clears all command history.
     */
    public void clear() {
        logger.info("Clearing all command history");

        // Cleanup commands that need it (e.g., delete backup files)
        cleanupCommands(undoStack);
        cleanupCommands(redoStack);

        undoStack.clear();
        redoStack.clear();

        notifyListeners();
    }

    /**
     * Gets the current undo stack size.
     * 
     * @return number of commands in undo stack
     */
    public int getUndoStackSize() {
        return undoStack.size();
    }

    /**
     * Gets the current redo stack size.
     * 
     * @return number of commands in redo stack
     */
    public int getRedoStackSize() {
        return redoStack.size();
    }

    /**
     * Adds a listener to be notified of command history changes.
     * 
     * @param listener the listener to add
     */
    public void addListener(CommandHistoryListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener.
     * 
     * @param listener the listener to remove
     */
    public void removeListener(CommandHistoryListener listener) {
        listeners.remove(listener);
    }

    /**
     * Enforces the maximum history size limit.
     * Removes oldest commands if limit is exceeded.
     */
    private void enforceHistoryLimit() {
        while (undoStack.size() > MAX_HISTORY_SIZE) {
            Command oldCommand = undoStack.remove(0);
            cleanupCommand(oldCommand);
            logger.debug("Removed old command from history: {}", oldCommand.getDescription());
        }
    }

    /**
     * Clears the redo stack and cleans up resources.
     */
    private void clearRedoStack() {
        cleanupCommands(redoStack);
        redoStack.clear();
    }

    /**
     * Cleans up resources for commands that need it.
     */
    private void cleanupCommands(Stack<Command> stack) {
        for (Command cmd : stack) {
            cleanupCommand(cmd);
        }
    }

    /**
     * Cleans up a single command if it implements cleanup.
     */
    private void cleanupCommand(Command command) {
        if (command instanceof DeletePageCommand) {
            ((DeletePageCommand) command).cleanup();
        }
        // Add other command cleanups as needed
    }

    /**
     * Notifies all listeners of a change in command history.
     */
    private void notifyListeners() {
        for (CommandHistoryListener listener : listeners) {
            listener.onHistoryChanged(canUndo(), canRedo(), getUndoDescription(), getRedoDescription());
        }
    }

    /**
     * Interface for listening to command history changes.
     */
    public interface CommandHistoryListener {
        /**
         * Called when the command history changes.
         * 
         * @param canUndo         true if undo is available
         * @param canRedo         true if redo is available
         * @param undoDescription description of next undo action
         * @param redoDescription description of next redo action
         */
        void onHistoryChanged(boolean canUndo, boolean canRedo,
                String undoDescription, String redoDescription);
    }
}