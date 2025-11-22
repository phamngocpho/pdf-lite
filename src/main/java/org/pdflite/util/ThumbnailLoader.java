package org.pdflite.util;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Utility class for loading PDF thumbnails.
 */
public class ThumbnailLoader {
    private static final Logger logger = LoggerFactory.getLogger(ThumbnailLoader.class);
    private static final double THUMBNAIL_SIZE = 120.0;
    private static final double PREVIEW_SCALE = 0.3;

    /**
     * Loads thumbnails for all pages of a PDF.
     *
     * @param sourceFile      the PDF file
     * @param totalPages      total number of pages
     * @param previewPane     the FlowPane to add thumbnails to
     * @param pdfService      the PDF service
     * @param executorService the executor service
     * @param updateStatus    callback to update status
     */
    public static void loadThumbnails(File sourceFile, int totalPages, FlowPane previewPane,
                                      PDFService pdfService, ExecutorService executorService,
                                      Consumer<String> updateStatus) {
        previewPane.getChildren().clear();
        updateStatus.accept("Loading thumbnails...");

        executorService.submit(() -> {
            PDFDocument doc = null;
            try {
                doc = new PDFDocument(
                        org.apache.pdfbox.Loader.loadPDF(sourceFile),
                        sourceFile
                );

                loadThumbnailsForDocument(doc, totalPages, previewPane, pdfService, updateStatus);

            } catch (IOException e) {
                logger.error("Error loading thumbnails", e);
                Platform.runLater(() -> updateStatus.accept("Error loading thumbnails"));
            } finally {
                if (doc != null && doc.getDocument() != null) {
                    try {
                        doc.getDocument().close();
                    } catch (IOException e) {
                        logger.error("Error closing document", e);
                    }
                }
            }
        });
    }

    /**
     * Loads thumbnails from an already-opened PDDocument (for encrypted PDFs).
     *
     * @param sourceDoc       the PDDocument (already opened/decrypted)
     * @param totalPages      total number of pages
     * @param previewPane     the FlowPane to add thumbnails to
     * @param pdfService      the PDF service
     * @param executorService the executor service
     * @param updateStatus    callback to update status
     */
    public static void loadThumbnailsFromDocument(PDDocument sourceDoc, int totalPages, FlowPane previewPane,
                                                  PDFService pdfService, ExecutorService executorService,
                                                  Consumer<String> updateStatus) {
        previewPane.getChildren().clear();
        updateStatus.accept("Loading thumbnails...");

        executorService.submit(() -> {
            try {
                // Create a temporary PDFDocument wrapper (without closing the underlying PDDocument)
                PDFDocument doc = new PDFDocument(sourceDoc, null);

                loadThumbnailsForDocument(doc, totalPages, previewPane, pdfService, updateStatus);

            } catch (IOException e) {
                logger.error("Error loading thumbnails", e);
                Platform.runLater(() -> updateStatus.accept("Error loading thumbnails"));
            }
            // Note: We don't close sourceDoc here as it's managed by the caller
        });
    }

    /**
     * Helper method to load thumbnails for all pages of a PDFDocument.
     *
     * @param doc          the PDF document
     * @param totalPages   total number of pages
     * @param previewPane  the FlowPane to add thumbnails to
     * @param pdfService   the PDF service
     * @param updateStatus callback to update status
     */
    private static void loadThumbnailsForDocument(PDFDocument doc, int totalPages, FlowPane previewPane,
                                                  PDFService pdfService, Consumer<String> updateStatus) throws IOException {
        for (int i = 0; i < totalPages; i++) {
            final int pageNum = i;
            Image thumbnail = pdfService.renderPage(doc, pageNum, (float) PREVIEW_SCALE);

            Platform.runLater(() -> {
                VBox pageBox = createThumbnailBox(thumbnail, pageNum + 1);
                previewPane.getChildren().add(pageBox);
            });
        }

        Platform.runLater(() -> updateStatus.accept("Thumbnails loaded"));
    }

    /**
     * Creates a thumbnail box with page number.
     *
     * @param thumbnail  the thumbnail image
     * @param pageNumber the page number (1-based)
     * @return VBox containing the thumbnail
     */
    public static VBox createThumbnailBox(Image thumbnail, int pageNumber) {
        ImageView imageView = new ImageView(thumbnail);
        imageView.setFitWidth(THUMBNAIL_SIZE);
        imageView.setFitHeight(THUMBNAIL_SIZE);
        imageView.setPreserveRatio(true);

        Label pageLabel = new Label("Page " + pageNumber);
        pageLabel.setStyle("-fx-font-size: 10px;");

        VBox box = new VBox(5, imageView, pageLabel);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-border-color: #ccc; -fx-border-width: 1; -fx-padding: 5;");

        return box;
    }
}

