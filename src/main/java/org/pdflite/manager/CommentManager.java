package org.pdflite.manager;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.pdflite.controller.PageRenderer;
import org.pdflite.model.CommentAnnotation;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.paint.Color;

/**
 * Manages comment annotation operations.
 */
public class CommentManager {
    private static final Logger logger = LoggerFactory.getLogger(CommentManager.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final UIStateManager uiStateManager;
    private final Supplier<PDFDocument> documentSupplier;
    private final DoubleSupplier zoomSupplier;
    private final Supplier<AnnotationManager> annotationManagerSupplier;

    /**
     * Creates a new CommentManager.
     */
    public CommentManager(UIStateManager uiStateManager,
                          Supplier<PDFDocument> documentSupplier,
                          DoubleSupplier zoomSupplier,
                          Supplier<AnnotationManager> annotationManagerSupplier) {
        this.uiStateManager = uiStateManager;
        this.documentSupplier = documentSupplier;
        this.zoomSupplier = zoomSupplier;
        this.annotationManagerSupplier = annotationManagerSupplier;

        logger.info("CommentManager initialized");
    }

    /**
     * Sets up the add comment callback for the context menu handler.
     */
    public void setupAddCommentCallback(PageRenderer pageRenderer) {
        pageRenderer.getContextMenuHandler().setAddCommentCallback(
                (pageIndex, canvasX, canvasY, comment) -> {
                    try {
                        PDFDocument currentDocument = documentSupplier.get();
                        if (currentDocument == null) {
                            uiStateManager.updateStatus(lang().getString("comment.noDocument"));
                            logger.warn("Cannot add comment: no document loaded");
                            return;
                        }

                        // Create comment annotation at cursor position
                        CommentAnnotation commentAnnotation = new CommentAnnotation(
                                pageIndex,
                                canvasX,
                                canvasY,
                                comment,
                                Color.YELLOW);

                        // Add to document
                        currentDocument.addAnnotation(commentAnnotation);
                        currentDocument.setHasUnsavedEdits(true);

                        // Refresh the page to show the comment
                        AnnotationManager annotationManager = annotationManagerSupplier.get();
                        if (annotationManager != null) {
                            annotationManager.refreshPageAnnotations(pageIndex);
                        }

                        uiStateManager.updateStatus(lang().getString("comment.added"));
                        logger.info("Added comment at page {} position ({}, {})", pageIndex + 1, canvasX, canvasY);

                    } catch (Exception e) {
                        logger.error("Error adding comment", e);
                        uiStateManager.updateStatus(lang().getString("comment.errorAdd") + ": " + e.getMessage());
                    }
                });

        logger.info("Comment callback configured successfully");
    }

    /**
     * Sets up the delete comment callback for the context menu handler.
     */
    public void setupDeleteCommentCallback(PageRenderer pageRenderer) {
        pageRenderer.getContextMenuHandler().setDeleteCommentCallback(
                (pageIndex, canvasX, canvasY) -> {
                    try {
                        PDFDocument currentDocument = documentSupplier.get();
                        if (currentDocument == null) {
                            uiStateManager.updateStatus(lang().getString("comment.noDocument"));
                            return;
                        }

                        // Find comment at cursor position
                        CommentAnnotation targetComment = null;
                        java.util.List<org.pdflite.model.Annotation> allAnnotations = currentDocument.getAnnotations();
                        
                        double iconSize = 24;
                        for (int i = allAnnotations.size() - 1; i >= 0; i--) {
                            org.pdflite.model.Annotation annotation = allAnnotations.get(i);
                            if (annotation.getPageNumber() != pageIndex) {
                                continue;
                            }
                            if (annotation instanceof CommentAnnotation comment) {
                                if (canvasX >= comment.getX() && canvasX <= comment.getX() + iconSize &&
                                    canvasY >= comment.getY() && canvasY <= comment.getY() + iconSize) {
                                    targetComment = comment;
                                    break;
                                }
                            }
                        }

                        if (targetComment == null) {
                            uiStateManager.updateStatus(lang().getString("comment.noCommentAtCursor"));
                            return;
                        }

                        // Remove the comment
                        boolean removed = allAnnotations.remove(targetComment);
                        if (removed) {
                            currentDocument.setHasUnsavedEdits(true);

                            // Refresh the page
                            AnnotationManager annotationManager = annotationManagerSupplier.get();
                            if (annotationManager != null) {
                                annotationManager.refreshPageAnnotations(pageIndex);
                            }

                            uiStateManager.updateStatus(lang().getString("comment.deleted"));
                            logger.info("Deleted comment at page {} position ({}, {})", pageIndex + 1, canvasX, canvasY);
                        } else {
                            uiStateManager.updateStatus(lang().getString("comment.deleteFailed"));
                        }

                    } catch (Exception e) {
                        logger.error("Error deleting comment", e);
                        uiStateManager.updateStatus(lang().getString("comment.errorDelete") + ": " + e.getMessage());
                    }
                }
        );

        logger.info("Delete comment callback configured successfully");
    }
}
