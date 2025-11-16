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
 * Interactive canvas layer for drawing and managing annotations on PDF pages.
 * <p>
 * This class extends JavaFX Canvas and provides an overlay layer that sits on top
 * of rendered PDF pages. It allows users to create annotations by interacting with
 * the mouse. The layer supports multiple annotation modes:
 * <ul>
 *   <li>{@link AnnotationMode#NONE} - No annotation functionality</li>
 *   <li>{@link AnnotationMode#HIGHLIGHT} - Create rectangular highlight annotations</li>
 *   <li>{@link AnnotationMode#DRAW} - Freehand drawing (planned)</li>
 *   <li>{@link AnnotationMode#TEXT} - Text annotations (planned)</li>
 *   <li>{@link AnnotationMode#SHAPE} - Shape annotations (planned)</li>
 * </ul>
 * </p>
 * <p>
 * The layer handles mouse events to create annotations interactively. When in
 * HIGHLIGHT mode, users can click and drag to select a rectangular area which
 * becomes a semi-transparent highlight annotation.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 * @see Annotation
 * @see HighlightAnnotation
 */
public class AnnotationLayer extends Canvas {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationLayer.class);
    
    /**
     * List of annotations currently on this layer.
     */
    private final List<Annotation> annotations = new ArrayList<>();

    /**
     * The current annotation mode.
     */
    private AnnotationMode currentMode = AnnotationMode.NONE;

    /**
     * The current color to use for new annotations.
     */
    private Color currentColor = Color.YELLOW;

    /**
     * Starting X coordinate for drag operations.
     */
    private double startX, startY;

    /**
     * Flag indicating whether a draw operation is in progress.
     */
    private boolean isDrawing = false;

    private final List<SearchResult> searchHighlights = new ArrayList<>();
    private SearchResult activeSearchResult = null;
    private static final Color SEARCH_HIGHLIGHT_COLOR = Color.YELLOW;
    private static final Color ACTIVE_SEARCH_HIGHLIGHT_COLOR = Color.ORANGE;
    private static final double SEARCH_HIGHLIGHT_OPACITY = 0.4;
    private static final double ACTIVE_SEARCH_HIGHLIGHT_OPACITY = 0.6;
    private double scale = 1.0;

    /**
     * Creates a new annotation layer with the specified dimensions.
     * <p>
     * The dimensions should match the rendered PDF page image dimensions
     * to ensure proper alignment of annotations.
     * </p>
     *
     * @param width the width of the layer in pixels
     * @param height the height of the layer in pixels
     */
    public AnnotationLayer(double width, double height) {
        super(width, height);
        setupMouseHandlers();
        logger.debug("AnnotationLayer created: {}x{}", width, height);
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    /**
     * Sets up mouse event handlers for interactive annotation creation.
     * <p>
     * This method configures handlers for:
     * <ul>
     *   <li>Mouse pressed - Start annotation creation</li>
     *   <li>Mouse dragged - Preview annotation while dragging</li>
     *   <li>Mouse released - Finalize and store annotation</li>
     * </ul>
     * </p>
     */
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
                    case DRAW, TEXT:
                        break;
                }
                isDrawing = false;
                redraw();
            }
        });
    }

    /**
     * Adds a highlight annotation to the layer.
     * <p>
     * This method creates a {@link HighlightAnnotation} from the given coordinates
     * and adds it to the annotations list. The coordinates are normalized so that
     * (x, y) represents the top-left corner. Highlights with dimensions smaller
     * than 5x5 pixels are ignored to prevent accidental tiny highlights.
     * </p>
     *
     * @param x1 the X coordinate of the first corner
     * @param y1 the Y coordinate of the first corner
     * @param x2 the X coordinate of the opposite corner
     * @param y2 the Y coordinate of the opposite corner
     */
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

    /**
     * Redraws all annotations on the canvas.
     * <p>
     * This method clears the canvas and then redraws all stored annotations.
     * It should be called whenever the annotation list changes or when the
     * layer needs to be refreshed.
     * </p>
     */
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

    /**
     * Creates a new Color with the specified alpha (opacity) value.
     * <p>
     * This utility method is used to create semi-transparent colors for
     * annotation rendering.
     * </p>
     *
     * @param color the base color
     * @param alpha the opacity value (0.0 = fully transparent, 1.0 = fully opaque)
     * @return a new Color with the specified alpha value
     */
    private Color getColorWithAlpha(Color color, double alpha) {
        return Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    /**
     * Sets the current annotation mode.
     * <p>
     * This determines what type of annotation will be created when the user
     * interacts with the layer. Set to {@link AnnotationMode#NONE} to disable
     * annotation creation.
     * </p>
     *
     * @param mode the annotation mode to set
     */
    public void setAnnotationMode(AnnotationMode mode) {
        this.currentMode = mode;
        logger.debug("Annotation mode set to: {}", mode);
    }

    /**
     * Gets the current annotation mode.
     *
     * @return the current AnnotationMode
     */
    public AnnotationMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Sets the color to use for new highlight annotations.
     *
     * @param color the color to set
     */
    public void setHighlightColor(Color color) {
        this.currentColor = color;
    }

    /**
     * Clears all annotations from this layer and redraws.
     * <p>
     * This permanently removes all annotations. The operation cannot be undone.
     * </p>
     */
    public void clearAnnotations() {
        annotations.clear();
        redraw();
    }

    /**
     * Gets a copy of all annotations on this layer.
     * <p>
     * Returns a new list to prevent external modification of the internal
     * annotations list.
     * </p>
     *
     * @return a new list containing all annotations
     */
    public List<Annotation> getAnnotations() {
        return new ArrayList<>(annotations);
    }

    /**
     * Enumeration of available annotation modes.
     * <p>
     * Each mode determines what type of annotation will be created when
     * the user interacts with the annotation layer.
     * </p>
     */
    public enum AnnotationMode {
        /**
         * No annotation functionality - clicks are ignored.
         */
        NONE,

        /**
         * Create rectangular highlight annotations by clicking and dragging.
         */
        HIGHLIGHT,

        /**
         * Freehand drawing mode (not yet implemented).
         */
        DRAW,

        /**
         * Text annotation mode (not yet implemented).
         */
        TEXT,

        /**
         * Shape annotation mode (not yet implemented).
         */
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
                    result.pageNumber(), result.startIndex(), result.endIndex(),
                    result.x(), result.y());
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
            if (result.width() <= 0 || result.height() <= 0) {
                logger.warn("Invalid coordinates for search result: {}", result);
                continue;
            }
            
            boolean isActive = (result.equals(activeSearchResult));

            Color highlightColor = isActive ? ACTIVE_SEARCH_HIGHLIGHT_COLOR : SEARCH_HIGHLIGHT_COLOR;
            double opacity = isActive ? ACTIVE_SEARCH_HIGHLIGHT_OPACITY : SEARCH_HIGHLIGHT_OPACITY;

            gc.setFill(Color.color(
                    highlightColor.getRed(),
                    highlightColor.getGreen(),
                    highlightColor.getBlue(),
                    opacity
            ));

            double finalScale = this.scale * LOW_RENDER_SCALE;
            double x = result.x() * finalScale;
            double y = result.y() * finalScale;
            double width = result.width() * finalScale;
            double height = result.height() * finalScale;

            gc.fillRect(x, y, width, height);

            if (isActive) {
                gc.setStroke(Color.DARKORANGE);
                gc.setLineWidth(2);
                gc.strokeRect(x, y, width, height);
                activeCount++;
                
                logger.trace("Drew ACTIVE highlight at ({}, {}) size {}x{} - page={}, start={}",
                        x, y, width, height, result.pageNumber(), result.startIndex());
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