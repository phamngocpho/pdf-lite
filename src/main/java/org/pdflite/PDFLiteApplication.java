package org.pdflite;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.pdflite.controller.MainController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Main JavaFX Application class for PDF Lite.
 * <p>
 * This class is responsible for initializing and starting the PDF Lite application.
 * It sets up the main window, loads the FXML layout, applies stylesheets, and configures
 * the application's appearance and behavior.
 * </p>
 * <p>
 * The application provides features for viewing PDF documents with zoom, navigation,
 * and annotation capabilities.
 * </p>
 *
 * @author PDF Lite Team
 * @version 1.0.0
 * @see javafx.application.Application
 * @since 1.0.0
 */
public class PDFLiteApplication extends Application {
    private static final Logger logger = LoggerFactory.getLogger(PDFLiteApplication.class);

    /**
     * Minimum width of the application window in pixels.
     */
    private static final int MIN_WIDTH = 1000;

    /**
     * Minimum height of the application window in pixels.
     */
    private static final int MIN_HEIGHT = 700;

    /**
     * Starts the PDF Lite application and initializes the main window.
     * <p>
     * This method is called by the JavaFX runtime when the application is launched.
     * It performs the following tasks:
     * <ul>
     *   <li>Loads the main FXML layout file</li>
     *   <li>Creates the main scene with specified dimensions</li>
     *   <li>Applies the CSS stylesheet</li>
     *   <li>Sets the window title and size constraints</li>
     *   <li>Attempts to load and set the application icon</li>
     *   <li>Displays the main window</li>
     * </ul>
     * </p>
     *
     * @param stage the primary stage for this application, onto which
     *              the application scene can be set
     * @throws IOException if the FXML file cannot be loaded
     */
    @Override
    public void start(Stage stage) throws IOException {
        logger.info("Starting PDF Lite Application");

        // Set maximized first, before creating the scene, to avoid window flash
        stage.setMaximized(true);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);

        FXMLLoader fxmlLoader = new FXMLLoader(PDFLiteApplication.class.getResource("main-view.fxml"));
        // Don’t set an explicit size; let maximizing handle it to avoid window flashing
        Scene scene = new Scene(fxmlLoader.load());

        // Add CSS stylesheet
        scene.getStylesheets().add(
                Objects.requireNonNull(PDFLiteApplication.class.getResource("styles.css")).toExternalForm()
        );

        stage.setTitle("PDF Lite - PDF Viewer & Editor");
        stage.setScene(scene);

        // Set application icon (if available)
        try {
            stage.getIcons().add(new Image(Objects.requireNonNull(PDFLiteApplication.class.getResourceAsStream("icon.png"))));
        } catch (Exception e) {
            logger.warn("Could not load application icon");
        }

        // Get controller and set up a window close handler
        MainController controller = fxmlLoader.getController();
        stage.setOnCloseRequest(event -> {
            controller.performExit();
            event.consume();
        });

        // Hide the window initially to avoid flash, then show maximized
        stage.setOpacity(0);
        stage.show();
        
        // Force maximize and make visible on JavaFX thread
        Platform.runLater(() -> {
            stage.setMaximized(true);
            stage.setOpacity(1);
        });
        
        logger.info("Application started successfully");

        // Open the last file after UI is shown
        Platform.runLater(controller::openLastFile);
    }

}
