package org.pdflite.manager;

import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import org.pdflite.controller.PageRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages drawing tools setup and configuration.
 */
public class DrawingToolsSetupManager {

    private static final Logger logger = LoggerFactory.getLogger(DrawingToolsSetupManager.class);

    private final PageRenderer pageRenderer;

    // Callbacks for updating styles
    private Runnable updateDrawingStyleCallback;

    public DrawingToolsSetupManager(PageRenderer pageRenderer, UIStateManager uiStateManager) {
        this.pageRenderer = pageRenderer;
    }

    /**
     * Sets up drawing tool selection listeners.
     */
    public void setupDrawingToolSelection(ToggleGroup drawingToolsGroup,
                                          ToggleButton btnDrawRect,
                                          ToggleButton btnDrawCircle,
                                          ToggleButton btnDrawArrow,
                                          VBox pagesContainer,
                                          AnnotationManager annotationManager) {
        if (drawingToolsGroup == null) {
            return;
        }

        drawingToolsGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            ToggleButton selectedBtn = (newVal != null) ? (ToggleButton) newVal : null;

            // Handle tool selection - annotationManager will be null until the document is opened
            if (annotationManager == null) {
                // If no document is open, just handle basic selection mode
                if (selectedBtn == null) {
                    // No tool selected - enable text selection by default
                    if (pageRenderer != null && pagesContainer != null) {
                        pageRenderer.setSelectionModeActive(pagesContainer, true);
                    }
                } else {
                    // Drawing tool selected - disable text selection
                    if (pageRenderer != null && pagesContainer != null) {
                        pageRenderer.setSelectionModeActive(pagesContainer, false);
                    }
                }
                return;
            }

            // Document is open - use annotation manager
            annotationManager.handleToolSelection(
                    selectedBtn,
                    btnDrawRect,
                    btnDrawCircle,
                    btnDrawArrow,
                    active -> {
                        if (pageRenderer != null && pagesContainer != null) {
                            pageRenderer.setSelectionModeActive(pagesContainer, active);
                        }
                    },
                    pagesContainer,
                    updateDrawingStyleCallback
            );
        });

        logger.info("Drawing tool selection listeners configured");
    }

    /**
     * Makes toggle buttons deselectable.
     */
    public void makeToggleButtonsDeselectable(ToggleButton btnDrawRect,
                                              ToggleButton btnDrawCircle,
                                              ToggleButton btnDrawArrow,
                                              ToggleGroup drawingToolsGroup,
                                              AnnotationManager annotationManager) {
        if (annotationManager != null && drawingToolsGroup != null) {
            if (btnDrawRect != null) {
                annotationManager.makeToggleButtonDeselectable(btnDrawRect, drawingToolsGroup);
            }
            if (btnDrawCircle != null) {
                annotationManager.makeToggleButtonDeselectable(btnDrawCircle, drawingToolsGroup);
            }
            if (btnDrawArrow != null) {
                annotationManager.makeToggleButtonDeselectable(btnDrawArrow, drawingToolsGroup);
            }
        }
    }

    /**
     * Sets up color picker for drawing tools.
     */
    public void setupColorPicker(ColorPicker colorPicker, Runnable updateCallback) {
        if (colorPicker == null) {
            return;
        }

        colorPicker.setValue(javafx.scene.paint.Color.WHITE);
        colorPicker.setOnAction(e -> {
            if (updateCallback != null) {
                updateCallback.run();
            }
        });

        // Sync initial color to annotation layers
        if (updateCallback != null) {
            updateCallback.run();
        }
    }

    /**
     * Sets up highlight color picker.
     */
    public void setupHighlightColorPicker(ColorPicker highlightColorPicker, Runnable updateCallback) {
        if (highlightColorPicker == null) {
            return;
        }

        highlightColorPicker.setValue(javafx.scene.paint.Color.YELLOW);
        highlightColorPicker.setOnAction(e -> {
            if (updateCallback != null) {
                updateCallback.run();
            }
        });

        // Sync initial color to annotation layers
        if (updateCallback != null) {
            updateCallback.run();
        }
    }

    /**
     * Sets up stroke width slider.
     */
    public void setupStrokeWidthSlider(Slider strokeWidthSlider,
                                       Label strokeWidthLabel,
                                       Runnable updateCallback) {
        if (strokeWidthSlider == null) {
            return;
        }

        strokeWidthSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (updateCallback != null) {
                updateCallback.run();
            }
            if (strokeWidthLabel != null) {
                strokeWidthLabel.setText(String.format("%.0f", newVal.doubleValue()));
            }
        });

        // Initialize label with current value
        if (strokeWidthLabel != null) {
            strokeWidthLabel.setText(String.format("%.0f", strokeWidthSlider.getValue()));
        }

        logger.info("Stroke width slider configured");
    }

    /**
     * Sets the callback for updating drawing style.
     */
    public void setUpdateDrawingStyleCallback(Runnable callback) {
        this.updateDrawingStyleCallback = callback;
    }

    /**
     * Sets the callback for updating highlight color.
     */
    public void setUpdateHighlightColorCallback(Runnable callback) {
    }
}
