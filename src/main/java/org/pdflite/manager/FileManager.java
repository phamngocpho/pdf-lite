package org.pdflite.manager;

import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.pdflite.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Manages file operations for PDF documents.
 * Handles opening, saving, and deleting pages.
 */
public record FileManager(PDFService pdfService, FileOperationListener fileOperationListener) {
    private static final Logger logger = LoggerFactory.getLogger(FileManager.class);

    /**
     * Interface for listening to file operations.
     */
    public interface FileOperationListener {
        void onFileOpened(PDFDocument document, File file);

        void onFileSaved(String fileName);

        void onFileSaveAs(String fileName);

        void onError(String title, String message);

        void onPageDeleted(int pageNumber);
    }

    /**
     * Creates a new FileManager.
     *
     * @param pdfService            the PDF service
     * @param fileOperationListener listener for file operation events
     */
    public FileManager {
    }

    /**
     * Opens a file chooser and returns the selected file.
     *
     * @param stage the parent stage
     * @return the selected file, or null if cancelled
     */
    public File showOpenDialog(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open PDF File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(Constants.PDF_DESCRIPTION, Constants.PDF_EXTENSION)
        );
        return fileChooser.showOpenDialog(stage);
    }

    /**
     * Opens a PDF file.
     *
     * @param file the PDF file to open
     * @return the opened PDF document
     * @throws IOException if the file cannot be opened
     */
    public PDFDocument openFile(File file) throws IOException {
        if (file == null) {
            return null;
        }

        PDFDocument document = pdfService.openPDF(file);
        if (fileOperationListener != null) {
            fileOperationListener.onFileOpened(document, file);
        }
        logger.info("Successfully opened PDF: {}", file.getName());
        return document;
    }

    /**
     * Saves the document to its original file.
     *
     * @param document the document to save
     * @throws IOException if the file cannot be saved
     */
    public void save(PDFDocument document) throws IOException {
        if (document == null) {
            throw new IOException("No document to save");
        }

        pdfService.save(document);
        if (fileOperationListener != null) {
            fileOperationListener.onFileSaved(document.getFileName());
        }
        logger.info("Document saved");
    }

    /**
     * Shows save as dialog and saves the document.
     *
     * @param document the document to save
     * @param stage    the parent stage
     * @throws IOException if the file cannot be saved
     */
    public void saveAs(PDFDocument document, Stage stage) throws IOException {
        if (document == null) {
            throw new IOException("No document to save");
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF As");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(Constants.PDF_DESCRIPTION, Constants.PDF_EXTENSION)
        );
        File target = fileChooser.showSaveDialog(stage);

        if (target == null) {
            return; // User cancelled
        }

        pdfService.saveAs(document, target);
        if (fileOperationListener != null) {
            fileOperationListener.onFileSaveAs(target.getName());
        }
        logger.info("Document saved as {}", target.getAbsolutePath());
    }

    /**
     * Deletes pages from the document.
     *
     * @param document    the document
     * @param pageIndices the page indices to delete (0-based)
     * @throws IOException if pages cannot be deleted
     */
    public void deletePages(PDFDocument document, Collection<Integer> pageIndices) throws IOException {
        if (document == null || pageIndices == null || pageIndices.isEmpty()) {
            return;
        }

        int total = document.getTotalPages();
        if (pageIndices.size() >= total) {
            throw new IllegalArgumentException("Cannot delete all pages of a PDF document.");
        }

        pdfService.deletePages(document, pageIndices);

        // Notify about deleted pages
        for (int pageIndex : pageIndices) {
            if (fileOperationListener != null) {
                fileOperationListener.onPageDeleted(pageIndex + 1);
            }
        }

        logger.info("Deleted {} page(s)", pageIndices.size());
    }

    /**
     * Closes a PDF document.
     *
     * @param document the document to close
     */
    public void close(PDFDocument document) {
        if (document != null) {
            pdfService.closePDF(document);
        }
    }
}

