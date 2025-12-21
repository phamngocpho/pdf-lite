package org.pdflite.manager;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Manages all zoom-related operations for the PDF viewer.
 * Handles zoom in/out, fit to width/page, and zoom combo box.
 */
public class ZoomManager {
    private static final Logger logger = LoggerFactory.getLogger(ZoomManager.class);

    private final PDFService pdfService;
    private ZoomChangeListener zoomChangeListener;

    private double currentZoom = Constants.DEFAULT_ZOOM;
    private ComboBox<String> zoomComboBox;
    private ScrollPane scrollPane;
    private PDFDocument currentDocument;

    /**
     * Interface for listening to zoom changes.
     */
    public interface ZoomChangeListener {
        void onZoomChanged(double newZoom);

        void onZoomApplied(double newZoom, String statusMessage);
    }

    /**
     * Creates a new ZoomManager.
     *
     * @param pdfService         the PDF service for rendering pages
     * @param zoomChangeListener listener for zoom change events (can be null initially)
     */
    public ZoomManager(PDFService pdfService, ZoomChangeListener zoomChangeListener) {
        this.pdfService = pdfService;
        this.zoomChangeListener = zoomChangeListener;
    }

    /**
     * Sets the zoom change listener.
     *
     * @param zoomChangeListener the zoom change listener
     */
    public void setZoomChangeListener(ZoomChangeListener zoomChangeListener) {
        this.zoomChangeListener = zoomChangeListener;
    }

    /**
     * Initializes the zoom manager with UI components.
     *
     * @param zoomComboBox the zoom combo box
     * @param scrollPane   the scroll pane for viewport calculations
     */
    public void initialize(ComboBox<String> zoomComboBox, ScrollPane scrollPane) {
        this.zoomComboBox = zoomComboBox;
        this.scrollPane = scrollPane;

        if (zoomComboBox != null) {
            zoomComboBox.getItems().addAll("50%", "75%", "100%", "125%", "150%", "200%");
            zoomComboBox.setValue("100%");
        }
    }

    /**
     * Sets the current document.
     *
     * @param document the PDF document
     */
    public void setDocument(PDFDocument document) {
        this.currentDocument = document;
    }

    /**
     * Gets the current zoom level.
     *
     * @return the current zoom level
     */
    public double getCurrentZoom() {
        return currentZoom;
    }

    /**
     * Sets the zoom level.
     * Updates the zoom combo box display if document is loaded.
     *
     * @param zoom the new zoom level
     */
    public void setCurrentZoom(double zoom) {
        this.currentZoom = zoom;

        // Update the zoom combo box display immediately for better visibility
        if (currentDocument != null && zoomComboBox != null) {
            String zoomValue = String.format("%.0f%%", zoom * 100);
            zoomComboBox.setValue(zoomValue);
        }
    }

    /**
     * Handles zoom in action.
     */
    public void zoomIn() {
        logger.info("Zoom In clicked - current zoom: {}, document: {}", currentZoom, currentDocument != null ? "loaded" : "null");
        currentZoom = Math.min(Constants.MAX_ZOOM, currentZoom + Constants.ZOOM_STEP);
        logger.info("New zoom level: {}", currentZoom);
        applyZoom(null);
    }

    /**
     * Handles zoom out action.
     */
    public void zoomOut() {
        logger.info("Zoom Out clicked - current zoom: {}, document: {}", currentZoom, currentDocument != null ? "loaded" : "null");
        currentZoom = Math.max(Constants.MIN_ZOOM, currentZoom - Constants.ZOOM_STEP);
        logger.info("New zoom level: {}", currentZoom);
        applyZoom(null);
    }

    /**
     * Handles zoom combo box change.
     */
    public void handleZoomComboBoxChange() {
        if (zoomComboBox != null && currentDocument != null) {
            String value = zoomComboBox.getValue();
            if (value != null) {
                try {
                    currentZoom = Double.parseDouble(value.replace("%", "")) / 100.0;
                    applyZoom(null);
                } catch (NumberFormatException e) {
                    logger.error("Invalid zoom value: {}", value);
                }
            }
        }
    }

    /**
     * Handles fit to width action.
     */
    public void fitToWidth() {
        if (currentDocument != null && scrollPane != null) {
            try {
                Image image = pdfService.renderPage(currentDocument, currentDocument.getCurrentPage(), 1.0f);
                double viewportWidth = scrollPane.getViewportBounds().getWidth() - 20;
                double imageWidth = image.getWidth();
                currentZoom = Math.min(1.0, viewportWidth / imageWidth);
                applyZoom("Fit to Width");
            } catch (IOException e) {
                logger.error("Error fitting to width", e);
            }
        }
    }

    /**
     * Handles fit to page action.
     */
    public void fitToPage() {
        if (currentDocument != null && scrollPane != null) {
            try {
                Image image = pdfService.renderPage(currentDocument, currentDocument.getCurrentPage(), 1.0f);
                currentZoom = calculateFitToPageZoom(image.getWidth(), image.getHeight());
                applyZoom("Fit to Page");
            } catch (IOException e) {
                logger.error("Error fitting to page", e);
            }
        }
    }

    /**
     * Calculates optimal zoom to fit page in viewport.
     *
     * @param imageWidth  the image width
     * @param imageHeight the image height
     * @return the calculated zoom level
     */
    private double calculateFitToPageZoom(double imageWidth, double imageHeight) {
        if (scrollPane == null) {
            return Constants.DEFAULT_ZOOM;
        }

        double viewportWidth = scrollPane.getViewportBounds().getWidth() - 20;
        double viewportHeight = scrollPane.getViewportBounds().getHeight() - 20;

        double zoomWidth = viewportWidth / imageWidth;
        double zoomHeight = viewportHeight / imageHeight;

        return Math.min(1.0, Math.min(zoomWidth, zoomHeight));
    }

    /**
     * Applies the current zoom level.
     *
     * @param prefix optional prefix for status message
     */
    private void applyZoom(String prefix) {
        logger.info("applyZoom called - document: {}, listener: {}",
                currentDocument != null ? "loaded" : "null",
                zoomChangeListener != null ? "set" : "null");

        if (currentDocument != null) {
            currentDocument.setZoomLevel(currentZoom);
            logger.info("Set document zoom level to: {}", currentZoom);

            // Update zoom combo box with clear percentage display
            if (zoomComboBox != null) {
                String zoomValue = String.format("%.0f%%", currentZoom * 100);
                zoomComboBox.setValue(zoomValue);
            }

            // Display zoom level clearly in status messages
            String statusMessage = prefix != null
                    ? String.format("%s - Zoom: %.0f%%", prefix, currentZoom * 100)
                    : String.format("Zoom: %.0f%%", currentZoom * 100);

            if (zoomChangeListener != null) {
                logger.info("Calling zoomChangeListener.onZoomChanged({})", currentZoom);
                zoomChangeListener.onZoomChanged(currentZoom);
                zoomChangeListener.onZoomApplied(currentZoom, statusMessage);
            } else {
                logger.warn("zoomChangeListener is null!");
            }
        } else {
            logger.warn("currentDocument is null - cannot apply zoom!");
        }
    }

    /**
     * Calculates initial zoom to fit the page when opening documents.
     *
     * @param firstPageImage the first page image
     * @return the calculated zoom level
     */
    public double calculateInitialZoom(Image firstPageImage) {
        if (scrollPane != null && scrollPane.getViewportBounds().getWidth() > 0
                && scrollPane.getViewportBounds().getHeight() > 0) {
            return calculateFitToPageZoom(firstPageImage.getWidth(), firstPageImage.getHeight());
        } else {
            return 0.7;
        }
    }

    /**
     * Calculates initial zoom to fit the page when opening documents from page dimensions.
     * This avoids rendering the first page with scale 1.0f, ensuring consistent sizing.
     *
     * @param pageWidth  the page width at scale 1.0
     * @param pageHeight the page height at scale 1.0
     * @return the calculated zoom level
     */
    public double calculateInitialZoomFromDimensions(double pageWidth, double pageHeight) {
        if (scrollPane != null && scrollPane.getViewportBounds().getWidth() > 0
                && scrollPane.getViewportBounds().getHeight() > 0) {
            return calculateFitToPageZoom(pageWidth, pageHeight);
        } else {
            return 0.7;
        }
    }
}

