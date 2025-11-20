package org.pdflite.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utility class for creating ZIP archives.
 * Used for batch downloading split PDF files.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class ZipUtility {

    private static final Logger logger = LoggerFactory.getLogger(ZipUtility.class);
    private static final int BUFFER_SIZE = 8192;

    /**
     * Creates a ZIP archive containing the specified files.
     *
     * @param files   List of files to include in the ZIP
     * @param zipFile The output ZIP file
     * @throws IOException if an error occurs during ZIP creation
     */
    public static void createZipArchive(List<File> files, File zipFile) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Files list cannot be null or empty");
        }

        if (zipFile == null) {
            throw new IllegalArgumentException("ZIP file cannot be null");
        }

        logger.info("Creating ZIP archive: {} with {} files", zipFile.getName(), files.size());

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            for (File file : files) {
                if (FileUtils.isValidFile(file)) {
                    logger.warn("Skipping invalid file: {}", FileUtils.getFileNameOrNull(file));
                    continue;
                }

                addFileToZip(file, zos);
            }
        }

        logger.info("Successfully created ZIP archive: {} ({})",
                zipFile.getName(), FileUtils.formatFileSize(zipFile.length()));
    }

    /**
     * Adds a single file to a ZIP output stream.
     *
     * @param file The file to add
     * @param zos  The ZIP output stream
     * @throws IOException if an error occurs during file addition
     */
    private static void addFileToZip(File file, ZipOutputStream zos) throws IOException {
        logger.debug("Adding file to ZIP: {} ({})", file.getName(), FileUtils.formatFileSize(file.length()));

        ZipEntry zipEntry = new ZipEntry(file.getName());
        zos.putNextEntry(zipEntry);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
        }

        zos.closeEntry();
    }

    /**
     * Creates a ZIP archive with a progress callback.
     *
     * @param files            List of files to include in the ZIP
     * @param zipFile          The output ZIP file
     * @param progressCallback Callback for progress updates (0.0 to 1.0)
     * @throws IOException if an error occurs during ZIP creation
     */
    public static void createZipArchiveWithProgress(List<File> files, File zipFile,
                                                    ProgressCallback progressCallback) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Files list cannot be null or empty");
        }

        if (zipFile == null) {
            throw new IllegalArgumentException("ZIP file cannot be null");
        }

        logger.info("Creating ZIP archive with progress: {} with {} files",
                zipFile.getName(), files.size());

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            int totalFiles = files.size();
            int processedFiles = 0;

            for (File file : files) {
                if (FileUtils.isValidFile(file)) {
                    logger.warn("Skipping invalid file: {}", FileUtils.getFileNameOrNull(file));
                    continue;
                }

                addFileToZip(file, zos);
                processedFiles++;

                if (progressCallback != null) {
                    double progress = (double) processedFiles / totalFiles;
                    progressCallback.onProgress(progress, processedFiles, totalFiles);
                }
            }
        }

        logger.info("Successfully created ZIP archive: {}", zipFile.getName());
    }

    /**
     * Callback interface for ZIP creation progress.
     */
    @FunctionalInterface
    public interface ProgressCallback {
        /**
         * Called when progress is updated.
         *
         * @param progress       Progress value (0.0 to 1.0)
         * @param processedFiles Number of files processed
         * @param totalFiles     Total number of files
         */
        void onProgress(double progress, int processedFiles, int totalFiles);
    }
}

