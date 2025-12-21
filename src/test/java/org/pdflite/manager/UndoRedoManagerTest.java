package org.pdflite.manager;

import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pdflite.command.Command;
import org.pdflite.command.CommandManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UndoRedoManager using Mockito.
 */
@ExtendWith(MockitoExtension.class)
class UndoRedoManagerTest {

    @Mock
    private UIStateManager uiStateManager;

    @Mock
    private Command mockCommand;

    private UndoRedoManager undoRedoManager;

    @BeforeEach
    void setUp() {
        undoRedoManager = new UndoRedoManager(uiStateManager);
    }

    @Test
    void testInitialization() {
        assertNotNull(undoRedoManager.getCommandManager());
    }

    @Test
    void testHandleUndoWhenNothingToUndo() {
        undoRedoManager.handleUndo();
        
        verify(uiStateManager).updateStatus("Nothing to undo");
    }

    @Test
    void testHandleRedoWhenNothingToRedo() {
        undoRedoManager.handleRedo();
        
        verify(uiStateManager).updateStatus("Nothing to redo");
    }

    @Test
    void testHandleUndoAfterExecutingCommand() {
        when(mockCommand.getDescription()).thenReturn("Test Command");
        
        CommandManager commandManager = undoRedoManager.getCommandManager();
        commandManager.executeCommand(mockCommand);
        
        undoRedoManager.handleUndo();
        
        verify(mockCommand).undo();
        verify(uiStateManager).updateStatus(contains("Undone"));
    }

    @Test
    void testHandleRedoAfterUndo() {
        when(mockCommand.getDescription()).thenReturn("Test Command");
        
        CommandManager commandManager = undoRedoManager.getCommandManager();
        commandManager.executeCommand(mockCommand);
        commandManager.undo();
        
        undoRedoManager.handleRedo();
        
        verify(mockCommand, times(2)).execute(); // Once initially, once on redo
        verify(uiStateManager).updateStatus(contains("Redone"));
    }

    @Test
    void testClear() {
        when(mockCommand.getDescription()).thenReturn("Test Command");
        
        CommandManager commandManager = undoRedoManager.getCommandManager();
        commandManager.executeCommand(mockCommand);
        
        undoRedoManager.clear();
        
        assertFalse(commandManager.canUndo());
        assertFalse(commandManager.canRedo());
    }

    @Test
    void testHandleUndoWithException() {
        when(mockCommand.getDescription()).thenReturn("Test Command");
        doThrow(new RuntimeException("Undo failed")).when(mockCommand).undo();
        
        CommandManager commandManager = undoRedoManager.getCommandManager();
        commandManager.executeCommand(mockCommand);
        
        undoRedoManager.handleUndo();
        
        verify(uiStateManager).showError(eq("Undo Error"), anyString());
    }
}
