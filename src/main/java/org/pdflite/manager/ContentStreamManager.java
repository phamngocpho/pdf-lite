package org.pdflite.manager;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manager for low-level PDF content stream manipulation.
 * <p>
 * This manager provides functionality to:
 * <ul>
 *   <li>Parse and modify PDF content streams</li>
 *   <li>Remove text operators from specific regions</li>
 *   <li>Add text to content streams at specific positions</li>
 *   <li>Validate content stream integrity</li>
 * </ul>
 * </p>
 * <p>
 * WARNING: Content stream manipulation is complex and can corrupt PDFs if not done carefully.
 * Always validate the content stream after modifications.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class ContentStreamManager {
    private static final Logger logger = LoggerFactory.getLogger(ContentStreamManager.class);

    /**
     * Removes text operators from a specific region of a page's content stream.
     * <p>
     * This is a complex operation that requires parsing the content stream,
     * identifying text operators within the specified region, and removing them.
     * </p>
     * <p>
     * WARNING: This operation is not yet fully implemented and may corrupt the PDF.
     * Use with caution and always backup the original file.
     * </p>
     *
     * @param page   the PDF page
     * @param region the rectangular region to remove text from (PDF coordinates)
     * @throws IOException if content stream manipulation fails
     */
    public void removeTextOperators(PDPage page, javafx.geometry.Rectangle2D region) throws IOException {
        if (page == null || region == null) {
            throw new IllegalArgumentException("Page and region cannot be null");
        }

        logger.warn("removeTextOperators is not fully implemented - this is a placeholder");
        logger.info("Would remove text from region: ({}, {}) {}x{}",
                region.getMinX(), region.getMinY(), region.getWidth(), region.getHeight());

        // This is a placeholder for the actual implementation
        // Actual implementation would:
        // 1. Parse the content stream using PDFStreamParser
        // 2. Identify text operators (Tj, TJ, ', ", etc.) within the region
        // 3. Remove those operators from the stream
        // 4. Rebuild the content stream without the removed operators
        
        // For now, we just log a warning
        throw new UnsupportedOperationException(
                "Text removal from content stream is not yet implemented. " +
                        "This requires complex content stream parsing and manipulation.");
    }

    /**
     * Adds text to a page's content stream at the specified position.
     * <p>
     * This method appends text to the existing content stream using the
     * specified font and font size. The text is added at the given coordinates
     * in PDF coordinate space.
     * </p>
     *
     * @param document the PDF document
     * @param page     the PDF page
     * @param text     the text to add
     * @param x        the X coordinate (PDF space)
     * @param y        the Y coordinate (PDF space)
     * @param font     the font to use
     * @param fontSize the font size in points
     * @throws IOException if content stream manipulation fails
     */
    public void addText(org.apache.pdfbox.pdmodel.PDDocument document, PDPage page, String text, double x, double y, PDFont font, float fontSize) throws IOException {
        if (document == null || page == null || text == null || font == null) {
            throw new IllegalArgumentException("Document, page, text, and font cannot be null");
        }
        if (fontSize <= 0) {
            throw new IllegalArgumentException("Font size must be positive");
        }

        logger.info("Adding text '{}' at ({}, {}) with font size {}", text, x, y, fontSize);

        // Use APPEND mode to add to existing content
        try (PDPageContentStream contentStream = new PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true)) {

            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset((float) x, (float) y);
            contentStream.showText(text);
            contentStream.endText();

            logger.debug("Successfully added text to content stream");
        }
    }

    /**
     * Adds text to a page's content stream at the specified position.
     * <p>
     * Deprecated: Use addText(PDDocument, PDPage, ...) instead.
     * </p>
     *
     * @param page     the PDF page
     * @param text     the text to add
     * @param x        the X coordinate (PDF space)
     * @param y        the Y coordinate (PDF space)
     * @param font     the font to use
     * @param fontSize the font size in points
     * @throws IOException if content stream manipulation fails
     * @deprecated Use {@link #addText(org.apache.pdfbox.pdmodel.PDDocument, PDPage, String, double, double, PDFont, float)} instead
     */
    @Deprecated
    public void addText(PDPage page, String text, double x, double y, PDFont font, float fontSize) throws IOException {
        if (page == null || text == null || font == null) {
            throw new IllegalArgumentException("Page, text, and font cannot be null");
        }
        if (fontSize <= 0) {
            throw new IllegalArgumentException("Font size must be positive");
        }

        logger.info("Adding text '{}' at ({}, {}) with font size {}", text, x, y, fontSize);

        // Note: We need the PDDocument to create a content stream
        // This method should be called with a page that belongs to an open document
        // The document reference is obtained from the page's document catalog
        
        // Create content stream in APPEND mode to add to existing content
        // PDFBox will automatically get the document from the page
        org.apache.pdfbox.pdmodel.PDDocument doc = null;
        
        // Try to get document from page (PDFBox 3.x way)
        try {
            // In PDFBox 3.x, we need to pass the document explicitly
            // For now, throw an exception indicating we need the document
            throw new IllegalStateException(
                "addText requires the PDDocument to be passed. " +
                "Use addText(PDDocument, PDPage, String, double, double, PDFont, float) instead.");
        } catch (IllegalStateException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Adds text using the default Helvetica font.
     *
     * @param document the PDF document
     * @param page     the PDF page
     * @param text     the text to add
     * @param x        the X coordinate (PDF space)
     * @param y        the Y coordinate (PDF space)
     * @param fontSize the font size in points
     * @throws IOException if content stream manipulation fails
     */
    public void addText(org.apache.pdfbox.pdmodel.PDDocument document, PDPage page, String text, double x, double y, float fontSize) throws IOException {
        PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        addText(document, page, text, x, y, font, fontSize);
    }

    /**
     * Draws a filled white rectangle to cover existing content.
     * This is used to "erase" text before adding new text.
     *
     * @param document the PDF document
     * @param page     the PDF page
     * @param x        the X coordinate (PDF space, bottom-left corner)
     * @param y        the Y coordinate (PDF space, bottom-left corner)
     * @param width    the width of the rectangle
     * @param height   the height of the rectangle
     * @throws IOException if content stream manipulation fails
     */
    public void drawWhiteRectangle(org.apache.pdfbox.pdmodel.PDDocument document, PDPage page, 
                                   float x, float y, float width, float height) throws IOException {
        if (document == null || page == null) {
            throw new IllegalArgumentException("Document and page cannot be null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }

        logger.info("Drawing white rectangle at ({}, {}) with size {}x{}", x, y, width, height);

        // Use APPEND mode to add to existing content
        try (PDPageContentStream contentStream = new PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true)) {

            // Set fill color to white
            contentStream.setNonStrokingColor(1.0f, 1.0f, 1.0f);
            
            // Draw filled rectangle
            contentStream.addRect(x, y, width, height);
            contentStream.fill();

            logger.debug("Successfully drew white rectangle");
        }
    }

    /**
     * Replaces text by covering the old text with a white rectangle and adding new text.
     * This is a workaround for PDF's limitation of not being able to truly delete content.
     *
     * @param document the PDF document
     * @param page     the PDF page
     * @param x        the X coordinate (PDF space, bottom-left corner)
     * @param y        the Y coordinate (PDF space, bottom-left corner)
     * @param width    the width of the area to cover
     * @param height   the height of the area to cover
     * @param newText  the new text to add
     * @param textX    the X coordinate for the new text
     * @param textY    the Y coordinate for the new text (baseline)
     * @param font     the font to use
     * @param fontSize the font size in points
     * @throws IOException if content stream manipulation fails
     */
    public void replaceText(org.apache.pdfbox.pdmodel.PDDocument document, PDPage page,
                           float x, float y, float width, float height,
                           String newText, float textX, float textY,
                           PDFont font, float fontSize) throws IOException {
        // First, draw white rectangle to cover old text
        drawWhiteRectangle(document, page, x, y, width, height);
        
        // Then, add new text
        addText(document, page, newText, textX, textY, font, fontSize);
        
        logger.info("Replaced text: covered area ({}, {}) {}x{}, added '{}' at ({}, {})",
                x, y, width, height, newText, textX, textY);
    }

    /**
     * Modifies a page's content stream using a custom modifier function.
     * <p>
     * This method provides low-level access to the content stream for advanced
     * manipulation. The modifier function receives a PDFStreamEngine that can
     * be used to parse and modify the stream.
     * </p>
     * <p>
     * WARNING: This is an advanced operation that can easily corrupt the PDF.
     * Only use if you understand PDF content stream structure.
     * </p>
     *
     * @param page     the PDF page
     * @param modifier the function to modify the content stream
     * @throws IOException if content stream manipulation fails
     */
    public void modifyContentStream(PDPage page, Consumer<PDFStreamEngine> modifier) throws IOException {
        if (page == null || modifier == null) {
            throw new IllegalArgumentException("Page and modifier cannot be null");
        }

        logger.warn("modifyContentStream is an advanced operation - use with caution");

        // This is a placeholder for advanced content stream manipulation
        // Actual implementation would create a custom PDFStreamEngine
        // and allow the modifier to process operators
        
        throw new UnsupportedOperationException(
                "Advanced content stream modification is not yet implemented. " +
                        "This requires custom PDFStreamEngine implementation.");
    }

    /**
     * Validates the integrity of a page's content stream.
     * <p>
     * This method attempts to parse the content stream to ensure it's valid
     * and doesn't contain errors that would prevent rendering.
     * </p>
     *
     * @param page the PDF page to validate
     * @return true if the content stream is valid, false otherwise
     */
    public boolean validateContentStream(PDPage page) {
        if (page == null) {
            return false;
        }

        try {
            // Try to create a simple stream engine to parse the content
            PDFStreamEngine engine = new PDFStreamEngine() {
                @Override
                protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
                    // Just parse, don't process
                }
            };

            engine.processPage(page);
            logger.debug("Content stream validation passed");
            return true;

        } catch (IOException e) {
            logger.error("Content stream validation failed", e);
            return false;
        }
    }

    /**
     * Checks if a page has any text content.
     *
     * @param page the PDF page
     * @return true if the page contains text operators
     * @throws IOException if content stream parsing fails
     */
    public boolean hasTextContent(PDPage page) throws IOException {
        if (page == null) {
            return false;
        }

        // Use a simple stream engine to detect text operators
        final boolean[] hasText = {false};

        try {
            PDFStreamEngine engine = new PDFStreamEngine() {
                @Override
                protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
                    // Check for text showing operators
                    String op = operator.getName();
                    if ("Tj".equals(op) || "TJ".equals(op) || "'".equals(op) || "\"".equals(op)) {
                        hasText[0] = true;
                    }
                }
            };

            engine.processPage(page);

        } catch (IOException e) {
            logger.error("Error checking for text content", e);
            throw e;
        }

        return hasText[0];
    }

    /**
     * Gets the bounding box of all content on a page.
     * <p>
     * This can be useful for determining the actual content area vs. the page size.
     * </p>
     *
     * @param page the PDF page
     * @return the bounding rectangle of all content, or the page's media box if content bounds cannot be determined
     */
    public PDRectangle getContentBounds(PDPage page) {
        if (page == null) {
            return null;
        }

        // For now, just return the media box
        // A full implementation would parse the content stream and calculate actual bounds
        PDRectangle mediaBox = page.getMediaBox();
        logger.debug("Content bounds (using media box): {}x{}", mediaBox.getWidth(), mediaBox.getHeight());
        return mediaBox;
    }

    /**
     * Clears all content from a page's content stream.
     * <p>
     * WARNING: This removes ALL content from the page, including text, images, and graphics.
     * Use with extreme caution.
     * </p>
     *
     * @param page the PDF page to clear
     * @throws IOException if content stream manipulation fails
     */
    public void clearContentStream(PDPage page) throws IOException {
        if (page == null) {
            throw new IllegalArgumentException("Page cannot be null");
        }

        logger.warn("Clearing all content from page");

        // Set the content stream to empty (cast to PDStream)
        page.setContents((org.apache.pdfbox.pdmodel.common.PDStream) null);

        logger.debug("Page content cleared");
    }
}
