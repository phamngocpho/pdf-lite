package org.pdflite;

import javafx.application.Application;

/**
 * Launcher class for the PDF Lite application.
 * <p>
 * This class serves as the entry point for the application. It launches the JavaFX application
 * by delegating to {@link PDFLiteApplication}. This separate launcher class is necessary to
 * avoid issues with module system when running JavaFX applications.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class Launcher {
    /**
     * Main entry point for the PDF Lite application.
     * <p>
     * This method launches the JavaFX application by calling {@link Application#launch(Class, String...)}
     * with the {@link PDFLiteApplication} class.
     * </p>
     *
     * @param args command line arguments passed to the application
     */
    public static void main(String[] args) {
        Application.launch(PDFLiteApplication.class, args);
    }
}
