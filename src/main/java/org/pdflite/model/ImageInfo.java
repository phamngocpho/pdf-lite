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
 * @see <a href="../../../docs/knowledge_2.md">Knowledge Base - Image Extraction</a>
 */
public class ImageInfo {
    private final int pageIndex;
    private final float xPosition;      // PDF coordinates (Y=bottom)
    private final float yPosition;      // PDF coordinates (Y=bottom)
    private final float width;          // Rendered width in PDF points
    private final float height;         // Rendered height in PDF points
    private final BufferedImage image;  // Actual image data for clipboard
    
    public ImageInfo(int pageIndex, float xPosition, float yPosition,
                    float width, float height, BufferedImage image) {
        this.pageIndex = pageIndex;
        this.xPosition = xPosition;
        this.yPosition = yPosition;
        this.width = width;
        this.height = height;
        this.image = image;
    }
    
    /**
     * Gets bounding box in PDF coordinates (Y=bottom).
     */
    public Rectangle2D.Float getPdfBounds() {
        return new Rectangle2D.Float(xPosition, yPosition, width, height);
    }
    
    // Getters
    public int getPageIndex() { return pageIndex; }
    public float getXPosition() { return xPosition; }
    public float getYPosition() { return yPosition; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public BufferedImage getImage() { return image; }
}