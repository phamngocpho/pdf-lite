package org.pdflite.manager;

import java.awt.geom.Rectangle2D;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.pdflite.controller.PageRenderer;
import org.pdflite.model.Annotation;
import org.pdflite.model.HighlightAnnotation;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.ColorPicker;
import javafx.scene.paint.Color;

/**
 * Manages highlight operations including color selection, creation, and persistence.
 * This manager separates highlighted concerns from MainController.
 */
public class HighlightManager {
    private static final Logger logger = LoggerFactory.getLogger(HighlightManager.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final ColorPicker highlightColorPicker;
    private final HighlightPersistenceManager persistenceManager;
    private final UIStateManager uiStateManager;

    private final Supplier<PDFDocument> documentSupplier;
    private final DoubleSupplier zoomSupplier;
    private final Supplier<AnnotationManager> annotationManagerSupplier;

    private static final double HIGHLIGHT_HEIGHT_MULTIPLIER = 1.35;
    private static final double HIGHLIGHT_EXTRA_PADDING_PX = 2.0;
    private static final double HIGHLIGHT_DUPLICATE_TOLERANCE = 0.8;
    private static final double MIN_REGION_SIZE = 0.25;

    /**
     * Creates a new HighlightManager.
     *
     * @param highlightColorPicker the color picker for highlight color selection
     * @param uiStateManager       the UI state manager
     */
    public HighlightManager(ColorPicker highlightColorPicker,
                            UIStateManager uiStateManager,
                            Supplier<PDFDocument> documentSupplier,
                            DoubleSupplier zoomSupplier,
                            Supplier<AnnotationManager> annotationManagerSupplier) {
        this.highlightColorPicker = highlightColorPicker;
        this.uiStateManager = uiStateManager;
        this.documentSupplier = documentSupplier;
        this.zoomSupplier = zoomSupplier;
        this.annotationManagerSupplier = annotationManagerSupplier;
        this.persistenceManager = new HighlightPersistenceManager();

        initializeColorPicker();

        logger.info("HighlightManager initialized");
    }

    /**
     * Initializes the highlight color picker with default values and event handlers.
     */
    private void initializeColorPicker() {
        if (highlightColorPicker != null) {
            // Color picker state and persistence are managed by DrawingToolsSetupManager.
            // Keep this manager read-only to avoid overriding user custom colors/actions.
            logger.debug("Highlight color picker linked to HighlightManager");
        }
    }

    /**
     * Sets up the highlight callback for the context menu handler.
     * This callback is invoked when the user highlights text from the context menu.
     *
     * @param pageRenderer the page renderer containing the context menu handler
     */
    public void setupHighlightCallback(PageRenderer pageRenderer) {
        pageRenderer.getContextMenuHandler().setHighlightCallback(
                (pageIndex, highlightRegions, defaultColor) -> {
                    try {
                        PDFDocument currentDocument = documentSupplier.get();
                        if (currentDocument == null) {
                            uiStateManager.updateStatus(lang().getString("highlight.noDocument"));
                            logger.warn("Cannot highlight: no document loaded");
                            return;
                        }

                        // Get the highlight color from the color picker
                        Color highlightColor = getHighlightColor();

                        double zoom = zoomSupplier.getAsDouble();

                        logger.info("Creating {} highlights on page {} with color {}",
                                highlightRegions.size(), pageIndex + 1, highlightColor);

                        final String batchId = java.util.UUID.randomUUID().toString();

                        int addedCount = 0;
                        List<Rect> existingRects = collectExistingHighlightRects(currentDocument, pageIndex);

                        // Create highlight annotations for each region
                        for (Rectangle2D region : highlightRegions) {
                            // highlightRegions are in PDF coordinates (from SmartTextSelector)
                            // Store in normalized coordinates (PDF coords * LOW_RENDER_SCALE)
                            // so highlights remain correctly positioned when zoom changes

                            // Convert PDF coordinates to normalized coordinates (independent of zoom)
                            double normalizedX = region.getX() * org.pdflite.util.Constants.LOW_RENDER_SCALE;
                            double normalizedY = region.getY() * org.pdflite.util.Constants.LOW_RENDER_SCALE;
                            double normalizedWidth = region.getWidth() * org.pdflite.util.Constants.LOW_RENDER_SCALE;

                            double originalHeight = region.getHeight() * org.pdflite.util.Constants.LOW_RENDER_SCALE;
                            double expandedHeight = (originalHeight * HIGHLIGHT_HEIGHT_MULTIPLIER) + HIGHLIGHT_EXTRA_PADDING_PX;
                            double normalizedHeight = expandedHeight;
                            double normalizedYAdjusted = normalizedY - ((expandedHeight - originalHeight) / 2.0);

                            Rect candidate = new Rect(normalizedX, normalizedYAdjusted, normalizedWidth, normalizedHeight);
                            List<Rect> nonOverlapping = subtractOverlaps(candidate, existingRects);

                            for (Rect piece : nonOverlapping) {
                                if (piece.width < MIN_REGION_SIZE || piece.height < MIN_REGION_SIZE) {
                                    continue;
                                }
                                if (isDuplicateHighlight(currentDocument, pageIndex,
                                        piece.x, piece.y, piece.width, piece.height)) {
                                    continue;
                                }

                                HighlightAnnotation highlight = new HighlightAnnotation(
                                        pageIndex,
                                        piece.x,
                                        piece.y,
                                        piece.width,
                                        piece.height,
                                        highlightColor,
                                        batchId);
                                currentDocument.addAnnotation(highlight);
                                existingRects.add(piece);
                                addedCount++;
                            }

                            logger.debug("Processed highlight region: page={}, pdfX={}, pdfY={}, normalizedX={}, normalizedY={}, w={}, h={}",
                                    pageIndex, region.getX(), region.getY(), normalizedX, normalizedYAdjusted, normalizedWidth, normalizedHeight);
                        }

                        // Mark document as modified
                        if (addedCount > 0) {
                            currentDocument.setHasUnsavedEdits(true);
                        }

                        // Refresh the page to show the highlights
                        AnnotationManager annotationManager = annotationManagerSupplier.get();
                        if (annotationManager != null) {
                            annotationManager.refreshPageAnnotations(pageIndex);
                        }

                        // Update status
                        if (addedCount > 0) {
                            uiStateManager.updateStatus(
                                    MessageFormat.format(lang().getString("highlight.added"), addedCount));
                        } else {
                            uiStateManager.updateStatus(lang().getString("highlight.alreadyExists"));
                        }

                    } catch (Exception e) {
                        logger.error("Error creating highlights", e);
                        uiStateManager.updateStatus(lang().getString("highlight.error") + ": " + e.getMessage());

                        // Show error dialog
                        javafx.application.Platform.runLater(() -> {
                            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                                    javafx.scene.control.Alert.AlertType.ERROR);
                            alert.setTitle(lang().getString("highlight.errorTitle"));
                            alert.setHeaderText(lang().getString("highlight.errorHeader"));
                            alert.setContentText(e.getMessage());
                            alert.showAndWait();
                        });
                    }
                });

        logger.info("Highlight callback configured successfully");
    }

    private boolean isDuplicateHighlight(PDFDocument document, int pageIndex,
                                         double x, double y, double width, double height) {
        for (Annotation annotation : document.getAnnotations()) {
            if (!(annotation instanceof HighlightAnnotation existing)) {
                continue;
            }
            if (existing.getPageNumber() != pageIndex) {
                continue;
            }

            if (Math.abs(existing.getX() - x) < HIGHLIGHT_DUPLICATE_TOLERANCE
                    && Math.abs(existing.getY() - y) < HIGHLIGHT_DUPLICATE_TOLERANCE
                    && Math.abs(existing.getWidth() - width) < HIGHLIGHT_DUPLICATE_TOLERANCE
                    && Math.abs(existing.getHeight() - height) < HIGHLIGHT_DUPLICATE_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    private List<Rect> collectExistingHighlightRects(PDFDocument document, int pageIndex) {
        List<Rect> rects = new ArrayList<>();
        for (Annotation annotation : document.getAnnotations()) {
            if (annotation instanceof HighlightAnnotation highlight && highlight.getPageNumber() == pageIndex) {
                rects.add(new Rect(highlight.getX(), highlight.getY(), highlight.getWidth(), highlight.getHeight()));
            }
        }
        return rects;
    }

    private List<Rect> subtractOverlaps(Rect candidate, List<Rect> existingRects) {
        List<Rect> result = new ArrayList<>();
        result.add(candidate);

        for (Rect existing : existingRects) {
            List<Rect> next = new ArrayList<>();
            for (Rect current : result) {
                next.addAll(subtractRect(current, existing));
            }
            result = next;
            if (result.isEmpty()) {
                break;
            }
        }

        return result;
    }

    private List<Rect> subtractRect(Rect source, Rect mask) {
        Rect intersection = source.intersection(mask);
        if (intersection == null) {
            return List.of(source);
        }

        List<Rect> pieces = new ArrayList<>();

        // Top
        addRectIfValid(pieces, new Rect(
                source.x,
                source.y,
                source.width,
                intersection.y - source.y));

        // Bottom
        addRectIfValid(pieces, new Rect(
                source.x,
                intersection.y + intersection.height,
                source.width,
                (source.y + source.height) - (intersection.y + intersection.height)));

        // Left
        addRectIfValid(pieces, new Rect(
                source.x,
                intersection.y,
                intersection.x - source.x,
                intersection.height));

        // Right
        addRectIfValid(pieces, new Rect(
                intersection.x + intersection.width,
                intersection.y,
                (source.x + source.width) - (intersection.x + intersection.width),
                intersection.height));

        return pieces;
    }

    private void addRectIfValid(List<Rect> target, Rect rect) {
        if (rect.width > MIN_REGION_SIZE && rect.height > MIN_REGION_SIZE) {
            target.add(rect);
        }
    }

    private static final class Rect {
        private final double x;
        private final double y;
        private final double width;
        private final double height;

        private Rect(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private Rect intersection(Rect other) {
            double ix = Math.max(this.x, other.x);
            double iy = Math.max(this.y, other.y);
            double ix2 = Math.min(this.x + this.width, other.x + other.width);
            double iy2 = Math.min(this.y + this.height, other.y + other.height);

            double iw = ix2 - ix;
            double ih = iy2 - iy;
            if (iw <= 0 || ih <= 0) {
                return null;
            }
            return new Rect(ix, iy, iw, ih);
        }
    }

    /**
     * Sets up the delete highlight callback for the context menu handler.
     * Deletes all segments created in the same highlight action (batch) when possible.
     */
    public void setupDeleteHighlightCallback(PageRenderer pageRenderer) {
        pageRenderer.getContextMenuHandler().setDeleteHighlightCallback(
                (pageIndex, canvasX, canvasY) -> {
                    try {
                        PDFDocument currentDocument = documentSupplier.get();
                        if (currentDocument == null) {
                            uiStateManager.updateStatus(lang().getString("highlight.noDocument"));
                            return;
                        }

                        double zoom = zoomSupplier.getAsDouble();

                        // Find topmost highlight at cursor (reverse order)
                        // canvasX/canvasY are in screen coordinates
                        // Highlights are stored in render coordinates (screen / zoom)
                        HighlightAnnotation target = null;
                        List<Annotation> all = currentDocument.getAnnotations();
                        
                        for (int i = all.size() - 1; i >= 0; i--) {
                            Annotation annotation = all.get(i);
                            if (annotation.getPageNumber() != pageIndex) {
                                continue;
                            }
                            if (annotation instanceof HighlightAnnotation highlight) {
                                // Scale render coordinates to screen coordinates for hit detection
                                double scaledX = highlight.getX() * zoom;
                                double scaledY = highlight.getY() * zoom;
                                double scaledWidth = highlight.getWidth() * zoom;
                                double scaledHeight = highlight.getHeight() * zoom;
                                double x2 = scaledX + scaledWidth;
                                double y2 = scaledY + scaledHeight;
                                
                                if (canvasX >= scaledX && canvasX <= x2 && canvasY >= scaledY && canvasY <= y2) {
                                    target = highlight;
                                    break;
                                }
                            }
                        }

                        if (target == null) {
                            uiStateManager.updateStatus(lang().getString("highlight.noHighlightAtCursor"));
                            return;
                        }

                        int removedCount = 0;
                        String batchId = target.getBatchId();
                        if (batchId != null && !batchId.isBlank()) {
                            for (int i = all.size() - 1; i >= 0; i--) {
                                Annotation annotation = all.get(i);
                                if (annotation.getPageNumber() != pageIndex) {
                                    continue;
                                }
                                if (annotation instanceof HighlightAnnotation highlight) {
                                    if (batchId.equals(highlight.getBatchId())) {
                                        all.remove(i);
                                        removedCount++;
                                    }
                                }
                            }
                        } else {
                            boolean removed = all.remove(target);
                            removedCount = removed ? 1 : 0;
                        }

                        if (removedCount <= 0) {
                            uiStateManager.updateStatus(lang().getString("highlight.deleteFailed"));
                            return;
                        }

                        currentDocument.setHasUnsavedEdits(true);

                        AnnotationManager annotationManager = annotationManagerSupplier.get();
                        if (annotationManager != null) {
                            annotationManager.refreshPageAnnotations(pageIndex);
                        }

                        uiStateManager.updateStatus(MessageFormat.format(lang().getString("highlight.deleted"), removedCount));
                    } catch (Exception e) {
                        logger.error("Error deleting highlight", e);
                        uiStateManager.updateStatus(lang().getString("highlight.errorDelete") + ": " + e.getMessage());
                    }
                }
        );
    }

    /**
     * Updates highlight color for all pages.
     */
    private void updateHighlightColorForAllPages() {
        AnnotationManager annotationManager = annotationManagerSupplier.get();
        if (annotationManager != null && highlightColorPicker != null) {
            annotationManager.updateHighlightColorForAllPages(highlightColorPicker.getValue());
        }
    }

    /**
     * Gets the current highlight color from the color picker.
     *
     * @return the highlight color (defaults to YELLOW if picker is null)
     */
    public Color getHighlightColor() {
        return highlightColorPicker != null ?
                highlightColorPicker.getValue() : Color.YELLOW;
    }

    /**
     * Sets the current document and zoom level.
     *
     * @param document the PDF document
     * @param zoom the current zoom level
     */
    // Document and zoom come from suppliers (MainController).

    /**
     * Loads existing highlights from the PDF document.
     *
     * @param document the PDF document to load highlights from
     */
    public void loadHighlights(PDFDocument document) {
        if (document == null || persistenceManager == null) {
            return;
        }

        try {
            List<HighlightAnnotation> loadedHighlights =
                    persistenceManager.loadHighlightsFromPDF(document.getDocument());

            // Add loaded highlights to document
            for (HighlightAnnotation highlight : loadedHighlights) {
                document.addAnnotation(highlight);
            }

            logger.info("Loaded {} highlights from PDF", loadedHighlights.size());
        } catch (Exception e) {
            logger.error("Error loading highlights from PDF", e);
        }
    }

    /**
     * Saves highlights to the PDF document.
     *
     * @param document the PDF document to save highlights to
     */
    public void saveHighlights(PDFDocument document) {
        if (document == null || persistenceManager == null) {
            return;
        }

        try {
            persistenceManager.saveHighlightsToPDF(
                    document.getDocument(),
                    document.getAnnotations());
            logger.info("Highlights saved to PDF");
        } catch (Exception e) {
            logger.error("Error saving highlights to PDF", e);
            uiStateManager.showError(lang().getString("highlight.saveError"),
                    lang().getString("highlight.saveErrorMsg") + ": " + e.getMessage());
        }
    }
}
