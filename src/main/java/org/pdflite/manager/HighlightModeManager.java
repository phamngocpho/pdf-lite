package org.pdflite.manager;

import javafx.scene.control.ToggleGroup;
import org.pdflite.controller.PageRenderer;
import org.pdflite.view.AnnotationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Manager for highlight mode operations.
 */
public class HighlightModeManager {

    private static final Logger logger = LoggerFactory.getLogger(HighlightModeManager.class);

    private final UIStateManager uiStateManager;
    private final PageRenderer pageRenderer;
    private final Supplier<ToggleGroup> drawingToolsGroupSupplier;
    private final Consumer<AnnotationLayer.AnnotationMode> annotationModeUpdater;

    private boolean highlightModeActive = false;

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    public HighlightModeManager(UIStateManager uiStateManager,
                                PageRenderer pageRenderer,
                                Supplier<ToggleGroup> drawingToolsGroupSupplier,
                                Consumer<AnnotationLayer.AnnotationMode> annotationModeUpdater) {
        this.uiStateManager = uiStateManager;
        this.pageRenderer = pageRenderer;
        this.drawingToolsGroupSupplier = drawingToolsGroupSupplier;
        this.annotationModeUpdater = annotationModeUpdater;
    }

    /**
     * Toggles highlight mode on/off.
     */
    public void handleHighlight() {
        highlightModeActive = !highlightModeActive;

        if (highlightModeActive) {
            // Disable drawing tools when enabling highlight
            ToggleGroup drawingToolsGroup = drawingToolsGroupSupplier.get();
            if (drawingToolsGroup != null) {
                drawingToolsGroup.selectToggle(null);
            }

            uiStateManager.updateStatus(lang().getString("toolbar.highlight"));
            pageRenderer.setHighlightModeActive();
            annotationModeUpdater.accept(AnnotationLayer.AnnotationMode.HIGHLIGHT);
            logger.debug("Highlight mode activated");
        } else {
            uiStateManager.updateStatus(lang().getString("status.ready"));
            pageRenderer.setHighlightModeActive();
            annotationModeUpdater.accept(AnnotationLayer.AnnotationMode.NONE);
            logger.debug("Highlight mode deactivated");
        }
    }

    public boolean isHighlightModeActive() {
        return highlightModeActive;
    }

    public void setHighlightModeActive(boolean active) {
        this.highlightModeActive = active;
    }
}
