package org.pdflite.model;

/**
 * Sealed interface representing an edit operation performed on a PDF document.
 * <p>
 * This interface is part of the edit tracking system that records all modifications
 * made to a PDF document. Each implementation represents a specific type of edit
 * operation (text edit, image insert, page delete, annotation edit).
 * </p>
 * <p>
 * Being a sealed interface, only the explicitly permitted implementations can
 * implement this interface, ensuring type safety and exhaustive pattern matching.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @see TextEdit
 * @see ImageInsert
 * @since 1.0.0
 */
public sealed interface EditOperation permits TextEdit, ImageInsert {

    /**
     * Gets the zero-based page index where this edit operation was performed.
     *
     * @return the page index
     */
    int pageIndex();

    /**
     * Gets the timestamp when this edit operation was performed.
     *
     * @return the timestamp in milliseconds since epoch
     */
    long timestamp();

    /**
     * Gets a human-readable description of this edit operation.
     *
     * @return the description string
     */
    String description();
}
