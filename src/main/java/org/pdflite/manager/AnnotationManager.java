package org.pdflite.manager;

import java.util.List;
import java.util.function.Consumer;

import org.pdflite.model.Annotation;
import org.pdflite.model.PDFDocument;
import org.pdflite.util.PageContainerUtils;
import org.pdflite.view.AnnotationLayer;

import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Manages annotation operations for PDF pages.
 * Handles annotation mode updates, drawing style updates, undo operations,
 * and toggle button deselection behavior.
 */
public record AnnotationManager(VBox pagesContainer, UIStateManager uiStateManager, PDFDocument currentDocument) {
    /**
     * Creates a new AnnotationManager.
     *
     * @param pagesContainer  the container holding all page boxes
     * @param uiStateManager  the UI state manager for status updates
     * @param currentDocument the current PDF document
     */
    public AnnotationManager {
    }

    /**
     * Updates annotation mode for all pages.
     *
     * @param mode the annotation mode to set
     */
    public void updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode mode) {
        if (pagesContainer == null) return;
        processAllAnnotationLayers(layer -> layer.setAnnotationMode(mode));
        uiStateManager.updateStatus("Tool: " + mode);
    }

    /**
     * Updates drawing style (color and stroke width) for all pages.
     *
     * @param color       the drawing color
     * @param strokeWidth the stroke width
     */
    public void updateDrawingStyleForAllPages(Color color, double strokeWidth) {
        if (pagesContainer == null) return;

        processAllAnnotationLayers(layer -> {
            layer.setDrawingColor(color);
            layer.setLineWidth(strokeWidth);
            layer.redraw();
        });

        uiStateManager.updateStatus("Drawing style updated");
    }

    /**
     * Updates highlight color for all pages.
     *
     * @param color the highlight color
     */
    public void updateHighlightColorForAllPages(Color color) {
        if (pagesContainer == null) return;

        processAllAnnotationLayers(layer -> {
            layer.setHighlightColor(color);
            layer.redraw();
        });

        uiStateManager.updateStatus("Highlight color updated");
    }

    /**
     * Makes a toggle button deselectable by clicking it again when selected.
     *
     * @param btn               the toggle button to make deselectable
     * @param drawingToolsGroup the toggle group containing the button
     */
    public void makeToggleButtonDeselectable(ToggleButton btn, ToggleGroup drawingToolsGroup) {
        btn.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (btn.isSelected()) {
                drawingToolsGroup.selectToggle(null);
                event.consume();
            }
        });
    }

    /**
     * Handles undo operation for annotations.
     */
    public void handleUndo() {
        if (currentDocument == null) return;

        List<Annotation> anns = currentDocument.getAnnotations();

        if (anns.isEmpty()) {
            uiStateManager.updateStatus("Nothing to undo");
            return;
        }

        int lastIndex = anns.size() - 1;
        Annotation lastAnn = anns.get(lastIndex);
        int pageIndexOfLastAnn = lastAnn.getPageNumber();
        anns.remove(lastIndex);

        refreshPageAnnotations(pageIndexOfLastAnn);

        uiStateManager.updateStatus("Undid last action");
    }

    /**
     * Refreshes annotations for a specific page.
     *
     * @param pageIndex the page index to refresh
     */
    public void refreshPageAnnotations(int pageIndex) {
        if (pagesContainer == null || currentDocument == null) return;

        VBox pageBox = PageContainerUtils.findPageBox(pagesContainer, pageIndex);
        if (pageBox == null || pageBox.getChildren().isEmpty()) return;

        if (pageBox.getChildren().get(0) instanceof StackPane stack) {
            stack.getChildren().stream()
                    .filter(node -> node instanceof AnnotationLayer)
                    .map(node -> (AnnotationLayer) node)
                    .findFirst()
                    .ifPresent(layer -> layer.setAnnotations(currentDocument.getAnnotationsForPage(pageIndex)));
        }
    }

    /**
     * Processes all annotation layers with the given action.
     *
     * @param action the action to perform on each annotation layer
     */
    private void processAllAnnotationLayers(Consumer<AnnotationLayer> action) {
        for (VBox pageBox : PageContainerUtils.collectPageBoxes(pagesContainer)) {
            if (pageBox == null || pageBox.getChildren().isEmpty()) continue;
            if (pageBox.getChildren().get(0) instanceof StackPane stack) {
                stack.getChildren().stream()
                        .filter(child -> child instanceof AnnotationLayer)
                        .map(child -> (AnnotationLayer) child)
                        .forEach(action);
            }
        }
    }

    /**
     * Handles tool selection change for annotation tools.
     * This method processes the selected tool and updates the annotation mode accordingly.
     *
     * @param selectedBtn        the selected toggle button (null if none selected)
     * @param btnDrawRect        the rectangle drawing toggle button
     * @param btnDrawCircle      the circle drawing toggle button
     * @param btnDrawArrow       the arrow drawing toggle button
     * @param setSelectionMode   callback to set text selection mode (pagesContainer, active)
     * @param pagesContainer     the page container
     * @param updateDrawingStyle callback to update drawing style for all pages
     */
    public void handleToolSelection(ToggleButton selectedBtn,
                                    ToggleButton btnDrawRect,
                                    ToggleButton btnDrawCircle,
                                    ToggleButton btnDrawArrow,
                                    Consumer<Boolean> setSelectionMode,
                                    VBox pagesContainer,
                                    Runnable updateDrawingStyle) {
        if (selectedBtn == null) {
            // No tool selected - enable text selection by default (like browsers)
            updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.NONE);
            if (setSelectionMode != null) {
                setSelectionMode.accept(true);
            }
            return;
        }

        // Drawing tool selected - disable text selection
        if (setSelectionMode != null) {
            setSelectionMode.accept(false);
        }

        // Map tool to annotation mode
        if (selectedBtn == btnDrawRect) {
            updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.RECTANGLE);
        } else if (selectedBtn == btnDrawCircle) {
            updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.CIRCLE);
        } else if (selectedBtn == btnDrawArrow) {
            updateAnnotationModeForAllPages(AnnotationLayer.AnnotationMode.ARROW);
        }

        if (updateDrawingStyle != null) {
            updateDrawingStyle.run();
        }
    }
}

