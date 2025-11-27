package org.pdflite.manager;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.pdflite.controller.MainController;
import org.pdflite.controller.PageRenderer;
import org.pdflite.controller.SearchDialogController;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Manages the search dialog window and its lifecycle.
 */
public class SearchDialogManager {
    private static final Logger logger = LoggerFactory.getLogger(SearchDialogManager.class);

    private final BorderPane rootPane;
    private final PageRenderer pageRenderer;
    private final ZoomManager zoomManager;
    private final RenderingManager renderingManager;
    private final UIStateManager uiStateManager;

    private ThemeManager themeManager;

    private SearchDialogController searchDialogController;
    private Stage searchDialogStage;

    /**
     * Creates a new SearchDialogManager.
     *
     * @param rootPane         the root pane for the dialog owner
     * @param pageRenderer     the page renderer
     * @param zoomManager      the zoom manager
     * @param renderingManager the rendering manager
     * @param uiStateManager   the UI state manager
     * @param themeManager     the theme manager
     */
    public SearchDialogManager(BorderPane rootPane, PageRenderer pageRenderer,
                               ZoomManager zoomManager, RenderingManager renderingManager,
                               UIStateManager uiStateManager, ThemeManager themeManager) {
        this.rootPane = rootPane;
        this.pageRenderer = pageRenderer;
        this.zoomManager = zoomManager;
        this.renderingManager = renderingManager;
        this.uiStateManager = uiStateManager;
        this.themeManager = themeManager;
    }

    /**
     * Opens or shows the search dialog.
     *
     * @param currentDocument the current PDF document
     * @param mainController  the main controller
     */
    public void openSearchDialog(PDFDocument currentDocument, MainController mainController) {
        if (currentDocument == null) {
            uiStateManager.showError("No PDF Loaded", "Please open a PDF file first");
            return;
        }

        try {
            if (searchDialogStage == null) {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/org/pdflite/search-dialog.fxml")
                );

                Parent root = loader.load();

                searchDialogController = loader.getController();
                searchDialogController.setPDFDocument(currentDocument);
                searchDialogController.setMainController(mainController);

                searchDialogStage = new Stage();
                searchDialogStage.setTitle("Search in PDF");

                Scene scene = new Scene(root);
                searchDialogStage.setScene(scene);

                if (themeManager != null) {
                    themeManager.applyThemeToScene(scene);
                }

                searchDialogStage.initOwner(rootPane.getScene().getWindow());

                searchDialogStage.setOnCloseRequest(e -> searchDialogController.cleanup());
            } else {
                searchDialogController.setPDFDocument(currentDocument);
                if (themeManager != null) {
                    themeManager.applyThemeToScene(searchDialogStage.getScene());
                }
            }

            if (pageRenderer != null) {
                pageRenderer.clearCache();
                pageRenderer.cancelAllPendingRenders();
                pageRenderer.setZoom(zoomManager.getCurrentZoom());
            }
            renderingManager.renderAllPages();
            searchDialogStage.show();
            searchDialogStage.toFront();

            logger.info("Search dialog opened");

        } catch (IOException e) {
            logger.error("Error loading search dialog", e);
            uiStateManager.showError("Error", "Could not open search dialog: " + e.getMessage());
        }
    }

    /**
     * Closes the search dialog if it's open.
     */
    public void closeSearchDialog() {
        if (searchDialogStage != null && searchDialogStage.isShowing()) {
            searchDialogStage.close();
        }
    }
    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;

        if (searchDialogStage != null) {
            themeManager.applyThemeToScene(searchDialogStage.getScene());
        }
    }

}

