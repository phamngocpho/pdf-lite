package org.pdflite.view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import org.pdflite.model.Annotation;
import org.pdflite.model.HighlightAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import org.pdflite.model.SearchResult;
import static org.pdflite.util.Constants.LOW_RENDER_SCALE;

/**
 * Canvas layer for drawing annotations on top of PDF pages
 */
public class AnnotationLayer extends Canvas {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationLayer.class);

    private final List<Annotation> annotations = new ArrayList<>();
    private AnnotationMode currentMode = AnnotationMode.NONE;
    private Color currentColor = Color.YELLOW;
    private double startX, startY;
    private boolean isDrawing = false;

    private final List<SearchResult> searchHighlights = new ArrayList<>();
    private SearchResult activeSearchResult = null;
    private static final Color SEARCH_HIGHLIGHT_COLOR = Color.YELLOW;
    private static final Color ACTIVE_SEARCH_HIGHLIGHT_COLOR = Color.ORANGE;
    private static final double SEARCH_HIGHLIGHT_OPACITY = 0.4;
    private static final double ACTIVE_SEARCH_HIGHLIGHT_OPACITY = 0.6;
    private double scale = 1.0;

    public AnnotationLayer(double width, double height) {
        super(width, height);
        setupMouseHandlers();
        setupDebugListeners(); // Thêm dòng này
        System.out.println("ANNO_LAYER width=" + width + " height=" + height);
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    private void setupDebugListeners() {
        // Listener cho kích thước của AnnotationLayer
        this.widthProperty().addListener((obs, oldVal, newVal) -> {
            logger.debug("AnnotationLayer width changed from {} to {}", oldVal, newVal);
        });
        this.heightProperty().addListener((obs, oldVal, newVal) -> {
            logger.debug("AnnotationLayer height changed from {} to {}", oldVal, newVal);
        });

        // Listener cho kích thước thực tế của cửa sổ ứng dụng
        this.sceneProperty().addListener((obsScene, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((obsWindow, oldWindow, newWindow) -> {
                    if (newWindow != null) {
                        newWindow.widthProperty().addListener((obsW, oldW, newW)
                                -> logger.debug("Application window width changed from {} to {}", oldW, newW)
                        );
                        newWindow.heightProperty().addListener((obsH, oldH, newH)
                                -> logger.debug("Application window height from {} to {}", oldH, newH)
                        );
                    }
                });
            }
        });
    }

    private void setupMouseHandlers() {
        setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY && currentMode != AnnotationMode.NONE) {
                startX = event.getX();
                startY = event.getY();
                isDrawing = true;
            }
        });

        setOnMouseDragged(event -> {
            if (isDrawing && currentMode == AnnotationMode.HIGHLIGHT) {
                // Preview the highlight while dragging
                redraw();
                GraphicsContext gc = getGraphicsContext2D();
                gc.setFill(getColorWithAlpha(currentColor, 0.4));
                double x = Math.min(startX, event.getX());
                double y = Math.min(startY, event.getY());
                double w = Math.abs(event.getX() - startX);
                double h = Math.abs(event.getY() - startY);
                gc.fillRect(x, y, w, h);
            }
        });

        setOnMouseReleased(event -> {
            if (isDrawing && event.getButton() == MouseButton.PRIMARY) {
                switch (currentMode) {
                    case HIGHLIGHT:
                        addHighlight(startX, startY, event.getX(), event.getY());
                        break;
                    case DRAW:
                        // TODO: Implement freehand drawing
                        break;
                    case TEXT:
                        // TODO: Implement text annotation
                        break;
                }
                isDrawing = false;
                redraw();
            }
        });
    }

    private void addHighlight(double x1, double y1, double x2, double y2) {
        double x = Math.min(x1, x2);
        double y = Math.min(y1, y2);
        double width = Math.abs(x2 - x1);
        double height = Math.abs(y2 - y1);

        if (width > 5 && height > 5) { // Minimum size threshold
            HighlightAnnotation annotation = new HighlightAnnotation(0, x, y, width, height, currentColor);
            annotations.add(annotation);
            logger.debug("Added highlight annotation at ({}, {}) with size {}x{}", x, y, width, height);
        }
    }

    public void redraw() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());

        // Draw user annotations first
        for (Annotation annotation : annotations) {
            if (annotation instanceof HighlightAnnotation highlight) {
                gc.setFill(getColorWithAlpha(highlight.getColor(), 0.4));
                gc.fillRect(highlight.getX(), highlight.getY(),
                        highlight.getWidth(), highlight.getHeight());
            }
        }

        // Draw search highlights on top
        drawSearchHighlights(gc);
    }

    private Color getColorWithAlpha(Color color, double alpha) {
        return Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public void setAnnotationMode(AnnotationMode mode) {
        this.currentMode = mode;
        logger.debug("Annotation mode set to: {}", mode);
    }

    public AnnotationMode getCurrentMode() {
        return currentMode;
    }

    public void setHighlightColor(Color color) {
        this.currentColor = color;
    }

    public void clearAnnotations() {
        annotations.clear();
        redraw();
    }

    public List<Annotation> getAnnotations() {
        return new ArrayList<>(annotations);
    }

    public enum AnnotationMode {
        NONE,
        HIGHLIGHT,
        DRAW,
        TEXT,
        SHAPE
    }

    /**
     * Set search highlights for this page
     *
     * @param results List of search results to highlight
     */
    public void setSearchHighlights(List<SearchResult> results) {
        this.searchHighlights.clear();
        if (results != null) {
            this.searchHighlights.addAll(results);
        }
        redraw(); // ✅ IMPORTANT: Must call redraw
        logger.debug("Set {} search highlights", searchHighlights.size());
    }

    /**
     * Set the active (selected) search result
     *
     * @param result The active search result
     */
    public void setActiveSearchResult(SearchResult result) {
        this.activeSearchResult = result;
        redraw(); // ✅ IMPORTANT: Must call redraw
        logger.debug("Set active search result at ({}, {})",
                result != null ? result.getX() : 0,
                result != null ? result.getY() : 0);
    }

    /**
     * Clear all search highlights
     */
    public void clearSearchHighlights() {
        this.searchHighlights.clear();
        this.activeSearchResult = null;
        redraw(); // ✅ IMPORTANT: Must call redraw
        logger.debug("Cleared search highlights");
    }

    /**
     * Draw search highlights using accurate coordinates from PDFBox
     */
    private void drawSearchHighlights(GraphicsContext gc) {
        if (searchHighlights.isEmpty()) {
            return;
        }

        gc.save();

        // ✅ Get canvas dimensions (should match rendered PDF page size)
        double canvasWidth = getWidth();
        double canvasHeight = getHeight();

        logger.debug("Drawing on canvas {}x{} with scale={}", canvasWidth, canvasHeight, scale);

        for (SearchResult result : searchHighlights) {
            if (result.getWidth() <= 0 || result.getHeight() <= 0) {
                logger.warn("Invalid coordinates for search result: {}", result);
                continue;
            }

            boolean isActive = (activeSearchResult != null
                    && result.getPageNumber() == activeSearchResult.getPageNumber()
                    && result.getStartIndex() == activeSearchResult.getStartIndex()
                    && result.getEndIndex() == activeSearchResult.getEndIndex());

            Color highlightColor = isActive ? ACTIVE_SEARCH_HIGHLIGHT_COLOR : SEARCH_HIGHLIGHT_COLOR;
            double opacity = isActive ? ACTIVE_SEARCH_HIGHLIGHT_OPACITY : SEARCH_HIGHLIGHT_OPACITY;

            gc.setFill(Color.color(
                    highlightColor.getRed(),
                    highlightColor.getGreen(),
                    highlightColor.getBlue(),
                    opacity
            ));

            // ✅ FIXED: Both PDFBox and JavaFX now use top-left origin
            // No Y-axis conversion needed!
            // Coordinates are already in JavaFX coordinate system and scaled
            double finalscale = this.scale * LOW_RENDER_SCALE;
            double x = result.getX() * finalscale;
            double y = result.getY() * finalscale;
            double width = result.getWidth() * finalscale;
            double height = result.getHeight() * finalscale;

            gc.fillRect(x, y, width, height);

            if (isActive) {
                gc.setStroke(Color.DARKORANGE);
                gc.setLineWidth(2);
                gc.strokeRect(x, y, width, height);
            }

            logger.trace("Drew highlight at ({}, {}) size {}x{} on canvas {}x{}",
                    x, y, width, height, canvasWidth, scale);
        }

        gc.restore();
    }
}
