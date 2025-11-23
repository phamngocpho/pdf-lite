package org.pdflite.service;

import javafx.print.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for printing PDF documents using JavaFX PrinterJob API.
 * Handles print preview, page range selection, and respects zoom/rotation.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record PDFPrintService(PDFService pdfService) {

    private static final Logger logger = LoggerFactory.getLogger(PDFPrintService.class);

    /**
     * Constructor with PDFService dependency.
     *
     * @param pdfService the PDF service for rendering pages
     */
    public PDFPrintService {
    }

    /**
     * Print the entire PDF document.
     *
     * @param document the PDF document to print
     * @return true if printing was successful or initiated, false if canceled
     */
    public boolean printDocument(PDFDocument document) {
        if (document == null) {
            logger.warn("Cannot print document: document is null");
            return false;
        }

        return printPages(document, 0, document.getTotalPages() - 1);
    }

    /**
     * Print a single page from the PDF document.
     *
     * @param document   the PDF document
     * @param pageNumber the page number to print (0-based)
     * @return true if printing was successful or initiated, false if canceled
     */
    public boolean printPage(PDFDocument document, int pageNumber) {
        if (document == null) {
            logger.warn("Cannot print page: document is null");
            return false;
        }

        if (pageNumber < 0 || pageNumber >= document.getTotalPages()) {
            logger.warn("Invalid page number: {}", pageNumber);
            return false;
        }

        return printPages(document, pageNumber, pageNumber);
    }

    /**
     * Print a range of pages from the PDF document.
     *
     * @param document  the PDF document
     * @param startPage the starting page (0-based, inclusive)
     * @param endPage   the ending page (0-based, inclusive)
     * @return true if printing was successful or initiated, false if canceled
     */
    public boolean printPages(PDFDocument document, int startPage, int endPage) {
        if (document == null) {
            logger.warn("Cannot print pages: document is null");
            return false;
        }

        // Validate page range
        if (startPage < 0 || endPage >= document.getTotalPages() || startPage > endPage) {
            logger.warn("Invalid page range: {} to {}", startPage, endPage);
            return false;
        }

        logger.info("Starting print job for pages {} to {}", startPage + 1, endPage + 1);

        // Create a printer job
        PrinterJob printerJob = PrinterJob.createPrinterJob();
        if (printerJob == null) {
            logger.error("Failed to create printer job");
            return false;
        }

        try {
            // Show a print dialog
            boolean proceed = printerJob.showPrintDialog(null);
            if (!proceed) {
                logger.info("Print job cancelled by user");
                return false;
            }

            // Get printer and page layout
            Printer printer = printerJob.getPrinter();
            PageLayout pageLayout = printerJob.getJobSettings().getPageLayout();

            logger.info("Printing to: {}", printer.getName());
            logger.info("Page layout: {}x{}", pageLayout.getPrintableWidth(), pageLayout.getPrintableHeight());

            // Print each page in the range
            boolean success = true;
            for (int pageNum = startPage; pageNum <= endPage; pageNum++) {
                if (!printSinglePage(printerJob, document, pageNum, pageLayout)) {
                    success = false;
                    break;
                }
            }

            // End the print job
            if (success) {
                printerJob.endJob();
                logger.info("Print job completed successfully");
                return true;
            } else {
                printerJob.cancelJob();
                logger.error("Print job failed");
                return false;
            }

        } catch (Exception e) {
            logger.error("Error during printing", e);
            printerJob.cancelJob();
            return false;
        }
    }

    /**
     * Print a single page with proper scaling and layout.
     *
     * @param printerJob the printer job
     * @param document   the PDF document
     * @param pageNum    the page number (0-based)
     * @param pageLayout the page layout settings
     * @return true if the page was printed successfully
     */
    private boolean printSinglePage(PrinterJob printerJob, PDFDocument document,
                                    int pageNum, PageLayout pageLayout) {
        try {
            logger.debug("Printing page {}", pageNum + 1);

            // Render page at high quality for printing (use 2.0 scale for better quality)
            float renderScale = 2.0f;
            Image pageImage = pdfService.renderPage(document, pageNum, renderScale);

            if (pageImage == null) {
                logger.error("Failed to render page {} for printing", pageNum + 1);
                return false;
            }

            // Create an ImageView for printing
            ImageView imageView = getImageView(pageLayout, pageImage);

            // Print the page
            boolean printed = printerJob.printPage(pageLayout, imageView);

            if (!printed) {
                logger.error("Failed to print page {}", pageNum + 1);
                return false;
            }

            logger.debug("Successfully printed page {}", pageNum + 1);
            return true;

        } catch (Exception e) {
            logger.error("Error printing page {}", pageNum + 1, e);
            return false;
        }
    }

    private static ImageView getImageView(PageLayout pageLayout, Image pageImage) {
        ImageView imageView = new ImageView(pageImage);
        imageView.setPreserveRatio(true);

        // Calculate scale to fit the page layout
        double printableWidth = pageLayout.getPrintableWidth();
        double printableHeight = pageLayout.getPrintableHeight();

        double scaleX = printableWidth / pageImage.getWidth();
        double scaleY = printableHeight / pageImage.getHeight();
        double scale = Math.min(scaleX, scaleY);

        // Apply scaling
        imageView.setFitWidth(pageImage.getWidth() * scale);
        imageView.setFitHeight(pageImage.getHeight() * scale);
        return imageView;
    }

    /**
     * Get a list of available printers.
     *
     * @return list of available printers
     */
    public List<Printer> getAvailablePrinters() {
        List<Printer> printers = new ArrayList<>();
        try {
            printers.addAll(Printer.getAllPrinters());
            logger.debug("Found {} available printers", printers.size());
        } catch (Exception e) {
            logger.error("Error getting available printers", e);
        }
        return printers;
    }

    /**
     * Get the default printer.
     *
     * @return the default printer, or null if none available
     */
    public Printer getDefaultPrinter() {
        try {
            Printer printer = Printer.getDefaultPrinter();
            if (printer != null) {
                logger.debug("Default printer: {}", printer.getName());
            }
            return printer;
        } catch (Exception e) {
            logger.error("Error getting default printer", e);
            return null;
        }
    }

    /**
     * Check if printing is available on this system.
     *
     * @return true if at least one printer is available
     */
    public boolean isPrintingAvailable() {
        return getDefaultPrinter() != null || !getAvailablePrinters().isEmpty();
    }
}

