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

    public AnnotationLayer() {
        super();
        setupMouseHandlers();
    }

    public AnnotationLayer(double width, double height) {
        super(width, height);
        setupMouseHandlers();
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

        // Draw all annotations
        for (Annotation annotation : annotations) {
            if (annotation instanceof HighlightAnnotation highlight) {
                gc.setFill(getColorWithAlpha(highlight.getColor(), 0.4));
                gc.fillRect(highlight.getX(), highlight.getY(),
                           highlight.getWidth(), highlight.getHeight());
            }
        }
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
}
