package org.pdflite;

import java.io.IOException;

import org.pdflite.controller.MainController;
import org.pdflite.manager.TitleBarManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

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

        // Use undecorated style for a custom title bar
        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);

        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);

        var fxmlUrl = PDFLiteApplication.class.getResource("/org/pdflite/main-view.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("Cannot find FXML on classpath: /org/pdflite/main-view.fxml");
        }
        FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl);
        // Don’t set an explicit size; let maximizing handle it to avoid window flashing
        Scene scene = new Scene(fxmlLoader.load());

        // Add CSS stylesheet
        var cssUrl = PDFLiteApplication.class.getResource("/org/pdflite/styles.css");
        if (cssUrl == null) {
            throw new IllegalStateException("Cannot find CSS on classpath: /org/pdflite/styles.css");
        }
        scene.getStylesheets().add(cssUrl.toExternalForm());

        stage.setTitle("PDF Lite - PDF Viewer & Editor");
        stage.setScene(scene);


        // Get controller and set up a window close handler
        MainController controller = fxmlLoader.getController();
        stage.setOnCloseRequest(event -> {
            controller.performExit();
            event.consume();
        });

        // Maximize the window to fill the screen (excluding taskbar)
        TitleBarManager.maximizeToScreen(stage);

        // Set initial opacity to 0 for smooth fade-in
        stage.setOpacity(0);

        stage.show();

        // Smooth fade-in animation with easing (like Windows 11)
        Platform.runLater(() -> {
            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), scene.getRoot());
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setInterpolator(Interpolator.EASE_OUT);

            // Set stage opacity gradually for a smoother effect
            stage.setOpacity(1);
            fadeIn.play();

            // Notify the controller that the window is maximized on startup
            controller.notifyWindowMaximized();
        });

        logger.info("Application started successfully");

        // Open the last file after UI is shown
        Platform.runLater(controller::openLastFile);
    }

}
