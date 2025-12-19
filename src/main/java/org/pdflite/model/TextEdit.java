package org.pdflite.model;

/**
 * Record representing a text editing operation on a PDF document.
 * <p>
 * This record captures all information about a text edit, including the location
 * (text region), the original text, and the new text. It implements the
 * {@link EditOperation} interface as part of the edit tracking system.
 * </p>
 *
 * @param pageIndex   the zero-based page index where the edit occurred
 * @param timestamp   the timestamp when the edit was performed (milliseconds since epoch)
 * @param description a human-readable description of the edit
 * @param region      the text region that was edited
 * @param oldText     the original text before editing
 * @param newText     the new text after editing
 * @author PDF Lite Team
 * @version 1.0.0
 * @see EditOperation
 * @see TextRegion
 * @since 1.0.0
 */
public record TextEdit(
        int pageIndex,
        long timestamp,
        String description,
        TextRegion region,
        String oldText,
        String newText
) implements EditOperation {
    
    /**
     * Creates a new TextEdit with the current timestamp.
     *
     * @param pageIndex   the zero-based page index
     * @param region      the text region that was edited
     * @param oldText     the original text
     * @param newText     the new text
     * @return a new TextEdit instance
     */
    public static TextEdit create(int pageIndex, TextRegion region, String oldText, String newText) {
        long timestamp = System.currentTimeMillis();
        String description = String.format("Text edit on page %d: '%s' -> '%s'",
                pageIndex + 1, oldText, newText);
        return new TextEdit(pageIndex, timestamp, description, region, oldText, newText);
    }
}
