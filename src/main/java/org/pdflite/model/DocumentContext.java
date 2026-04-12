package org.pdflite.model;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
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
    private BorderPane tabRoot;
    private SplitPane splitPane;
    private VBox sidebarContainer;
    private boolean sidebarCollapsed;
    private double sidebarDividerPosition = 0.24;
    
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

    public BorderPane getTabRoot() {
        return tabRoot;
    }

    public void setTabRoot(BorderPane tabRoot) {
        this.tabRoot = tabRoot;
    }

    public SplitPane getSplitPane() {
        return splitPane;
    }

    public void setSplitPane(SplitPane splitPane) {
        this.splitPane = splitPane;
    }

    public VBox getSidebarContainer() {
        return sidebarContainer;
    }

    public void setSidebarContainer(VBox sidebarContainer) {
        this.sidebarContainer = sidebarContainer;
    }

    public boolean isSidebarCollapsed() {
        return sidebarCollapsed;
    }

    public void setSidebarCollapsed(boolean sidebarCollapsed) {
        this.sidebarCollapsed = sidebarCollapsed;
    }

    public double getSidebarDividerPosition() {
        return sidebarDividerPosition;
    }

    public void setSidebarDividerPosition(double sidebarDividerPosition) {
        this.sidebarDividerPosition = sidebarDividerPosition;
    }
}
