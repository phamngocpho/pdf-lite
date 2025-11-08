package org.pdflite;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Main Application class for PDF Lite
 */
public class PDFLiteApplication extends Application {
    private static final Logger logger = LoggerFactory.getLogger(PDFLiteApplication.class);
    private static final int MIN_WIDTH = 1000;
    private static final int MIN_HEIGHT = 700;

    @Override
    public void start(Stage stage) throws IOException {
        logger.info("Starting PDF Lite Application");
        
        FXMLLoader fxmlLoader = new FXMLLoader(PDFLiteApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        
        // Add CSS stylesheet
        scene.getStylesheets().add(
            Objects.requireNonNull(PDFLiteApplication.class.getResource("styles.css")).toExternalForm()
        );
        
        stage.setTitle("PDF Lite - PDF Viewer & Editor");
        stage.setScene(scene);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        
        // Set application icon (if available)
        try {
            stage.getIcons().add(new Image(Objects.requireNonNull(PDFLiteApplication.class.getResourceAsStream("icon.png"))));
        } catch (Exception e) {
            logger.warn("Could not load application icon");
        }
        
        stage.show();
        logger.info("Application started successfully");
    }

}
