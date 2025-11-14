package org.pdflite.controller;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.pdflite.model.PDFDocument;
import org.pdflite.util.CoordinateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles context menu operations with CORRECT coordinate mapping.
 * 
 * <p><b>Implementation Notes:</b></p>
 * <ul>
 *   <li>Uses {@link CoordinateConverter} for proper canvas-to-PDF coordinate transformation</li>
 *   <li>Implements Knowledge Base Section III coordinate conversion pipeline</li>
 *   <li>Supports text extraction via {@link PDFTextStripperByArea} (Y=top coords)</li>
 *   <li>TODO: Image extraction via PDFStreamEngine (Y=bottom coords)</li>
 * </ul>
 * 
 * @see CoordinateConverter
 * @see <a href="../../../docs/knowledge.md">Knowledge Base</a>
 */
public class ContextMenuHandler {
    private static final Logger logger = LoggerFactory.getLogger(ContextMenuHandler.class);
    
    // Selection state
    private SelectionInfo currentSelection;
    
    public ContextMenuHandler() {
        logger.info("ContextMenuHandler initialized!");
    }
    
    /**
     * Analyzes rectangular selection area on PDF page.
     * 
     * <p><b>Coordinate Conversion Pipeline:</b></p>
     * <ol>
     *   <li>Input: Canvas coordinates (pixels, Y=top)</li>
     *   <li>Normalize: Create bounding box (x, y, width, height)</li>
     *   <li>Convert: Apply scale factor (zoom * 150/72)</li>
     *   <li>Extract: Pass to PDFTextStripperByArea (expects Y=top)</li>
     * </ol>
     * 
     * @param document The PDF document
     * @param pageIndex Zero-based page index
     * @param canvasX1 Canvas X coordinate of first corner (pixels)
     * @param canvasY1 Canvas Y coordinate of first corner (pixels)
     * @param canvasX2 Canvas X coordinate of opposite corner (pixels)
     * @param canvasY2 Canvas Y coordinate of opposite corner (pixels)
     * @param zoom Current zoom level (e.g., 1.0 = 100%)
     */
    public void analyzeSelection(PDFDocument document, int pageIndex,
                                double canvasX1, double canvasY1,
                                double canvasX2, double canvasY2,
                                double zoom) {
        
        if (document == null || document.getDocument() == null) {
            logger.warn("Analysis aborted: document is null");
            return;
        }
        
        try {
            PDPage page = document.getDocument().getPage(pageIndex);
            PDRectangle cropBox = page.getCropBox();
            float pageWidth = cropBox.getWidth();
            float pageHeight = cropBox.getHeight();
            
            // Normalize canvas coordinates (create bounding box)
            double x1 = Math.min(canvasX1, canvasX2);
            double y1 = Math.min(canvasY1, canvasY2);
            double width = Math.abs(canvasX2 - canvasX1);
            double height = Math.abs(canvasY2 - canvasY1);
            
            // Convert to PDF Java Points (Y=top)
            Rectangle2D.Float pdfRect = CoordinateConverter.canvasToPdfJavaRect(
                x1, y1, width, height, pageHeight, zoom
            );
            
            
            // Validate coordinates
            if (!CoordinateConverter.isValidPdfCoordinate(
                    pdfRect.x, pdfRect.y, pageWidth, pageHeight)) {
                logger.warn("Coordinates outside page bounds!");
            }
            
            // Extract text
            currentSelection = extractTextFromRegion(page, pdfRect, pageIndex);
            
            if (currentSelection.hasText()) {
                logger.info("Length:   {} characters",currentSelection.getText().length());
                
                String preview = currentSelection.getText().substring(0, 
                    Math.min(50, currentSelection.getText().length()));
            } else {
                logger.info("No text found in selection");
            }
            
        } catch (IOException e) {
            logger.error("Error analyzing selection: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Extracts text from a PDF Java rectangle (Y=top coordinates).
     * 
     * <p><b>PDFBox Requirements:</b></p>
     * <ul>
     *   <li>Input rectangle MUST use Java/AWT coordinates (Y=0 at top)</li>
     *   <li>Use {@code setSortByPosition(true)} for natural reading order</li>
     *   <li>Use {@code setShouldSeparateByBeads(false)} for area extraction</li>
     * </ul>
     * 
     * @param page The PDF page
     * @param rect Rectangle in PDF Java coordinates (Y=top)
     * @param pageIndex Page index for tracking
     * @return SelectionInfo containing extracted text
     * @throws IOException if extraction fails
     */
    private SelectionInfo extractTextFromRegion(PDPage page, Rectangle2D.Float rect, int pageIndex) 
            throws IOException {
        
        //logger.debug("Extracting text from region: ({:.2f},{:.2f})+{:.2f}x{:.2f}", rect.x, rect.y, rect.width, rect.height);
        
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        
        // Knowledge Base Section 1: Text Extraction Configuration
        stripper.setSortByPosition(true);
        stripper.setShouldSeparateByBeads(false);
        
        // Add region with Java coordinates (Y=top)
        String regionName = "selection";
        stripper.addRegion(regionName, rect);
        stripper.extractRegions(page);
        
        String text = stripper.getTextForRegion(regionName);
        String cleanedText = (text != null) ? text.trim() : "";
        
        logger.debug("Extracted {} characters from region", cleanedText.length());
        
        return new SelectionInfo(
            pageIndex,
            rect,
            cleanedText,
            new ArrayList<>() // TODO: Extract images
        );
    }
    
    /**
     * Copies extracted text to system clipboard.
     */
    public void handleCopyText() {
        if (currentSelection != null && currentSelection.hasText()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(currentSelection.getText());
            Clipboard.getSystemClipboard().setContent(content);
            
            logger.info("Copied {} characters to clipboard", 
                currentSelection.getText().length());
        } else {
            logger.warn("No text to copy");
        }
    }
    
    /**
     * Checks if text is available at last analyzed position.
     * @return 
     */
    public boolean hasTextAtPosition() {
        return currentSelection != null && currentSelection.hasText();
    }
    
    /**
     * Gets current selection info (for debugging).
     * @return 
     */
    public SelectionInfo getCurrentSelection() {
        return currentSelection;
    }
    
    /**
     * Inner class to hold selection information.
     */
    public static class SelectionInfo {
        private final int pageIndex;
        private final Rectangle2D.Float pdfRect;
        private final String text;
        private final List<Object> images; // TODO: Implement image extraction
        
        public SelectionInfo(int pageIndex, Rectangle2D.Float pdfRect, 
                           String text, List<Object> images) {
            this.pageIndex = pageIndex;
            this.pdfRect = pdfRect;
            this.text = text;
            this.images = images;
        }
        
        public boolean hasText() {
            return text != null && !text.isEmpty();
        }
        
        public String getText() {
            return text;
        }
        
        public int getPageIndex() {
            return pageIndex;
        }
        
        public Rectangle2D.Float getPdfRect() {
            return pdfRect;
        }
    }
}