package org.pdflite.command;

import javafx.application.Platform;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.pdflite.controller.MainController;
import org.pdflite.model.PDFDocument;
import org.pdflite.service.PDFService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Command for deleting a page from a PDF document.
 * <p>
 * This command stores the deleted page data to allow undo.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 */
public class DeletePageCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(DeletePageCommand.class);

    private final MainController controller;
    private final PDFService pdfService;
    private final int pageIndex;
    private final long timestamp;

    // State for undo
    private PDPage deletedPage;
    private File backupFile;
    private PDFDocument document;

    /**
     * Creates a new DeletePageCommand.
     *
     * @param controller the main controller
     * @param pdfService the PDF service
     * @param pageIndex the zero-based index of the page to delete
     */
    public DeletePageCommand(MainController controller, PDFService pdfService, int pageIndex) {
        this.controller = controller;
        this.pdfService = pdfService;
        this.pageIndex = pageIndex;
        this.timestamp = System.currentTimeMillis();
        this.document = controller.getCurrentDocument();
    }

    @Override
    public void execute() throws IOException {
        logger.info("Executing DeletePageCommand for page {}", pageIndex + 1);

        // Get current document from controller (may have been reloaded)
        PDFDocument currentDoc = controller.getCurrentDocument();

        if (currentDoc == null || currentDoc.getDocument() == null) {
            throw new IOException("No document loaded");
        }

        // Update document reference
        this.document = currentDoc;
        PDDocument pdDoc = document.getDocument();

        // Backup the page before deletion
        if (pageIndex >= 0 && pageIndex < pdDoc.getNumberOfPages()) {
            deletedPage = pdDoc.getPage(pageIndex);

            // Create backup of entire file
            File originalFile = document.getFile();

            // Only create backup if not exists (first execute)
            if (backupFile == null) {
                backupFile = new File(originalFile.getParent(),
                    originalFile.getName() + ".backup_" + timestamp);

                // Save current state to backup
                pdDoc.save(backupFile);
                logger.info("Created backup file: {}", backupFile.getName());
            }

            // Now delete the page
            pdDoc.removePage(pageIndex);

            // Clear cache
            document.clearCache();

            // Save the modified document
            pdfService.save(document);

            // Note: Document reload will be handled by CommandManager after execute completes

            logger.info("Page {} deleted successfully", pageIndex + 1);
        } else {
            throw new IOException("Invalid page index: " + pageIndex);
        }
    }

    @Override
    public void undo() throws IOException {
        logger.info("Undoing DeletePageCommand for page {}", pageIndex + 1);

        if (backupFile == null || !backupFile.exists()) {
            throw new IOException("Cannot undo: backup file not found");
        }

        // Get current document from controller
        PDFDocument currentDoc = controller.getCurrentDocument();

        // Close current document if open
        if (currentDoc != null && currentDoc.getDocument() != null) {
            try {
                currentDoc.getDocument().close();
            } catch (Exception e) {
                logger.warn("Error closing document during undo", e);
            }
        }

        // Restore from backup
        File originalFile = currentDoc != null ? currentDoc.getFile() : document.getFile();

        if (originalFile == null) {
            throw new IOException("Cannot undo: original file path not found");
        }

        // Copy backup to original
        java.nio.file.Files.copy(
            backupFile.toPath(),
            originalFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );

        logger.info("Restored document from backup");

        // Note: Document reload will be handled by CommandManager after undo completes
        // Don't delete backup file - we might need it for redo

        logger.info("Undo completed for page {}", pageIndex + 1);
    }

    @Override
    public String getDescription() {
        return "Delete Page " + (pageIndex + 1);
    }

    @Override
    public CommandType getType() {
        return CommandType.DELETE_PAGE;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Cleans up resources (backup file) if command is no longer needed.
     */
    public void cleanup() {
        if (backupFile != null && backupFile.exists()) {
            backupFile.delete();
            logger.debug("Cleaned up backup file for page {}", pageIndex + 1);
        }
    }
}
