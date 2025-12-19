package org.pdflite.util;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration utility for the Apache PDFBox library.
 * <p>
 * This class provides configuration for PDFBox memory management and scratch file
 * mechanisms to handle large PDF documents efficiently.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class PDFBoxConfig {
    private static final Logger logger = LoggerFactory.getLogger(PDFBoxConfig.class);

    /**
     * Default memory threshold in bytes (100 MB).
     * When memory usage exceeds this threshold, PDFBox will use scratch files.
     */
    private static final long DEFAULT_MEMORY_THRESHOLD = 100 * 1024 * 1024; // 100 MB

    /**
     * Default maximum main memory usage in bytes (500 MB).
     */
    private static final long DEFAULT_MAX_MAIN_MEMORY = 500 * 1024 * 1024; // 500 MB

    /**
     * Scratch file directory for temporary PDF processing.
     */
    private static Path scratchFileDirectory;

    /**
     * Memory usage setting for PDFBox operations.
     */
    private static MemoryUsageSetting memoryUsageSetting;

    static {
        initialize();
    }

    /**
     * Initializes PDFBox configuration with default settings.
     */
    private static void initialize() {
        try {
            // Create scratch file directory in system temp
            scratchFileDirectory = Files.createTempDirectory("pdflite-scratch");
            scratchFileDirectory.toFile().deleteOnExit();

            // Configure memory usage setting
            memoryUsageSetting = MemoryUsageSetting.setupMixed(DEFAULT_MAX_MAIN_MEMORY)
                    .setTempDir(scratchFileDirectory.toFile());

            logger.info("PDFBox configured with scratch directory: {}", scratchFileDirectory);
            logger.info("Memory threshold: {} MB, Max main memory: {} MB",
                    DEFAULT_MEMORY_THRESHOLD / (1024 * 1024),
                    DEFAULT_MAX_MAIN_MEMORY / (1024 * 1024));

        } catch (IOException e) {
            logger.error("Failed to initialize PDFBox scratch directory", e);
            // Fallback to default memory-only mode
            memoryUsageSetting = MemoryUsageSetting.setupMainMemoryOnly();
        }
    }

    /**
     * Gets the configured memory usage setting for PDFBox operations.
     * <p>
     * This setting should be used when loading or saving large PDF documents
     * to ensure efficient memory management.
     * </p>
     *
     * @return the memory usage setting
     */
    public static MemoryUsageSetting getMemoryUsageSetting() {
        return memoryUsageSetting;
    }

    /**
     * Gets the scratch file directory path.
     *
     * @return the scratch file directory, or null if not configured
     */
    public static Path getScratchFileDirectory() {
        return scratchFileDirectory;
    }

    /**
     * Cleans up the scratch file directory.
     * <p>
     * This method should be called when the application is shutting down
     * to remove temporary files.
     * </p>
     */
    public static void cleanup() {
        if (scratchFileDirectory != null && Files.exists(scratchFileDirectory)) {
            try {
                // Delete all files in the scratch directory
                Files.walk(scratchFileDirectory)
                        .sorted((a, b) -> -a.compareTo(b)) // Delete files before directories
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                logger.warn("Failed to delete scratch file: {}", path, e);
                            }
                        });
                logger.info("Cleaned up scratch directory: {}", scratchFileDirectory);
            } catch (IOException e) {
                logger.error("Failed to cleanup scratch directory", e);
            }
        }
    }

    /**
     * Reconfigures PDFBox with custom memory settings.
     * <p>
     * This method allows customization of memory thresholds for specific use cases.
     * </p>
     *
     * @param maxMainMemory the maximum main memory to use in bytes
     * @throws IOException if scratch directory cannot be created
     */
    public static void reconfigure(long maxMainMemory) throws IOException {
        if (scratchFileDirectory == null || !Files.exists(scratchFileDirectory)) {
            scratchFileDirectory = Files.createTempDirectory("pdflite-scratch");
            scratchFileDirectory.toFile().deleteOnExit();
        }

        memoryUsageSetting = MemoryUsageSetting.setupMixed(maxMainMemory)
                .setTempDir(scratchFileDirectory.toFile());

        logger.info("PDFBox reconfigured with max main memory: {} MB",
                maxMainMemory / (1024 * 1024));
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private PDFBoxConfig() {
        throw new UnsupportedOperationException("Utility class");
    }
}
