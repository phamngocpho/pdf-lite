package org.pdflite.service;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class for handling PDF operations
 */
public class PDFService {
    private static final Logger logger = LoggerFactory.getLogger(PDFService.class);
    private static final float DEFAULT_DPI = 150f;

    /**
     * Open a PDF file
     */
    public PDFDocument openPDF(File file) throws IOException {
        logger.info("Opening PDF file: {}", file.getAbsolutePath());
        PDDocument document = Loader.loadPDF(file);
        return new PDFDocument(document, file);
    }

    /**
     * Render a specific page of the PDF as an Image
     */
    public Image renderPage(PDFDocument pdfDoc, int pageIndex, float scale) throws IOException {
        if (pageIndex < 0 || pageIndex >= pdfDoc.getTotalPages()) {
            throw new IllegalArgumentException("Invalid page index: " + pageIndex);
        }

        // Check cache first
        Image cachedImage = pdfDoc.getCachedImage(pageIndex, scale);
        if (cachedImage != null) {
            logger.debug("Using cached image for page {}", pageIndex);
            return cachedImage;
        }

        PDFRenderer renderer = new PDFRenderer(pdfDoc.getDocument());
        float dpi = DEFAULT_DPI * scale;

        logger.debug("Rendering page {} with DPI {}", pageIndex, dpi);
        BufferedImage bufferedImage = renderer.renderImageWithDPI(pageIndex, dpi);

        Image image = SwingFXUtils.toFXImage(bufferedImage, null);

        // Cache the rendered image
        pdfDoc.cacheImage(pageIndex, scale, image);

        return image;
    }

    /**
     * Close the PDF document
     */
    public void closePDF(PDFDocument pdfDoc) {
        if (pdfDoc != null && pdfDoc.getDocument() != null) {
            try {
                pdfDoc.getDocument().close();
                logger.info("PDF document closed");
            } catch (IOException e) {
                logger.error("Error closing PDF document", e);
            }
        }
    }

    /**
     * Search for text in the PDF
     * @return true if text is found, false otherwise
     */
    public boolean searchText(PDFDocument pdfDoc, String searchTerm) throws IOException {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return false;
        }

        logger.info("Searching for text: {}", searchTerm);

        PDFTextStripper stripper = new PDFTextStripper();
        String allText = stripper.getText(pdfDoc.getDocument());

        return allText.toLowerCase().contains(searchTerm.toLowerCase());
    }

    /**
     * Extract text from a specific page
     */
    public String extractTextFromPage(PDFDocument pdfDoc, int pageIndex) throws IOException {
        if (pageIndex < 0 || pageIndex >= pdfDoc.getTotalPages()) {
            throw new IllegalArgumentException("Invalid page index: " + pageIndex);
        }
        
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        return stripper.getText(pdfDoc.getDocument());
    }

    /**
     * Search for text and return list of page numbers where it's found
     * @return List of page indices (0-based) where the search term is found
     */
    public List<Integer> searchTextInPages(PDFDocument pdfDoc, String searchTerm) throws IOException {
        List<Integer> matchingPages = new ArrayList<>();

        if (searchTerm == null || searchTerm.isEmpty()) {
            return matchingPages;
        }

        String lowerSearchTerm = searchTerm.toLowerCase();

        for (int i = 0; i < pdfDoc.getTotalPages(); i++) {
            String pageText = extractTextFromPage(pdfDoc, i);
            if (pageText.toLowerCase().contains(lowerSearchTerm)) {
                matchingPages.add(i);
            }
        }

        logger.info("Found '{}' on {} page(s)", searchTerm, matchingPages.size());
        return matchingPages;
    }
}
