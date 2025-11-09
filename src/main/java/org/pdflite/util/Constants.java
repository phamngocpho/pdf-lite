package org.pdflite.util;

/**
 * Application-wide constants for PDF Lite.
 * <p>
 * This class contains all constant values used throughout the application,
 * including application metadata, default values for PDF rendering and viewing,
 * zoom settings, and file extension definitions.
 * </p>
 * <p>
 * This is a utility class and cannot be instantiated.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class Constants {
    
    // Application Info

    /**
     * The name of the application.
     */
    public static final String APP_NAME = "PDF Lite";

    /**
     * The version number of the application.
     */
    public static final String APP_VERSION = "1.0.0";
    
    // Default values

    /**
     * Default DPI (Dots Per Inch) for PDF rendering.
     * <p>
     * Higher DPI values produce higher quality images but require more memory
     * and processing time. 150 DPI provides a good balance between quality and performance.
     * </p>
     */
    public static final float DEFAULT_DPI = 150f;

    /**
     * Default zoom level (1.0 = 100%).
     * <p>
     * A value of 1.0 means the PDF is displayed at its original size.
     * </p>
     */
    public static final double DEFAULT_ZOOM = 1.0;

    /**
     * Minimum allowed zoom level (0.25 = 25%).
     * <p>
     * This prevents the user from zooming out too far, which would make
     * the content unreadable.
     * </p>
     */
    public static final double MIN_ZOOM = 0.25;

    /**
     * Maximum allowed zoom level (5.0 = 500%).
     * <p>
     * This prevents excessive memory usage and performance issues that
     * would occur with very high zoom levels.
     * </p>
     */
    public static final double MAX_ZOOM = 5.0;

    /**
     * Zoom increment/decrement step (0.25 = 25%).
     * <p>
     * Each zoom in/out operation changes the zoom level by this amount.
     * </p>
     */
    public static final double ZOOM_STEP = 0.25;
    
    // File extensions

    /**
     * File extension pattern for PDF files.
     * <p>
     * Used in file chooser dialogs to filter for PDF files.
     * </p>
     */
    public static final String PDF_EXTENSION = "*.pdf";

    /**
     * Human-readable description for PDF file type.
     * <p>
     * Displayed in file chooser dialogs.
     * </p>
     */
    public static final String PDF_DESCRIPTION = "PDF Files";
    
    /**
     * Private constructor to prevent instantiation.
     * <p>
     * This is a utility class containing only static constants and should
     * not be instantiated.
     * </p>
     *
     * @throws AssertionError if an attempt is made to instantiate this class
     */
    private Constants() {
        // Prevent instantiation
        throw new AssertionError("Cannot instantiate Constants class");
    }
}
