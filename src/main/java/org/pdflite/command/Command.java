package org.pdflite.command;

import java.io.IOException;

/**
 * Command interface for implementing the Command Pattern.
 * <p>
 * All undoable/redoable actions must implement this interface.
 * Commands encapsulate all information needed to perform and undo an action.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 */
public interface Command {
    
    /**
     * Executes the command.
     * This method performs the action and saves any state needed for undo.
     * 
     * @throws IOException if the command execution fails
     */
    void execute() throws IOException;
    
    /**
     * Undoes the command.
     * This method reverses the action performed by execute().
     * 
     * @throws IOException if the undo operation fails
     */
    void undo() throws IOException;
    
    /**
     * Gets a human-readable description of this command.
     * This is used for UI feedback and logging.
     * 
     * @return description string (e.g., "Delete Page 5")
     */
    String getDescription();
    
    /**
     * Gets the type of this command.
     * Used for categorizing and filtering commands.
     * 
     * @return the command type
     */
    CommandType getType();
    
    /**
     * Checks if this command can be undone.
     * Some commands may not be undoable in certain states.
     * 
     * @return true if the command can be undone
     */
    default boolean canUndo() {
        return true;
    }
    
    /**
     * Gets the timestamp when this command was executed.
     * 
     * @return timestamp in milliseconds
     */
    long getTimestamp();
}