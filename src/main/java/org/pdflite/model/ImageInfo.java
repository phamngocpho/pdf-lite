package org.pdflite.model;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Data model for PDF images with coordinate information.
 *
 * <p><b>Coordinate System:</b></p>
 * <ul>
 *   <li>xPosition, yPosition: PDF coordinates (Y=bottom)</li>
 *   <li>Canvas coordinates calculated on-demand via converter</li>
 * </ul>
 *
 * @param xPosition PDF coordinates (Y=bottom)
 * @param yPosition PDF coordinates (Y=bottom)
 * @param width     Rendered width in PDF points
 * @param height    Rendered height in PDF points
 * @param image     Actual image data for clipboard
 * @see <a href="../../../docs/knowledge_2.md">Knowledge Base - Image Extraction</a>
 */
public record ImageInfo(int pageIndex, float xPosition, float yPosition, float width, float height,
                        BufferedImage image) {

    /**
     * Gets bounding box in PDF coordinates (Y=bottom).
     *
     * @return a rectangle representing the image bounds in PDF coordinates
     */
    public Rectangle2D.Float getPdfBounds() {
        return new Rectangle2D.Float(xPosition, yPosition, width, height);
    }

    /**
     * Gets the X position of the image in PDF coordinates.
     *
     * @return the X position in PDF points
     */
    public float getXPosition() {
        return xPosition;
    }

    /**
     * Gets the Y position of the image in PDF coordinates (Y=bottom).
     *
     * @return the Y position in PDF points
     */
    public float getYPosition() {
        return yPosition;
    }
}