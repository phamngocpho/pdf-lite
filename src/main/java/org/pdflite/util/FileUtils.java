package org.pdflite.util;

import java.io.File;

/**
 * Utility class for common file operations.
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class FileUtils {

    private FileUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Formats file size in human-readable format.
     *
     * @param bytes File size in bytes
     * @return Formatted string (e.g., "1.5 MB")
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Checks if a file is valid (not null, exists, and can be read).
     *
     * @param file The file to validate
     * @return true if the file is valid, false otherwise
     */
    public static boolean isValidFile(File file) {
        return file == null || !file.exists() || !file.canRead();
    }

    /**
     * Gets a safe file name for logging (returns "null" if file is null).
     *
     * @param file The file
     * @return The file name or "null"
     */
    public static String getFileNameOrNull(File file) {
        return file != null ? file.getName() : "null";
    }
}

