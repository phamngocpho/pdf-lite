package org.pdflite.command;

/**
 * Interface for commands that can be executed and undone.
 * This is the core of the Command Pattern implementation for undo/redo functionality.
 */
public interface Command {
    /**
     * Executes the command.
     */
    void execute();
    
    /**
     * Undoes the command, reverting to the previous state.
     */
    void undo();
    
    /**
     * Gets a description of the command for display purposes.
     * 
     * @return a human-readable description of the command
     */
    String getDescription();
}
