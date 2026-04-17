package org.pdflite.manager;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.pdflite.controller.PageRenderer;
import org.pdflite.model.AnnotationLineStyle;
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
                                          ToggleButton btnDrawFreehand,
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
                    btnDrawFreehand,
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
                                              ToggleButton btnDrawFreehand,
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
            if (btnDrawFreehand != null) {
                annotationManager.makeToggleButtonDeselectable(btnDrawFreehand, drawingToolsGroup);
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

        disableCustomColorDialog(colorPicker);
        colorPicker.setValue(parseColor(UserPreferencesManager.getInstance().getPreferences().getAnnotationDrawingColor(),
                javafx.scene.paint.Color.WHITE));
        colorPicker.setOnAction(e -> {
            javafx.scene.paint.Color selectedColor = colorPicker.getValue();
            if (selectedColor == null) {
                selectedColor = javafx.scene.paint.Color.WHITE;
                colorPicker.setValue(selectedColor);
            }
            UserPreferencesManager.getInstance().getPreferences()
                    .setAnnotationDrawingColor(toRgbaString(selectedColor));
            UserPreferencesManager.getInstance().savePreferences();
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

        disableCustomColorDialog(highlightColorPicker);
        highlightColorPicker.setValue(parseColor(UserPreferencesManager.getInstance().getPreferences().getAnnotationHighlightColor(),
                javafx.scene.paint.Color.YELLOW));
        highlightColorPicker.setOnAction(e -> {
            javafx.scene.paint.Color selectedColor = highlightColorPicker.getValue();
            if (selectedColor == null) {
                selectedColor = javafx.scene.paint.Color.YELLOW;
                highlightColorPicker.setValue(selectedColor);
            }
            UserPreferencesManager.getInstance().getPreferences()
                    .setAnnotationHighlightColor(toRgbaString(selectedColor));
            UserPreferencesManager.getInstance().savePreferences();
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

        strokeWidthSlider.setValue(UserPreferencesManager.getInstance().getPreferences().getAnnotationStrokeWidth());
        strokeWidthSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            UserPreferencesManager.getInstance().getPreferences().setAnnotationStrokeWidth(newVal.doubleValue());
            UserPreferencesManager.getInstance().savePreferences();
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

    public void setupLineStyleSelector(ComboBox<AnnotationLineStyle> lineStyleComboBox, Runnable updateCallback) {
        if (lineStyleComboBox == null) {
            return;
        }

        lineStyleComboBox.getItems().setAll(AnnotationLineStyle.values());
        lineStyleComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(AnnotationLineStyle style) {
                return style == null ? "" : style.getDisplayName();
            }

            @Override
            public AnnotationLineStyle fromString(String value) {
                return AnnotationLineStyle.fromString(value);
            }
        });
        lineStyleComboBox.setValue(AnnotationLineStyle.fromString(
                UserPreferencesManager.getInstance().getPreferences().getAnnotationLineStyle()));
        lineStyleComboBox.setOnAction(e -> {
            AnnotationLineStyle selected = lineStyleComboBox.getValue();
            UserPreferencesManager.getInstance().getPreferences()
                    .setAnnotationLineStyle(selected == null ? AnnotationLineStyle.SOLID.name() : selected.name());
            UserPreferencesManager.getInstance().savePreferences();
            if (updateCallback != null) {
                updateCallback.run();
            }
        });
    }

    public void setupOpacitySlider(Slider opacitySlider, Label opacityLabel, Runnable updateCallback) {
        if (opacitySlider == null) {
            return;
        }

        opacitySlider.setValue(UserPreferencesManager.getInstance().getPreferences().getAnnotationOpacity());
        opacitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            UserPreferencesManager.getInstance().getPreferences().setAnnotationOpacity(newVal.doubleValue());
            UserPreferencesManager.getInstance().savePreferences();
            if (opacityLabel != null) {
                opacityLabel.setText(String.format("%.0f%%", newVal.doubleValue() * 100));
            }
            if (updateCallback != null) {
                updateCallback.run();
            }
        });

        if (opacityLabel != null) {
            opacityLabel.setText(String.format("%.0f%%", opacitySlider.getValue() * 100));
        }
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

    /**
     * Keep ColorPicker on preset colors only.
     * Some JavaFX runtime combinations crash when opening CustomColorDialog.
     */
    private void disableCustomColorDialog(ColorPicker colorPicker) {
        colorPicker.showingProperty().addListener((obs, oldVal, showing) -> {
            if (Boolean.TRUE.equals(showing)) {
                Platform.runLater(this::disableCustomColorLinksInPopupWindows);
            }
        });
    }

    private void disableCustomColorLinksInPopupWindows() {
        for (Window window : Window.getWindows()) {
            if (window == null || !window.isShowing() || window.getScene() == null) {
                continue;
            }
            Node root = window.getScene().getRoot();
            if (root != null) {
                disableCustomColorLinks(root);
            }
        }
    }

    private void disableCustomColorLinks(Node root) {
        for (Node node : root.lookupAll(".custom-color-link")) {
            hideCustomColorNode(node);
        }
        for (Node node : root.lookupAll(".hyperlink")) {
            if (node instanceof Hyperlink hyperlink && isCustomColorHyperlink(hyperlink)) {
                hideCustomColorNode(hyperlink);
            }
        }
    }

    private boolean isCustomColorHyperlink(Hyperlink hyperlink) {
        String text = hyperlink.getText();
        return text != null && text.toLowerCase().contains("custom");
    }

    private void hideCustomColorNode(Node node) {
        node.setVisible(false);
        node.setManaged(false);
        node.setMouseTransparent(true);
        node.setDisable(true);
    }

    private javafx.scene.paint.Color parseColor(String value, javafx.scene.paint.Color fallback) {
        try {
            return javafx.scene.paint.Color.web(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String toRgbaString(javafx.scene.paint.Color color) {
        int red = (int) Math.round(color.getRed() * 255);
        int green = (int) Math.round(color.getGreen() * 255);
        int blue = (int) Math.round(color.getBlue() * 255);
        int alpha = (int) Math.round(color.getOpacity() * 255);
        return String.format("#%02X%02X%02X%02X", red, green, blue, alpha);
    }
}
