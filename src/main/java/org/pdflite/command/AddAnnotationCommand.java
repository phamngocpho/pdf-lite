package org.pdflite.command;

import org.pdflite.model.Annotation;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for adding an annotation to a PDF document.
 * Supports undo by removing the annotation.
 */
public class AddAnnotationCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(AddAnnotationCommand.class);
    
    private final PDFDocument document;
    private final Annotation annotation;
    private final java.util.function.Consumer<Integer> refreshCallback;
    
    /**
     * Creates a new AddAnnotationCommand.
     * 
     * @param document the PDF document
     * @param annotation the annotation to add
     * @param refreshCallback callback to refresh the page display (takes page index)
     */
    public AddAnnotationCommand(PDFDocument document, Annotation annotation, 
                                java.util.function.Consumer<Integer> refreshCallback) {
        this.document = document;
        this.annotation = annotation;
        this.refreshCallback = refreshCallback;
    }
    
    @Override
    public void execute() {
        document.addAnnotation(annotation);
        document.setHasUnsavedEdits(true);
        
        if (refreshCallback != null) {
            refreshCallback.accept(annotation.getPageNumber());
        }
        
        logger.debug("Added annotation on page {}", annotation.getPageNumber() + 1);
    }
    
    @Override
    public void undo() {
        document.getAnnotations().remove(annotation);
        document.setHasUnsavedEdits(true);
        
        if (refreshCallback != null) {
            refreshCallback.accept(annotation.getPageNumber());
        }
        
        logger.debug("Removed annotation from page {}", annotation.getPageNumber() + 1);
    }
    
    @Override
    public String getDescription() {
        return "Add " + annotation.getClass().getSimpleName();
    }
}
