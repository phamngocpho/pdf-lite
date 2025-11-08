package org.pdflite.util;

/**
 * Application constants
 */
public class Constants {
    
    // Application Info
    public static final String APP_NAME = "PDF Lite";
    public static final String APP_VERSION = "1.0.0";
    
    // Default values
    public static final float DEFAULT_DPI = 150f;
    public static final double DEFAULT_ZOOM = 1.0;
    public static final double MIN_ZOOM = 0.25;
    public static final double MAX_ZOOM = 5.0;
    public static final double ZOOM_STEP = 0.25;
    
    // File extensions
    public static final String PDF_EXTENSION = "*.pdf";
    public static final String PDF_DESCRIPTION = "PDF Files";
    
    private Constants() {
        // Prevent instantiation
    }
}
