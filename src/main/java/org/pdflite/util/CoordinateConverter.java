package org.pdflite.util;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.pdflite.util.Constants.LOW_RENDER_SCALE;

/**
 * Utility for converting coordinates between Canvas/GUI and PDF coordinate
 * systems.
 *
 * <p>
 * <b>Coordinate Systems:</b></p>
 * <ul>
 * <li><b>Canvas/GUI:</b> Origin (0,0) at TOP-LEFT, Y increases downward, units
 * in PIXELS</li>
 * <li><b>PDF User Space:</b> Origin (0,0) at BOTTOM-LEFT, Y increases upward,
 * units in POINTS (1/72 inch)</li>
 * <li><b>PDF Java/AWT:</b> Origin (0,0) at TOP-LEFT, Y increases downward,
 * units in POINTS</li>
 * </ul>
 *
 * <p>
 * <b>Knowledge Base Reference:</b></p>
 * <pre>
 * Section III - Coordinate Conversion:
 * Y_pdf_java_points = (H_pdf - (Y_canvas / scale))
 * where scale = zoom * LOW_RENDER_SCALE (zoom * 150/72)
 * </pre>
 *
 * @see <a href="../../../docs/knowledge.md">Knowledge Base - Section III</a>
 */
public class CoordinateConverter {

    private static final Logger logger = LoggerFactory.getLogger(CoordinateConverter.class);

    /**
     * Converts canvas/GUI rectangle to PDF Java/AWT rectangle (Y=top).
     *
     * <p>
     * <b>Algorithm:</b></p>
     * <ol>
     * <li>Calculate final scale: {@code scale = zoom * LOW_RENDER_SCALE}</li>
     * <li>Convert X: {@code pdfX = canvasX / scale}</li>
     * <li>Convert Y: {@code pdfY = canvasY / scale} (already Y=top)</li>
     * <li>Convert dimensions:
     * {@code pdfWidth/Height = canvasWidth/Height / scale}</li>
     * </ol>
     *
     * @param canvasX X coordinate on canvas (pixels, origin at top-left)
     * @param canvasY Y coordinate on canvas (pixels, origin at top-left)
     * @param canvasWidth Width of rectangle (pixels)
     * @param canvasHeight Height of rectangle (pixels)
     * @param pageHeight PDF page height in points (unused for Java coords, kept
     * for API compatibility)
     * @param zoom Current zoom level (e.g., 1.0, 1.5, 2.0)
     * @return Rectangle in PDF Java/AWT coordinate system (Y=top, units in
     * points)
     */
    public static Rectangle2D.Float canvasToPdfJavaRect(
            double canvasX, double canvasY,
            double canvasWidth, double canvasHeight,
            float pageHeight, double zoom) {

        // Calculate scale factor (150 DPI / 72 DPI)
        double finalScale = zoom * LOW_RENDER_SCALE;

        float pdfX = (float) (canvasX / finalScale);
        float pdfY = (float) (canvasY / finalScale);
        float pdfWidth = (float) (canvasWidth / finalScale);
        float pdfHeight = (float) (canvasHeight / finalScale);

        // (PDFTextStripperByArea expects Y=top coordinates)
        /**
         * logger.trace("Converted canvas->pdfJava:
         * ({:.2f},{:.2f})+{:.2f}x{:.2f}px @ scale={:.4f} ->
         * ({:.2f},{:.2f})+{:.2f}x{:.2f}pt", canvasX, canvasY, canvasWidth,
         * canvasHeight, finalScale, pdfX, pdfY, pdfWidth, pdfHeight);
         */
        return new Rectangle2D.Float(pdfX, pdfY, pdfWidth, pdfHeight);
    }

    /**
     * Converts single canvas point to PDF Java/AWT point (Y=top).
     *
     * @param canvasX X coordinate on canvas (pixels)
     * @param canvasY Y coordinate on canvas (pixels)
     * @param pageHeight PDF page height (unused, kept for API compatibility)
     * @param zoom Current zoom level
     * @return Point in PDF Java/AWT coordinate system (Y=top, units in points)
     */
    public static Point2D.Float canvasToPdfJavaPoint(
            double canvasX, double canvasY,
            float pageHeight, double zoom) {

        double finalScale = zoom * LOW_RENDER_SCALE;

        float pdfX = (float) (canvasX / finalScale);
        float pdfY = (float) (canvasY / finalScale);

        /*
        logger.trace("Converted canvas->pdfJava point: ({:.2f},{:.2f})px @ scale={:.4f} -> ({:.2f},{:.2f})pt",
                canvasX, canvasY, finalScale, pdfX, pdfY);
         */
        return new Point2D.Float(pdfX, pdfY);
    }

    /**
     * @param pdfX X in PDF points
     * @param pdfY Y in PDF points (bottom-origin)
     * @param pageHeight Page height in PDF points
     * @param zoom Current zoom level
     * @return Point in canvas coordinates (pixels, Y=top)
     */
    public static Point2D.Float pdfToCanvasPoint(
            float pdfX, float pdfY,
            float pageHeight, double zoom) {

        double finalScale = zoom * LOW_RENDER_SCALE;

        // Y-axis inversion: PDF Y=bottom → Canvas Y=top
        float canvasX = (float) (pdfX * finalScale);
        float canvasY = (float) ((pageHeight - pdfY) * finalScale);

        /*
        logger.trace("Converted PDF->Canvas: ({:.2f},{:.2f}) @ page_h={:.2f} → ({:.2f},{:.2f})px",
                pdfX, pdfY, pageHeight, canvasX, canvasY);
         */
        return new Point2D.Float(canvasX, canvasY);
    }

    /**
     *
     * @param pdfX
     * @param pdfY
     * @param pdfWidth
     * @param pdfHeight
     * @param pageHeight
     * @param zoom
     * @return
     */
    public static Rectangle2D.Float pdfToCanvasRect(
            float pdfX, float pdfY, float pdfWidth, float pdfHeight,
            float pageHeight, double zoom) {

        double finalScale = zoom * LOW_RENDER_SCALE;

        // Convert coordinates
        float canvasX = (float) (pdfX * finalScale);
        // Y-axis inversion for TOP-LEFT corner of image
        float canvasY = (float) ((pageHeight - pdfY - pdfHeight) * finalScale);
        float canvasWidth = (float) (pdfWidth * finalScale);
        float canvasHeight = (float) (pdfHeight * finalScale);

        /*
        logger.trace("PDF rect ({:.2f},{:.2f})+{:.2f}x{:.2f} → Canvas ({:.2f},{:.2f})+{:.2f}x{:.2f}",
                pdfX, pdfY, pdfWidth, pdfHeight,
                canvasX, canvasY, canvasWidth, canvasHeight);
         */
        return new Rectangle2D.Float(canvasX, canvasY, canvasWidth, canvasHeight);
    }

    /**
     * Validates if PDF coordinates are within page bounds.
     *
     * @param pdfX X coordinate in PDF points
     * @param pdfY Y coordinate in PDF points
     * @param pageWidth Page width in points
     * @param pageHeight Page height in points
     * @return true if coordinates are valid
     */
    public static boolean isValidPdfCoordinate(float pdfX, float pdfY,
            float pageWidth, float pageHeight) {
        boolean valid = pdfX >= 0 && pdfX <= pageWidth
                && pdfY >= 0 && pdfY <= pageHeight;

        if (!valid) {
            //logger.warn("Invalid PDF coordinates: ({:.2f},{:.2f}) outside bounds (0,0)-({:.2f},{:.2f})",pdfX, pdfY, pageWidth, pageHeight);
        }

        return valid;
    }
}
