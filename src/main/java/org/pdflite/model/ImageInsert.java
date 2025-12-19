package org.pdflite.model;

/**
 * Record representing an image insertion operation on a PDF document.
 * <p>
 * This record captures all information about an image insertion, including the
 * placement details (position, size, source file). It implements the
 * {@link EditOperation} interface as part of the edit tracking system.
 * </p>
 *
 * @param pageIndex   the zero-based page index where the image was inserted
 * @param timestamp   the timestamp when the insertion was performed (milliseconds since epoch)
 * @param description a human-readable description of the insertion
 * @param placement   the image placement details (position, size, file)
 * @author PDF Lite Team
 * @version 1.0.0
 * @see EditOperation
 * @see ImagePlacement
 * @since 1.0.0
 */
public record ImageInsert(
        int pageIndex,
        long timestamp,
        String description,
        ImagePlacement placement
) implements EditOperation {
    
    /**
     * Creates a new ImageInsert with the current timestamp.
     *
     * @param pageIndex the zero-based page index
     * @param placement the image placement details
     * @return a new ImageInsert instance
     */
    public static ImageInsert create(int pageIndex, ImagePlacement placement) {
        long timestamp = System.currentTimeMillis();
        String description = String.format("Image inserted on page %d: %s",
                pageIndex + 1, placement.imageFile().getName());
        return new ImageInsert(pageIndex, timestamp, description, placement);
    }
}
