package org.pdflite.manager;

import java.util.List;

import org.pdflite.controller.PageRenderer;
import org.pdflite.controller.ScrollHandler;
import org.pdflite.model.HighlightAnnotation;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/**
 * Manages document setup operations when opening a PDF file.
 */
public class DocumentSetupManager {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentSetupManager.class);
    
    private final ZoomManager zoomManager;
    private final RenderingManager renderingManager;
    private final PageInfoManager pageInfoManager;
    private final HighlightPersistenceManager highlightPersistenceManager;
    private final AutoSaveManager autoSaveManager;
    private final SaveStatusManager saveStatusManager;
    private final PageRenderer pageRenderer;
    private final ScrollHandler scrollHandler;
    
    public DocumentSetupManager(ZoomManager zoomManager,
                               RenderingManager renderingManager,
                               PageInfoManager pageInfoManager,
                               HighlightPersistenceManager highlightPersistenceManager,
                               AutoSaveManager autoSaveManager,
                               SaveStatusManager saveStatusManager,
                               PageRenderer pageRenderer,
                               ScrollHandler scrollHandler) {
        this.zoomManager = zoomManager;
        this.renderingManager = renderingManager;
        this.pageInfoManager = pageInfoManager;
        this.highlightPersistenceManager = highlightPersistenceManager;
        this.autoSaveManager = autoSaveManager;
        this.saveStatusManager = saveStatusManager;
        this.pageRenderer = pageRenderer;
        this.scrollHandler = scrollHandler;
    }
    
    /**
     * Sets up a newly opened document with all necessary configurations.
     */
    public AnnotationManager setupDocument(PDFDocument document, VBox pagesContainer, 
                                          ScrollPane scrollPane,
                                          ListenerFactory.ZoomChangeListenerWithContext zoomChangeListener,
                                          UIStateManager uiStateManager) {
        if (document == null || pagesContainer == null) {
            return null;
        }
        
        logger.info("Setting up document: {}", document.getFile() != null ? 
                   document.getFile().getName() : "untitled");
        
        // Load existing highlights from PDF
        loadHighlights(document);
        
        // Create annotation manager
        AnnotationManager annotationManager = new AnnotationManager(pagesContainer, uiStateManager, document);
        
        // Set refresh callback for PageRenderer
        setupRefreshCallback(annotationManager);
        
        // Update managers with current document
        updateManagers(document, pagesContainer, scrollPane, zoomChangeListener);
        
        // Enable text selection by default
        enableTextSelection(pagesContainer);
        
        // Setup auto-save
        setupAutoSave(document);
        
        // Show saved status initially
        updateSaveStatus(document);
        
        logger.info("Document setup completed");
        
        return annotationManager;
    }
    
    /**
     * Loads existing highlights from the PDF document.
     */
    private void loadHighlights(PDFDocument document) {
        if (highlightPersistenceManager == null) {
            return;
        }
        
        try {
            List<HighlightAnnotation> loadedHighlights = 
                highlightPersistenceManager.loadHighlightsFromPDF(document.getDocument());
            
            // Add loaded highlights to document
            for (HighlightAnnotation highlight : loadedHighlights) {
                document.addAnnotation(highlight);
            }
            
            logger.info("Loaded {} highlights from PDF", loadedHighlights.size());
        } catch (Exception e) {
            logger.error("Error loading highlights from PDF", e);
        }
    }
    
    /**
     * Sets up the refresh callback for page renderer.
     */
    private void setupRefreshCallback(AnnotationManager annotationManager) {
        if (pageRenderer != null) {
            pageRenderer.setRefreshAnnotationsCallback(pageIndex -> {
                if (annotationManager != null) {
                    annotationManager.refreshPageAnnotations(pageIndex);
                }
            });
        }
    }
    
    /**
     * Updates all managers with the current document context.
     */
    private void updateManagers(PDFDocument document, VBox pagesContainer, 
                               ScrollPane scrollPane,
                               ListenerFactory.ZoomChangeListenerWithContext zoomChangeListener) {
        // Update zoom manager
        if (zoomManager != null) {
            zoomManager.setDocument(document);
        }
        
        // Update rendering manager
        if (renderingManager != null) {
            renderingManager.setDocument(document);
        }
        
        // Update zoom change listener
        if (zoomChangeListener != null) {
            zoomChangeListener.updateContext(document, pagesContainer, scrollPane);
        }
        
        // Update page change listener
        if (scrollHandler != null && pageInfoManager != null) {
            scrollHandler.setPageChangeListener(
                ListenerFactory.createPageChangeListener(document, pageInfoManager));
        }
    }
    
    /**
     * Enables text selection by default when document is opened.
     */
    private void enableTextSelection(VBox pagesContainer) {
        // Use Platform.runLater to ensure pages are fully rendered first
        Platform.runLater(() -> {
            if (pageRenderer != null && pagesContainer != null) {
                pageRenderer.setSelectionModeActive(pagesContainer, true);
            }
        });
    }
    
    /**
     * Sets up auto-save for the document.
     */
    private void setupAutoSave(PDFDocument document) {
        if (autoSaveManager == null) {
            return;
        }
        
        autoSaveManager.setDocument(document);
        logger.info("Auto-save enabled for document");
    }
    
    /**
     * Updates the save status indicator.
     */
    private void updateSaveStatus(PDFDocument document) {
        if (saveStatusManager != null) {
            saveStatusManager.setCurrentDocument(document);
            saveStatusManager.updateSaveStatusIndicator(true);
        }
    }
}
