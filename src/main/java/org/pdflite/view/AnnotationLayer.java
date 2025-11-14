// src/main/java/org/pdflite/view/AnnotationLayer.java

package org.pdflite.view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import org.pdflite.manager.DrawingManager; // Thêm import
import org.pdflite.model.Annotation;
import org.pdflite.model.HighlightAnnotation;
// Thêm các import model
import org.pdflite.model.ShapeAnnotation;
import org.pdflite.model.RectangleAnnotation;
import org.pdflite.model.CircleAnnotation;
import org.pdflite.model.ArrowAnnotation;
import org.pdflite.model.DrawingTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import org.pdflite.model.SearchResult;
import static org.pdflite.util.Constants.LOW_RENDER_SCALE;

public class AnnotationLayer extends Canvas {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationLayer.class);

    private final List<Annotation> annotations = new ArrayList<>();
    private AnnotationMode currentMode = AnnotationMode.NONE;

    // GIỮ NGUYÊN 'currentColor' cho logic HIGHLIGHT cũ của bạn
    private Color currentColor = Color.YELLOW;

    // Tọa độ vẽ
    private double startX, startY;
    private double currentX, currentY; // Dùng cho live-preview SHAPE
    private boolean isDrawing = false;

    // === CÁC BIẾN MỚI ===
    private DrawingManager drawingManager;
    private int pageNumber = 0; // Số trang của layer này

    // (Các biến Search của bạn)
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

    // === CÁC HÀM SETTER MỚI ===
    /**
     * [QUAN TRỌNG] Inject DrawingManager từ MainController.
     */
    public void setDrawingManager(DrawingManager drawingManager) {
        this.drawingManager = drawingManager;
    }

    /**
     * [QUAN TRỌNG] Set số trang cho lớp này.
     * Cần gọi khi tạo AnnotationLayer (trong MainController).
     */
    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    /**
     * Cập nhật setupMouseHandlers để xử lý cả HIGHLIGHT và SHAPE
     */
    private void setupMouseHandlers() {
        setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;

            if (currentMode == AnnotationMode.HIGHLIGHT) {
                // Logic HIGHLIGHT (Giữ nguyên)
                startX = event.getX();
                startY = event.getY();
                isDrawing = true;
            } else if (currentMode == AnnotationMode.SHAPE && drawingManager != null
                    && drawingManager.getCurrentTool() != DrawingTool.NONE) {
                // Logic SHAPE (Mới)
                startX = event.getX();
                startY = event.getY();
                currentX = startX;
                currentY = startY;
                isDrawing = true;
            }
        });

        setOnMouseDragged(event -> {
            if (!isDrawing) return;

            if (currentMode == AnnotationMode.HIGHLIGHT) {
                // Logic HIGHLIGHT (Giữ nguyên)
                redraw(); // Xóa và vẽ lại
                GraphicsContext gc = getGraphicsContext2D();
                gc.setFill(getColorWithAlpha(currentColor, 0.4));
                double x = Math.min(startX, event.getX());
                double y = Math.min(startY, event.getY());
                double w = Math.abs(event.getX() - startX);
                double h = Math.abs(event.getY() - startY);
                gc.fillRect(x, y, w, h);
            } else if (currentMode == AnnotationMode.SHAPE) {
                // Logic SHAPE (Mới)
                currentX = event.getX();
                currentY = event.getY();
                redraw(); // Xóa và vẽ lại các annotation đã lưu
                drawPreview(getGraphicsContext2D()); // Vẽ hình preview
            }
        });

        setOnMouseReleased(event -> {
            if (!isDrawing || event.getButton() != MouseButton.PRIMARY) return;

            if (currentMode == AnnotationMode.HIGHLIGHT) {
                // Logic HIGHLIGHT (Giữ nguyên)
                addHighlight(startX, startY, event.getX(), event.getY());
            } else if (currentMode == AnnotationMode.SHAPE) {
                // Logic SHAPE (Mới)
                addShapeAnnotation(startX, startY, event.getX(), event.getY());
            }

            isDrawing = false;
            redraw(); // Vẽ lại lần cuối
        });
    }

    /**
     * Thêm HighlightAnnotation (Code gốc của bạn - Giữ nguyên)
     * [SỬA ĐỔI NHỎ] Dùng 'this.pageNumber' thay vì 0
     */
    private void addHighlight(double x1, double y1, double x2, double y2) {
        double x = Math.min(x1, x2);
        double y = Math.min(y1, y2);
        double width = Math.abs(x2 - x1);
        double height = Math.abs(y2 - y1);

        if (width > 5 && height > 5) {
            // Sửa 0 thành this.pageNumber
            HighlightAnnotation annotation = new HighlightAnnotation(this.pageNumber, x, y, width, height, currentColor);
            annotations.add(annotation);
            logger.debug("Added highlight (Page {}) at ({}, {})", this.pageNumber, x, y);
        }
    }

    /**
     * [HÀM MỚI] Thêm ShapeAnnotation (Rectangle, Circle, Arrow)
     */
    private void addShapeAnnotation(double x1, double y1, double x2, double y2) {
        if (drawingManager == null) return;

        DrawingTool tool = drawingManager.getCurrentTool();
        String colorStr = drawingManager.getCurrentColorAsWebString();
        double lineWidth = drawingManager.getCurrentLineWidth();

        // Chuẩn hóa tọa độ
        double x = Math.min(x1, x2);
        double y = Math.min(y1, y2);
        double w = Math.abs(x2 - x1);
        double h = Math.abs(y2 - y1);

        // Bỏ qua nếu quá nhỏ (trừ Arrow)
        if (w < 3 && h < 3 && tool != DrawingTool.ARROW) {
            return;
        }

        switch (tool) {
            case RECTANGLE:
                RectangleAnnotation rect = new RectangleAnnotation(this.pageNumber, x, y, w, h, colorStr, lineWidth);
                annotations.add(rect);
                logger.debug("Added Rectangle (Page {})", this.pageNumber);
                break;

            case CIRCLE:
                double centerX = x + w / 2;
                double centerY = y + h / 2;
                double radius = Math.max(w, h) / 2;
                CircleAnnotation circle = new CircleAnnotation(this.pageNumber, centerX, centerY, radius, colorStr, lineWidth);
                annotations.add(circle);
                logger.debug("Added Circle (Page {})", this.pageNumber);
                break;

            case ARROW:
                ArrowAnnotation arrow = new ArrowAnnotation(this.pageNumber, x1, y1, x2, y2, colorStr, lineWidth);
                annotations.add(arrow);
                logger.debug("Added Arrow (Page {})", this.pageNumber);
                break;
            default:
                break;
        }
    }

    /**
     * [HÀM MỚI] Vẽ hình dạng xem trước (preview) khi kéo chuột.
     */
    private void drawPreview(GraphicsContext gc) {
        if (drawingManager == null) return;

        DrawingTool tool = drawingManager.getCurrentTool();
        Color color = drawingManager.getCurrentColor();
        double lineWidth = drawingManager.getCurrentLineWidth();

        gc.setStroke(color);
        gc.setLineWidth(lineWidth);

        double x = Math.min(startX, currentX);
        double y = Math.min(startY, currentY);
        double w = Math.abs(currentX - startX);
        double h = Math.abs(currentY - startY);

        switch (tool) {
            case RECTANGLE:
                gc.strokeRect(x, y, w, h);
                break;
            case CIRCLE:
                double centerX = x + w / 2;
                double centerY = y + h / 2;
                double radius = Math.max(w, h) / 2;
                gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
                break;
            case ARROW:
                gc.strokeLine(startX, startY, currentX, currentY);
                drawArrowhead(gc, startX, startY, currentX, currentY, 10);
                break;
            default:
                break;
        }
    }

    /**
     * Redraws tất cả các annotations (ĐÃ NÂNG CẤP)
     */
    public void redraw() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());

        // Vẽ tất cả annotations đã lưu
        for (Annotation annotation : annotations) {
            if (annotation instanceof HighlightAnnotation highlight) {
                // Logic HIGHLIGHT (Giữ nguyên)
                gc.setFill(getColorWithAlpha(highlight.getColor(), 0.4));
                gc.fillRect(highlight.getX(), highlight.getY(),
                        highlight.getWidth(), highlight.getHeight());
            }
            else if (annotation instanceof ShapeAnnotation shape) {
                // Logic SHAPE (Mới)
                Color color = Color.web(shape.getColor());
                double lineWidth = shape.getLineWidth();
                gc.setStroke(color);
                gc.setLineWidth(lineWidth);

                if (shape instanceof RectangleAnnotation rect) {
                    gc.strokeRect(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());

                } else if (shape instanceof CircleAnnotation circle) {
                    double radius = circle.getRadius();
                    gc.strokeOval(circle.getCenterX() - radius, circle.getCenterY() - radius, radius * 2, radius * 2);

                } else if (shape instanceof ArrowAnnotation arrow) {
                    gc.strokeLine(arrow.getStartX(), arrow.getStartY(), arrow.getEndX(), arrow.getEndY());
                    drawArrowhead(gc, arrow.getStartX(), arrow.getStartY(), arrow.getEndX(), arrow.getEndY(), 10);
                }
            }
        }

        // Vẽ search highlights (Giữ nguyên)
        drawSearchHighlights(gc);
    }

    /**
     * [HÀM MỚI] Tiện ích vẽ đầu mũi tên.
     */
    private void drawArrowhead(GraphicsContext gc, double x1, double y1, double x2, double y2, double arrowSize) {
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);

        // Điểm P1
        double x3 = x2 - arrowSize * cos + arrowSize / 2 * sin;
        double y3 = y2 - arrowSize * sin - arrowSize / 2 * cos;

        // Điểm P2
        double x4 = x2 - arrowSize * cos - arrowSize / 2 * sin;
        double y4 = y2 - arrowSize * sin + arrowSize / 2 * cos;

        // Dùng fill() để vẽ tam giác đặc
        gc.setFill(gc.getStroke());
        gc.fillPolygon(new double[]{x2, x3, x4}, new double[]{y2, y3, y4}, 3);
    }


    // (Các hàm còn lại của bạn giữ nguyên)

    private Color getColorWithAlpha(Color color, double alpha) {
        return Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public void setAnnotationMode(AnnotationMode mode) {
        this.currentMode = mode;
        logger.debug("Annotation mode (Page {}) set to: {}", this.pageNumber, mode);
    }

    public AnnotationMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Hàm này giờ chỉ dùng cho HIGHLIGHT (logic cũ)
     */
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

    /**
     * Enum gốc của bạn (Giữ nguyên)
     */
    public enum AnnotationMode {
        NONE,
        HIGHLIGHT,
        DRAW,
        TEXT,
        SHAPE
    }

    // ==================== SEARCH HIGHLIGHTS (Giữ nguyên) ====================
    //<editor-fold desc="Search Highlight (Giữ nguyên code của bạn)">
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
    //</editor-fold>
}