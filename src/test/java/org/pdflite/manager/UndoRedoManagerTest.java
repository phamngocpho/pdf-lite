package org.pdflite.manager;

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
 * Tests business logic without JavaFX UI components.
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
    void testInitialState() {
        assertNotNull(undoRedoManager.getCommandManager());
    }

    @Test
    void testHandleUndoWithNoHistory() {
        undoRedoManager.handleUndo();
        
        verify(uiStateManager).updateStatus("Nothing to undo");
    }

    @Test
    void testHandleRedoWithNoHistory() {
        undoRedoManager.handleRedo();
        
        verify(uiStateManager).updateStatus("Nothing to redo");
    }

    @Test
    void testHandleUndoWithCommand() {
        when(mockCommand.getDescription()).thenReturn("Test Command");
        
        CommandManager commandManager = undoRedoManager.getCommandManager();
        commandManager.executeCommand(mockCommand);
        
        undoRedoManager.handleUndo();
        
        verify(mockCommand).undo();
        verify(uiStateManager).updateStatus(contains("Undone"));
    }

    @Test
    void testHandleRedoWithCommand() {
        when(mockCommand.getDescription()).thenReturn("Test Command");
        
        CommandManager commandManager = undoRedoManager.getCommandManager();
        commandManager.executeCommand(mockCommand);
        commandManager.undo();
        
        undoRedoManager.handleRedo();
        
        verify(mockCommand, times(2)).execute();
        verify(uiStateManager).updateStatus(contains("Redone"));
    }

    @Test
    void testClear() {
        CommandManager commandManager = undoRedoManager.getCommandManager();
        commandManager.executeCommand(mockCommand);
        
        undoRedoManager.clear();
        
        assertFalse(commandManager.canUndo());
        assertFalse(commandManager.canRedo());
    }

    @Test
    void testGetCommandManager() {
        CommandManager commandManager = undoRedoManager.getCommandManager();
        
        assertNotNull(commandManager);
    }
}
