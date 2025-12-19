package org.pdflite.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.pdflite.model.PDFDocument;
import org.pdflite.model.WatermarkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for adding watermarks to PDF documents.
 * Supports both text and image watermarks with various positioning and styling options.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class WatermarkService {
    private static final Logger logger = LoggerFactory.getLogger(WatermarkService.class);
    
    /**
     * Applies watermark to the PDF document based on the configuration.
     *
     * @param pdfDoc the PDF document to add watermark to
     * @param config the watermark configuration
     * @throws IOException if an error occurs during watermark application
     */
    public void applyWatermark(PDFDocument pdfDoc, WatermarkConfig config) throws IOException {
        if (pdfDoc == null || config == null) {
            throw new IllegalArgumentException("PDF document and config cannot be null");
        }
        
        PDDocument document = pdfDoc.getDocument();
        List<Integer> targetPages = determineTargetPages(document, config);
        
        logger.info("Applying {} watermark to {} page(s)", config.getType(), targetPages.size());
        
        for (int pageIndex : targetPages) {
            PDPage page = document.getPage(pageIndex);
            
            if (config.getType() == WatermarkConfig.WatermarkType.TEXT) {
                applyTextWatermark(document, page, config);
            } else {
                applyImageWatermark(document, page, config);
            }
        }
        
        // Clear cache to force re-render
        pdfDoc.clearCache();
        
        logger.info("Watermark applied successfully");
    }
    
    /**
     * Applies text watermark to a page.
     */
    private void applyTextWatermark(PDDocument document, PDPage page, WatermarkConfig config) 
            throws IOException {
        PDRectangle pageSize = page.getMediaBox();
        float pageWidth = pageSize.getWidth();
        float pageHeight = pageSize.getHeight();
        
        try (PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            
            // Set transparency
            PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
            graphicsState.setNonStrokingAlphaConstant(config.getOpacity());
            graphicsState.setStrokingAlphaConstant(config.getOpacity());
            contentStream.setGraphicsStateParameters(graphicsState);
            
            // Set font
            PDFont font = getFont(config.getFontName());
            contentStream.setFont(font, config.getFontSize());
            
            // Set color
            contentStream.setNonStrokingColor(config.getColor());
            
            // Calculate text dimensions
            float textWidth = font.getStringWidth(config.getText()) / 1000 * config.getFontSize();
            float textHeight = config.getFontSize();
            
            // Calculate position (this gives us the bottom-left corner)
            float[] position = calculatePosition(config, pageWidth, pageHeight, textWidth, textHeight);
            float x = position[0];
            float y = position[1];
            
            // Apply rotation and positioning
            contentStream.beginText();
            
            if (config.getRotation() != 0) {
                // Calculate center of text for rotation
                float centerX = x + textWidth / 2;
                float centerY = y + textHeight / 2;
                
                // Create rotation matrix around center point
                double radians = Math.toRadians(config.getRotation());
                Matrix matrix = new Matrix();
                matrix.translate(centerX, centerY);
                matrix.rotate(radians);
                matrix.translate(-textWidth / 2, -textHeight / 2);
                
                contentStream.setTextMatrix(matrix);
            } else {
                contentStream.newLineAtOffset(x, y);
            }
            
            contentStream.showText(config.getText());
            contentStream.endText();
        }
    }
    
    /**
     * Applies image watermark to a page.
     */
    private void applyImageWatermark(PDDocument document, PDPage page, WatermarkConfig config) 
            throws IOException {
        if (config.getImageFile() == null || !config.getImageFile().exists()) {
            throw new IOException("Image file not found: " + config.getImageFile());
        }
        
        PDRectangle pageSize = page.getMediaBox();
        float pageWidth = pageSize.getWidth();
        float pageHeight = pageSize.getHeight();
        
        // Load image
        BufferedImage bufferedImage = ImageIO.read(config.getImageFile());
        PDImageXObject pdImage = PDImageXObject.createFromFileByContent(
                config.getImageFile(), document);
        
        // Calculate scaled dimensions
        float imageWidth = pdImage.getWidth() * config.getScale();
        float imageHeight = pdImage.getHeight() * config.getScale();
        
        // Calculate position
        float[] position = calculatePosition(config, pageWidth, pageHeight, imageWidth, imageHeight);
        float x = position[0];
        float y = position[1];
        
        try (PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            
            // Set transparency
            PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
            graphicsState.setNonStrokingAlphaConstant(config.getOpacity());
            contentStream.setGraphicsStateParameters(graphicsState);
            
            // Save graphics state
            contentStream.saveGraphicsState();
            
            if (config.getRotation() != 0) {
                // Calculate center of image for rotation
                float centerX = x + imageWidth / 2;
                float centerY = y + imageHeight / 2;
                
                // Translate to center, rotate, translate back
                contentStream.transform(Matrix.getTranslateInstance(centerX, centerY));
                contentStream.transform(Matrix.getRotateInstance(Math.toRadians(config.getRotation()), 0, 0));
                contentStream.transform(Matrix.getTranslateInstance(-imageWidth / 2, -imageHeight / 2));
                
                contentStream.drawImage(pdImage, 0, 0, imageWidth, imageHeight);
            } else {
                contentStream.drawImage(pdImage, x, y, imageWidth, imageHeight);
            }
            
            // Restore graphics state
            contentStream.restoreGraphicsState();
        }
    }
    
    /**
     * Calculates the position for watermark based on configuration.
     */
    private float[] calculatePosition(WatermarkConfig config, float pageWidth, float pageHeight,
                                     float contentWidth, float contentHeight) {
        float x, y;

        y = switch (config.getPosition()) {
            case TOP_LEFT -> {
                x = 50;
                yield pageHeight - contentHeight - 50;
            }
            case TOP_CENTER -> {
                x = (pageWidth - contentWidth) / 2;
                yield pageHeight - contentHeight - 50;
            }
            case TOP_RIGHT -> {
                x = pageWidth - contentWidth - 50;
                yield pageHeight - contentHeight - 50;
            }
            case MIDDLE_LEFT -> {
                x = 50;
                yield (pageHeight - contentHeight) / 2;
            }
            case MIDDLE_RIGHT -> {
                x = pageWidth - contentWidth - 50;
                yield (pageHeight - contentHeight) / 2;
            }
            case BOTTOM_LEFT -> {
                x = 50;
                yield 50;
            }
            case BOTTOM_CENTER -> {
                x = (pageWidth - contentWidth) / 2;
                yield 50;
            }
            case BOTTOM_RIGHT -> {
                x = pageWidth - contentWidth - 50;
                yield 50;
            }
            case CUSTOM -> {
                x = config.getCustomX();
                yield config.getCustomY();
            }
            default -> {
                x = (pageWidth - contentWidth) / 2;
                yield (pageHeight - contentHeight) / 2;
            }
        };
        
        return new float[]{x, y};
    }
    
    /**
     * Determines which pages to apply watermark to based on configuration.
     */
    private List<Integer> determineTargetPages(PDDocument document, WatermarkConfig config) {
        List<Integer> pages = new ArrayList<>();
        int totalPages = document.getNumberOfPages();
        
        if (config.isApplyToAllPages()) {
            for (int i = 0; i < totalPages; i++) {
                pages.add(i);
            }
        } else {
            // Parse page range (e.g., "1-3,5,7-9")
            String[] ranges = config.getPageRange().split(",");
            for (String range : ranges) {
                range = range.trim();
                if (range.contains("-")) {
                    String[] parts = range.split("-");
                    try {
                        int start = Integer.parseInt(parts[0].trim()) - 1;
                        int end = Integer.parseInt(parts[1].trim()) - 1;
                        for (int i = Math.max(0, start); i <= Math.min(totalPages - 1, end); i++) {
                            if (!pages.contains(i)) {
                                pages.add(i);
                            }
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid page range: {}", range);
                    }
                } else {
                    try {
                        int page = Integer.parseInt(range) - 1;
                        if (page >= 0 && page < totalPages && !pages.contains(page)) {
                            pages.add(page);
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid page number: {}", range);
                    }
                }
            }
        }
        
        return pages;
    }
    
    /**
     * Gets PDFont based on font name.
     */
    private PDFont getFont(String fontName) {
        return switch (fontName.toLowerCase()) {
            case "helvetica-bold" -> new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            case "times-roman" -> new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            case "times-bold" -> new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
            case "courier" -> new PDType1Font(Standard14Fonts.FontName.COURIER);
            case "courier-bold" -> new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
            default -> new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        };
    }
}
