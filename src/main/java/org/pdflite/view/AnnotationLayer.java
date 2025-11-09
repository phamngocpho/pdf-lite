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
        logger.debug("AnnotationLayer created: {}x{}", width, height);
    }

    public void setScale(double scale) {
        this.scale = scale;
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
                        break;
                    case TEXT:
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

        if (width > 5 && height > 5) {
            HighlightAnnotation annotation = new HighlightAnnotation(0, x, y, width, height, currentColor);
            annotations.add(annotation);
            logger.debug("Added highlight annotation at ({}, {}) with size {}x{}", x, y, width, height);
        }
    }

    public void redraw() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());

        for (Annotation annotation : annotations) {
            if (annotation instanceof HighlightAnnotation highlight) {
                gc.setFill(getColorWithAlpha(highlight.getColor(), 0.4));
                gc.fillRect(highlight.getX(), highlight.getY(),
                        highlight.getWidth(), highlight.getHeight());
            }
        }

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

    // ==================== SEARCH HIGHLIGHTS ====================

    public void setSearchHighlights(List<SearchResult> results) {
        this.searchHighlights.clear();
        if (results != null) {
            this.searchHighlights.addAll(results);
        }
        redraw();
        logger.debug("Set {} search highlights", searchHighlights.size());
    }

    public void setActiveSearchResult(SearchResult result) {
        if (activeSearchResult != null && activeSearchResult.equals(result)) {
            logger.trace("Active result unchanged: {}", result);
            return;
        }
        
        this.activeSearchResult = result;
        redraw();
        
        if (result != null) {
            logger.debug("Set active search result: page={}, start={}, end={}, pos=({}, {})",
                    result.getPageNumber(), result.getStartIndex(), result.getEndIndex(),
                    result.getX(), result.getY());
        } else {
            logger.debug("Cleared active search result");
        }
    }

    public void clearSearchHighlights() {
        this.searchHighlights.clear();
        this.activeSearchResult = null;
        redraw();
        logger.debug("Cleared search highlights");
    }

    private void drawSearchHighlights(GraphicsContext gc) {
        if (searchHighlights.isEmpty()) {
            return;
        }

        gc.save();

        double canvasWidth = getWidth();
        double canvasHeight = getHeight();

        logger.trace("Drawing {} highlights on canvas {}x{} with scale={}",
                searchHighlights.size(), canvasWidth, canvasHeight, scale);

        int normalCount = 0;
        int activeCount = 0;

        for (SearchResult result : searchHighlights) {
            if (result.getWidth() <= 0 || result.getHeight() <= 0) {
                logger.warn("Invalid coordinates for search result: {}", result);
                continue;
            }
            
            boolean isActive = (activeSearchResult != null && result.equals(activeSearchResult));

            Color highlightColor = isActive ? ACTIVE_SEARCH_HIGHLIGHT_COLOR : SEARCH_HIGHLIGHT_COLOR;
            double opacity = isActive ? ACTIVE_SEARCH_HIGHLIGHT_OPACITY : SEARCH_HIGHLIGHT_OPACITY;

            gc.setFill(Color.color(
                    highlightColor.getRed(),
                    highlightColor.getGreen(),
                    highlightColor.getBlue(),
                    opacity
            ));

            double finalScale = this.scale * LOW_RENDER_SCALE;
            double x = result.getX() * finalScale;
            double y = result.getY() * finalScale;
            double width = result.getWidth() * finalScale;
            double height = result.getHeight() * finalScale;

            gc.fillRect(x, y, width, height);

            if (isActive) {
                gc.setStroke(Color.DARKORANGE);
                gc.setLineWidth(2);
                gc.strokeRect(x, y, width, height);
                activeCount++;
                
                logger.trace("Drew ACTIVE highlight at ({}, {}) size {}x{} - page={}, start={}",
                        x, y, width, height, result.getPageNumber(), result.getStartIndex());
            } else {
                normalCount++;
            }
        }

        gc.restore();

        if (activeCount > 1) {
            logger.warn("⚠️ Multiple active highlights detected! Count: {}", activeCount);
        }
        
        logger.trace("Drew {} normal + {} active highlights", normalCount, activeCount);
    }
}