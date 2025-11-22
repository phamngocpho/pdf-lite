package org.pdflite.command;

/**
 * Enum representing different types of undoable commands.
 * 
 * @author PDF Lite Team
 * @version 1.0.0
 */
public enum CommandType {
    DELETE_PAGE("Delete Page"),
    HIGHLIGHT("Highlight Text"),
    DRAW_SHAPE("Draw Shape"),
    ADD_NOTE("Add Note"),
    ROTATE_PAGE("Rotate Page"),
    MERGE_PAGES("Merge Pages"),
    SPLIT_PAGE("Split Page"),
    OTHER("Other");

    private final String displayName;

    CommandType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}