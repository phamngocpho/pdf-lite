package org.pdflite.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Manages command history for undo/redo operations.
 * Maintains two stacks: one for executed commands (history) and one for undone commands (redo).
 * Limits history to a maximum of 50 commands.
 */
public class CommandManager {
    private static final Logger logger = LoggerFactory.getLogger(CommandManager.class);
    private static final int MAX_HISTORY_SIZE = 50;
    
    private final Deque<Command> historyStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    private Consumer<Boolean> undoStateListener;
    private Consumer<Boolean> redoStateListener;
    
    /**
     * Executes a command and adds it to the history stack.
     * Clears the redo stack when a new command is executed.
     * 
     * @param command the command to execute
     */
    public void executeCommand(Command command) {
        if (command == null) {
            logger.warn("Attempted to execute null command");
            return;
        }
        
        try {
            command.execute();
            historyStack.push(command);
            
            // Limit history size
            if (historyStack.size() > MAX_HISTORY_SIZE) {
                historyStack.removeLast();
            }
            
            // Clear redo stack when new command is executed
            redoStack.clear();
            
            updateListeners();
            logger.debug("Executed command: {}", command.getDescription());
        } catch (Exception e) {
            logger.error("Error executing command: {}", command.getDescription(), e);
            throw new RuntimeException("Failed to execute command: " + e.getMessage(), e);
        }
    }
    
    /**
     * Undoes the most recent command.
     * 
     * @return true if undo was successful, false if history is empty
     */
    public boolean undo() {
        if (historyStack.isEmpty()) {
            logger.debug("Cannot undo: history stack is empty");
            return false;
        }
        
        try {
            Command command = historyStack.pop();
            command.undo();
            redoStack.push(command);
            
            updateListeners();
            logger.debug("Undone command: {}", command.getDescription());
            return true;
        } catch (Exception e) {
            logger.error("Error undoing command", e);
            // If undo fails, try to restore state by re-executing
            if (!historyStack.isEmpty()) {
                Command failedCommand = redoStack.pop();
                historyStack.push(failedCommand);
            }
            throw new RuntimeException("Failed to undo command: " + e.getMessage(), e);
        }
    }
    
    /**
     * Redoes the most recently undone command.
     * 
     * @return true if redo was successful, false if redo stack is empty
     */
    public boolean redo() {
        if (redoStack.isEmpty()) {
            logger.debug("Cannot redo: redo stack is empty");
            return false;
        }
        
        try {
            Command command = redoStack.pop();
            command.execute();
            historyStack.push(command);
            
            updateListeners();
            logger.debug("Redone command: {}", command.getDescription());
            return true;
        } catch (Exception e) {
            logger.error("Error redoing command", e);
            // If redo fails, keep it in redo stack
            if (!redoStack.isEmpty()) {
                Command failedCommand = historyStack.pop();
                redoStack.push(failedCommand);
            }
            throw new RuntimeException("Failed to redo command: " + e.getMessage(), e);
        }
    }
    
    /**
     * Checks if undo is available.
     * 
     * @return true if there are commands to undo
     */
    public boolean canUndo() {
        return !historyStack.isEmpty();
    }
    
    /**
     * Checks if redo is available.
     * 
     * @return true if there are commands to redo
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }
    
    /**
     * Gets the description of the next command to undo.
     * 
     * @return description of the next undo command, or null if none available
     */
    public String getUndoDescription() {
        return historyStack.isEmpty() ? null : historyStack.peek().getDescription();
    }
    
    /**
     * Gets the description of the next command to redo.
     * 
     * @return description of the next redo command, or null if none available
     */
    public String getRedoDescription() {
        return redoStack.isEmpty() ? null : redoStack.peek().getDescription();
    }
    
    /**
     * Clears all command history.
     */
    public void clear() {
        historyStack.clear();
        redoStack.clear();
        updateListeners();
        logger.debug("Command history cleared");
    }
    
    /**
     * Sets a listener for undo state changes.
     * 
     * @param listener consumer that receives true when undo is available, false otherwise
     */
    public void setUndoStateListener(Consumer<Boolean> listener) {
        this.undoStateListener = listener;
        if (listener != null) {
            listener.accept(canUndo());
        }
    }
    
    /**
     * Sets a listener for redo state changes.
     * 
     * @param listener consumer that receives true when redo is available, false otherwise
     */
    public void setRedoStateListener(Consumer<Boolean> listener) {
        this.redoStateListener = listener;
        if (listener != null) {
            listener.accept(canRedo());
        }
    }
    
    /**
     * Updates all registered listeners with current state.
     */
    private void updateListeners() {
        if (undoStateListener != null) {
            undoStateListener.accept(canUndo());
        }
        if (redoStateListener != null) {
            redoStateListener.accept(canRedo());
        }
    }
    
    /**
     * Gets the current size of the history stack.
     * 
     * @return number of commands in history
     */
    public int getHistorySize() {
        return historyStack.size();
    }
    
    /**
     * Gets the current size of the redo stack.
     * 
     * @return number of commands available for redo
     */
    public int getRedoSize() {
        return redoStack.size();
    }
}
