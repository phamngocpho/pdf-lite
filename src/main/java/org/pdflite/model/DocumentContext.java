package org.pdflite.model;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.pdflite.controller.ScrollHandler;
import org.pdflite.manager.AnnotationManager;
import org.pdflite.manager.RenderingManager;
import org.pdflite.view.AnnotationLayer;

/**
 * Context for a single document tab.
 * Contains all state and UI components for one PDF document.
 */
public class DocumentContext {
    private final PDFDocument document;
    private final ScrollPane scrollPane;
    private final VBox pagesContainer;
    private final StackPane contentPane;
    private AnnotationLayer.AnnotationMode annotationMode;
    private AnnotationManager annotationManager;
    private RenderingManager renderingManager;
    private ScrollHandler scrollHandler;
    
    public DocumentContext(PDFDocument document, ScrollPane scrollPane, VBox pagesContainer, StackPane contentPane) {
        this.document = document;
        this.scrollPane = scrollPane;
        this.pagesContainer = pagesContainer;
        this.contentPane = contentPane;
        this.annotationMode = AnnotationLayer.AnnotationMode.NONE;
    }
    
    public PDFDocument getDocument() {
        return document;
    }
    
    public ScrollPane getScrollPane() {
        return scrollPane;
    }
    
    public VBox getPagesContainer() {
        return pagesContainer;
    }
    
    public StackPane getContentPane() {
        return contentPane;
    }
    
    public AnnotationLayer.AnnotationMode getAnnotationMode() {
        return annotationMode;
    }
    
    public void setAnnotationMode(AnnotationLayer.AnnotationMode mode) {
        this.annotationMode = mode;
    }
    
    public AnnotationManager getAnnotationManager() {
        return annotationManager;
    }
    
    public void setAnnotationManager(AnnotationManager annotationManager) {
        this.annotationManager = annotationManager;
    }
    
    public RenderingManager getRenderingManager() {
        return renderingManager;
    }
    
    public void setRenderingManager(RenderingManager renderingManager) {
        this.renderingManager = renderingManager;
    }
    
    public ScrollHandler getScrollHandler() {
        return scrollHandler;
    }
    
    public void setScrollHandler(ScrollHandler scrollHandler) {
        this.scrollHandler = scrollHandler;
    }
}
