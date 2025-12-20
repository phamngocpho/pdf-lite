package org.pdflite.view;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.pdflite.model.Annotation;
import org.pdflite.model.ArrowAnnotation;
import org.pdflite.model.CircleAnnotation;
import org.pdflite.model.HighlightAnnotation;
import org.pdflite.model.RectangleAnnotation;
import org.pdflite.model.SearchResult;
import org.pdflite.model.ShapeAnnotation;
import org.pdflite.util.Constants;
import static org.pdflite.util.Constants.LOW_RENDER_SCALE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;

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
 * @see Annotation
 * @see HighlightAnnotation
 * @since 1.0.0
 */
public class AnnotationLayer extends Canvas {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationLayer.class);

    private int pageIndex = 0;
    private Annotation tempAnnotation;
    private double currentLineWidth = 2.0;
    private Consumer<Annotation> onAnnotationAdded;

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
    private static final double SEARCH_HIGHLIGHT_OPACITY = Constants.SEARCH_HIGHLIGHT_OPACITY;
    private static final double ACTIVE_SEARCH_HIGHLIGHT_OPACITY = 0.6;
    private double scale = 1.0;
    private int pageNumber;

    // Text region highlighting
    private final List<TextRegionHighlight> textRegionHighlights = new ArrayList<>();
    private TextRegionHighlight selectedTextRegion = null;
    private static final Color TEXT_REGION_HIGHLIGHT_COLOR = Color.LIGHTBLUE;
    private static final Color SELECTED_TEXT_REGION_COLOR = Color.CYAN;
    private static final double TEXT_REGION_OPACITY = 0.3;
    private static final double SELECTED_TEXT_REGION_OPACITY = 0.5;

    /**
     * Record for storing text region highlight information.
     */
    public record TextRegionHighlight(double x, double y, double width, double height, String text) {}

    /**
     * Creates a new annotation layer with the specified dimensions.
     * <p>
     * The dimensions should match the rendered PDF page image dimensions
     * to ensure proper alignment of annotations.
     * </p>
     * <p>
     * Canvas dimensions are clamped to MAX_CANVAS_SIZE to prevent GPU texture overflow
     * which causes NullPointerException when creating GraphicsContext.
     * </p>
     *
     * @param width  the width of the layer in pixels
     * @param height the height of the layer in pixels
     */
    public AnnotationLayer(double width, double height) {
        // Clamp dimensions to prevent GPU texture overflow
        // Must call super() first for Java 21 compatibility
        super(Math.min(width, Constants.MAX_CANVAS_SIZE), Math.min(height, Constants.MAX_CANVAS_SIZE));
        
        // Check if clamping occurred and log warning
        double clampedWidth = Math.min(width, Constants.MAX_CANVAS_SIZE);
        double clampedHeight = Math.min(height, Constants.MAX_CANVAS_SIZE);
        if (width != clampedWidth || height != clampedHeight) {
            logger.warn("AnnotationLayer dimensions {}x{} clamped to {}x{} to prevent GPU texture overflow",
                    width, height, clampedWidth, clampedHeight);
        }
        
        setupMouseHandlers();
        logger.debug("AnnotationLayer created: {}x{}", clampedWidth, clampedHeight);
    }

    public void setPageIndex(int index) {
        this.pageIndex = index;
    }

    public void setOnAnnotationAdded(Consumer<Annotation> callback) {
        this.onAnnotationAdded = callback;
    }

    public void setLineWidth(double width) {
        this.currentLineWidth = width;
    }

    // Hàm này dùng chung để set màu cho cả Highlight và Vẽ hình
    public void setDrawingColor(Color color) {
        this.currentColor = color;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    // Nếu chưa có getter thì thêm luôn cho chắc
    public int getPageNumber() {
        return pageNumber;
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
            if (currentMode != AnnotationMode.NONE) {

                // --- BẮT ĐẦU FIX LỖI XUNG ĐỘT ---
                // Nếu bấm chuột chính (trái), BẮT ĐẦU VẼ và NUỐT (Consume) sự kiện
                if (event.getButton() == MouseButton.PRIMARY) {
                    startX = event.getX();
                    startY = event.getY();
                    isDrawing = true;
                    event.consume(); // Rất quan trọng để chặn ContextMenuPane
                }
                // Nếu bấm chuột phụ (phải), HỦY chế độ vẽ hiện tại
                else if (event.getButton() == MouseButton.SECONDARY) {
                    setAnnotationMode(AnnotationMode.NONE); // Trở về View Mode
                    event.consume(); // Nuốt sự kiện để không bật ContextMenu
                }
                // --- KẾT THÚC FIX LỖI XUNG ĐỘT ---
            }
        });

        setOnMouseDragged(event -> {
            if (!isDrawing) return;

            double mStartX = startX / scale;
            double mStartY = startY / scale;
            double mEndX = event.getX() / scale;
            double mEndY = event.getY() / scale;

            if (currentMode == AnnotationMode.HIGHLIGHT) {
                redraw();
                try {
                    GraphicsContext gc = getGraphicsContext2D();
                    if (gc == null) {
                        logger.warn("Cannot draw highlight - GraphicsContext is null (canvas too large)");
                        return;
                    }
                    gc.setFill(getColorWithAlpha(currentColor, 0.4));
                    double x = Math.min(startX, event.getX());
                    double y = Math.min(startY, event.getY());
                    double w = Math.abs(event.getX() - startX);
                    double h = Math.abs(event.getY() - startY);
                    gc.fillRect(x, y, w, h);
                } catch (NullPointerException e) {
                    logger.warn("Cannot draw highlight - canvas too large", e);
                }
            } else {
                switch (currentMode) {
                    case RECTANGLE:
                        tempAnnotation = new RectangleAnnotation(pageIndex, mStartX, mStartY, mEndX, mEndY, currentColor, currentLineWidth);
                        break;
                    case CIRCLE:
                        tempAnnotation = new CircleAnnotation(pageIndex, mStartX, mStartY, mEndX, mEndY, currentColor, currentLineWidth);
                        break;
                    case ARROW:
                        tempAnnotation = new ArrowAnnotation(pageIndex, mStartX, mStartY, mEndX, mEndY, currentColor, currentLineWidth);
                        break;
                }
                redraw();
            }
        });

        setOnMouseReleased(event -> {
            if (isDrawing && event.getButton() == MouseButton.PRIMARY) {

                if (currentMode == AnnotationMode.HIGHLIGHT) {
                    addHighlight(startX, startY, event.getX(), event.getY());
                } else if (tempAnnotation != null) {
                    annotations.add(tempAnnotation); // Lưu vào danh sách
                    if (onAnnotationAdded != null) {
                        onAnnotationAdded.accept(tempAnnotation);
                    }
                }

                isDrawing = false;
                tempAnnotation = null; // Xóa hình tạm
                redraw();
            }
        });

        //nạp lại hình cũ khi cuộn trang
    }

    public void setAnnotations(List<Annotation> loadedAnnotations) {
        this.annotations.clear();
        if (loadedAnnotations != null) {
            this.annotations.addAll(loadedAnnotations);
        }
        redraw();
    }

    /**
     * Adds a highlight annotation to the layer.
     * <p>
     * This method creates a {@link HighlightAnnotation} from the given coordinates
     * and adds it to the annotation list. The coordinates are normalized so that
     * (x, y) represent the top-left corner. Highlights with dimensions smaller
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
            HighlightAnnotation annotation = new HighlightAnnotation(pageIndex, x, y, width, height, currentColor);
            annotations.add(annotation);
            logger.debug("Added highlight annotation at ({}, {}) with size {}x{} on page {}", 
                    x, y, width, height, pageIndex);
        }
    }

    /**
     * Redraws all annotations on the canvas.
     * <p>
     * This method clears the canvas and then redraws all stored annotations.
     * It should be called whenever the annotation list changes or when the
     * layer needs to be refreshed.
     * </p>
     * <p>
     * Handles GPU texture overflow gracefully by catching NullPointerException
     * when GraphicsContext cannot be created (canvas too large).
     * </p>
     */
    public void redraw() {
        try {
            GraphicsContext gc = getGraphicsContext2D();
            if (gc == null) {
                logger.error("Failed to get GraphicsContext2D - canvas may be too large ({}x{})",
                        getWidth(), getHeight());
                return;
            }
            
            gc.clearRect(0, 0, getWidth(), getHeight());
            drawSearchHighlights(gc);
            drawTextRegionHighlights(gc);
            for (Annotation annotation : annotations) {
                if (annotation instanceof HighlightAnnotation highlight) {
                    gc.setFill(getColorWithAlpha(highlight.getColor(), 0.4));
                    gc.fillRect(highlight.getX(), highlight.getY(),
                            highlight.getWidth(), highlight.getHeight());
                } else if (annotation instanceof ShapeAnnotation shape) {
                    shape.draw(gc, scale);
                }
            }

            if (tempAnnotation instanceof ShapeAnnotation shapeTemp) {
                gc.setLineDashes(5); // Nét đứt
                shapeTemp.draw(gc, scale);
                gc.setLineDashes(0); // Reset về nét liền
            }
        } catch (NullPointerException e) {
            logger.error("NullPointerException in redraw() - canvas too large ({}x{}). " +
                    "This usually happens when canvas exceeds GPU texture limit.",
                    getWidth(), getHeight(), e);
        } catch (Exception e) {
            logger.error("Error redrawing annotations", e);
        }
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
         * Draw shape
         */
        RECTANGLE,
        CIRCLE,
        ARROW,

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
        SHAPE,

        /**
         * Text selection and editing mode.
         * Allows users to click on text regions to select and edit them.
         */
        TEXT_SELECT_EDIT
    }

    // ==================== TEXT REGION HIGHLIGHTS ====================

    /**
     * Sets the text region highlights for this layer.
     *
     * @param regions the list of text region highlights
     */
    public void setTextRegionHighlights(List<TextRegionHighlight> regions) {
        this.textRegionHighlights.clear();
        if (regions != null) {
            this.textRegionHighlights.addAll(regions);
        }
        redraw();
        logger.debug("Set {} text region highlights", textRegionHighlights.size());
    }

    /**
     * Sets the selected text region.
     *
     * @param region the selected text region, or null to clear selection
     */
    public void setSelectedTextRegion(TextRegionHighlight region) {
        this.selectedTextRegion = region;
        redraw();
        if (region != null) {
            logger.debug("Selected text region: '{}'", region.text());
        } else {
            logger.debug("Cleared text region selection");
        }
    }

    /**
     * Gets the selected text region.
     *
     * @return the selected text region, or null if none selected
     */
    public TextRegionHighlight getSelectedTextRegion() {
        return selectedTextRegion;
    }

    /**
     * Clears all text region highlights.
     */
    public void clearTextRegionHighlights() {
        this.textRegionHighlights.clear();
        this.selectedTextRegion = null;
        redraw();
        logger.debug("Cleared text region highlights");
    }

    /**
     * Finds a text region at the specified coordinates.
     *
     * @param x the X coordinate
     * @param y the Y coordinate
     * @return the text region at the coordinates, or null if none found
     */
    public TextRegionHighlight findTextRegionAt(double x, double y) {
        for (TextRegionHighlight region : textRegionHighlights) {
            if (x >= region.x() && x <= (region.x() + region.width()) &&
                y >= region.y() && y <= (region.y() + region.height())) {
                return region;
            }
        }
        return null;
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
        if (searchHighlights.isEmpty() || gc == null) {
            return;
        }

        int normalCount = 0;
        int activeCount = 0;

        try {
            gc.save();

            double canvasWidth = getWidth();
            double canvasHeight = getHeight();

            logger.trace("Drawing {} highlights on canvas {}x{} with scale={}",
                    searchHighlights.size(), canvasWidth, canvasHeight, scale);

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
        } catch (Exception e) {
            logger.error("Error drawing search highlights", e);
        }

        if (activeCount > 1) {
            logger.warn("Multiple active highlights detected! Count: {}", activeCount);
        }

        logger.trace("Drew {} normal + {} active highlights", normalCount, activeCount);
    }

    /**
     * Draws text region highlights on the canvas.
     *
     * @param gc the graphics context
     */
    private void drawTextRegionHighlights(GraphicsContext gc) {
        if (textRegionHighlights.isEmpty() || gc == null) {
            return;
        }

        try {
            gc.save();

            for (TextRegionHighlight region : textRegionHighlights) {
                boolean isSelected = region.equals(selectedTextRegion);

                Color highlightColor = isSelected ? SELECTED_TEXT_REGION_COLOR : TEXT_REGION_HIGHLIGHT_COLOR;
                double opacity = isSelected ? SELECTED_TEXT_REGION_OPACITY : TEXT_REGION_OPACITY;

                gc.setFill(Color.color(
                        highlightColor.getRed(),
                        highlightColor.getGreen(),
                        highlightColor.getBlue(),
                        opacity
                ));

                gc.fillRect(region.x(), region.y(), region.width(), region.height());

                if (isSelected) {
                    gc.setStroke(Color.BLUE);
                    gc.setLineWidth(2);
                    gc.strokeRect(region.x(), region.y(), region.width(), region.height());
                }
            }

            gc.restore();
        } catch (Exception e) {
            logger.error("Error drawing text region highlights", e);
        }
    }

    // ==================== ANNOTATION MANAGEMENT ====================

    /**
     * Finds an annotation at the specified coordinates using hit detection.
     *
     * @param x the X coordinate
     * @param y the Y coordinate
     * @return the annotation at the coordinates, or null if none found
     */
    public Annotation findAnnotationAt(double x, double y) {
        // Search in reverse order (top annotation first)
        for (int i = annotations.size() - 1; i >= 0; i--) {
            Annotation annotation = annotations.get(i);
            
            if (annotation instanceof HighlightAnnotation highlight) {
                // Check if point is within highlight bounds
                if (x >= highlight.getX() && x <= (highlight.getX() + highlight.getWidth()) &&
                    y >= highlight.getY() && y <= (highlight.getY() + highlight.getHeight())) {
                    return annotation;
                }
            } else if (annotation instanceof ShapeAnnotation shape) {
                // For shapes, check if point is within bounding box
                double minX = Math.min(shape.getX(), shape.getEndX()) * scale;
                double maxX = Math.max(shape.getX(), shape.getEndX()) * scale;
                double minY = Math.min(shape.getY(), shape.getEndY()) * scale;
                double maxY = Math.max(shape.getY(), shape.getEndY()) * scale;
                
                // Add some tolerance for easier selection
                double tolerance = 10;
                if (x >= (minX - tolerance) && x <= (maxX + tolerance) &&
                    y >= (minY - tolerance) && y <= (maxY + tolerance)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    /**
     * Removes an annotation from the layer.
     *
     * @param annotation the annotation to remove
     * @return true if the annotation was removed, false otherwise
     */
    public boolean removeAnnotation(Annotation annotation) {
        boolean removed = annotations.remove(annotation);
        if (removed) {
            redraw();
            logger.debug("Removed annotation: {}", annotation);
        }
        return removed;
    }

    /**
     * Updates the color of a HighlightAnnotation.
     *
     * @param annotation the annotation to update
     * @param newColor the new color
     * @return true if the color was updated, false otherwise
     */
    public boolean updateAnnotationColor(Annotation annotation, Color newColor) {
        if (annotation instanceof HighlightAnnotation highlight) {
            // Find the annotation in the list
            int index = annotations.indexOf(annotation);
            if (index >= 0) {
                // Create new annotation with updated color
                HighlightAnnotation updated = new HighlightAnnotation(
                    highlight.getPageNumber(),
                    highlight.getX(),
                    highlight.getY(),
                    highlight.getWidth(),
                    highlight.getHeight(),
                    newColor
                );
                
                // Replace in list
                annotations.set(index, updated);
                redraw();
                logger.debug("Updated annotation color to: {}", newColor);
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the number of annotations on this layer.
     *
     * @return the number of annotations
     */
    public int getAnnotationCount() {
        return annotations.size();
    }

    /**
     * Checks if the layer contains the specified annotation.
     *
     * @param annotation the annotation to check
     * @return true if the annotation is in the layer, false otherwise
     */
    public boolean containsAnnotation(Annotation annotation) {
        return annotations.contains(annotation);
    }
}
