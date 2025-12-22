package org.pdflite.manager;

import org.pdflite.dialog.CompressionDialog;
import org.pdflite.model.PDFDocument;
import org.pdflite.util.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.concurrent.Task;

import java.util.function.Supplier;

/**
 * Manages PDF optimization operations including compression and file size reduction.
 */
public class PDFOptimizationManager {

    private static final Logger logger = LoggerFactory.getLogger(PDFOptimizationManager.class);

    private final UIStateManager uiStateManager;
    private final SaveStatusManager saveStatusManager;
    private final ThemeManager themeManager;
    private Supplier<RenderingManager> renderingManagerSupplier;

    public PDFOptimizationManager(UIStateManager uiStateManager, RenderingManager renderingManager,
                                  SaveStatusManager saveStatusManager, ThemeManager themeManager) {
        this.uiStateManager = uiStateManager;
        this.saveStatusManager = saveStatusManager;
        this.themeManager = themeManager;
    }

    /**
     * Sets the rendering manager supplier for multi-tab support.
     */
    public void setRenderingManagerSupplier(Supplier<RenderingManager> renderingManagerSupplier) {
        this.renderingManagerSupplier = renderingManagerSupplier;
    }

    /**
     * Opens the PDF optimization dialog and handles compression.
     *
     * @param document The current PDF document
     */
    public void openOptimizationDialog(PDFDocument document) {
        if (document == null) {
            uiStateManager.showError("No Document", "Please open a PDF file first.");
            return;
        }

        try {
            // Get actual file size
            long fileSize = calculateFileSize(document);

            // Create a compression manager
            CompressionManager compressionManager = new CompressionManager();

            // Estimate compression for MEDIUM level (default selection)
            int estimatedReduction = compressionManager.estimateCompression(
                    document,
                    CompressionManager.CompressionLevel.MEDIUM
            );

            // Show compression dialog
            CompressionDialog dialog = new CompressionDialog(
                    fileSize, estimatedReduction, themeManager,
                    document, compressionManager);

            if (dialog.showAndWait()) {
                // User clicked Optimize
                var level = dialog.getSelectedLevel();

                // Perform compression with progress dialog
                performCompression(document, compressionManager, level);
            }
        } catch (Exception e) {
            logger.error("Error optimizing PDF", e);
            uiStateManager.showError("Error", "Failed to optimize PDF: " + e.getMessage());
        }
    }

    /**
     * Calculates the file size of the document.
     */
    private long calculateFileSize(PDFDocument document) {
        if (document.getFile() != null && document.getFile().exists()) {
            return document.getFile().length();
        } else {
            // Fallback: estimate based on document structure
            return (long) document.getDocument().getNumberOfPages() * 1024 * 100;
        }
    }

    /**
     * Performs compression with a progress dialog.
     */
    private void performCompression(PDFDocument document,
                                    CompressionManager compressionManager,
                                    CompressionManager.CompressionLevel level) {
        // Create background task
        Task<Boolean> compressionTask = new Task<>() {
            @Override
            protected Boolean call() {
                return compressionManager.compressPDF(document, level);
            }
        };

        // Run with progress dialog
        ProgressDialog.runWithProgress(
                compressionTask,
                "Optimizing",
                "Optimizing PDF...",
                result -> handleCompressionSuccess(result, level),
                this::handleCompressionError,
                themeManager
        );
    }

    /**
     * Handles successful compression.
     */
    private void handleCompressionSuccess(Boolean result, CompressionManager.CompressionLevel level) {
        if (result) {
            // Re-render all pages to show compressed version
            RenderingManager rm = renderingManagerSupplier != null ? renderingManagerSupplier.get() : null;
            if (rm != null) {
                rm.renderAllPages();
            }

            uiStateManager.updateStatus("PDF optimized - Don't forget to save!");
            logger.info("PDF compressed with {} level", level);

            // Trigger auto-save
            if (saveStatusManager != null) {
                saveStatusManager.triggerAutoSave();
            }
        } else {
            uiStateManager.showError("Optimization Failed",
                    "No images found to compress or optimization failed.");
        }
    }

    /**
     * Handles compression errors.
     */
    private void handleCompressionError(Throwable ex) {
        logger.error("Error during compression", ex);
        uiStateManager.showError("Error", "Failed to optimize PDF: " + ex.getMessage());
    }
}
