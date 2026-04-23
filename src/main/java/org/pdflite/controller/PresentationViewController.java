package org.pdflite.controller;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.util.DialogTitleBar;
import org.pdflite.manager.LanguageManager;
import org.pdflite.manager.UIStateManager;
import org.pdflite.manager.ZoomManager;
import org.pdflite.model.DocumentContext;
import org.pdflite.model.PDFDocument;

import java.text.MessageFormat;
import java.util.List;
import java.util.function.Supplier;

/**
 * Controls presentation/slideshow mode for the active PDF document.
 */
public class PresentationViewController {

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final BorderPane rootPane;
    private final TabPane documentTabPane;
    private final UIStateManager uiStateManager;
    private final Supplier<DocumentContext> contextSupplier;
    private final Supplier<Stage> stageSupplier;
    private final ZoomManager zoomManager;
    private final Runnable previousPageAction;
    private final Runnable nextPageAction;

    private boolean active;
    private boolean exiting;
    private boolean topWasVisible;
    private boolean topWasManaged;
    private boolean bottomWasVisible;
    private boolean bottomWasManaged;
    private boolean sidebarWasExpanded;
    private boolean wasFullscreenBeforeEnter;

    private DocumentContext activeContext;
    private String previousPagesStyle;
    private Pos previousPagesAlignment;

    private Label progressLabel;
    private Label presenterProgressLabel;
    private Stage presenterStage;

    private ChangeListener<Number> scrollListener;
    private ChangeListener<Boolean> fullscreenListener;
    private EventHandler<KeyEvent> keyHandler;
    private EventHandler<MouseEvent> clickHandler;

    public PresentationViewController(
            BorderPane rootPane,
            TabPane documentTabPane,
            UIStateManager uiStateManager,
            Supplier<DocumentContext> contextSupplier,
            Supplier<Stage> stageSupplier,
            ZoomManager zoomManager,
            Runnable previousPageAction,
            Runnable nextPageAction
    ) {
        this.rootPane = rootPane;
        this.documentTabPane = documentTabPane;
        this.uiStateManager = uiStateManager;
        this.contextSupplier = contextSupplier;
        this.stageSupplier = stageSupplier;
        this.zoomManager = zoomManager;
        this.previousPageAction = previousPageAction;
        this.nextPageAction = nextPageAction;
    }

    public boolean isActive() {
        return active;
    }

    public void toggle() {
        if (active) {
            exit();
        } else {
            enter();
        }
    }

    public void enter() {
        if (active || rootPane == null) {
            return;
        }

        DocumentContext context = contextSupplier.get();
        Stage stage = stageSupplier.get();
        if (context == null || stage == null || context.getDocument() == null) {
            if (uiStateManager != null) {
                uiStateManager.showError(lang().getString("error.noDocument"), lang().getString("error.noPdfLoadedMsg"));
            }
            return;
        }

        active = true;
        activeContext = context;
        wasFullscreenBeforeEnter = stage.isFullScreen();

        rememberAndHideChrome();
        prepareContextForPresentation(context);
        attachProgressIndicator(context);
        attachInputHandlers();
        attachContextListeners(context, stage);
        openPresenterView(stage);

        if (!stage.isFullScreen()) {
            stage.setFullScreen(true);
        }
        Platform.runLater(() -> {
            if (active && zoomManager != null) {
                zoomManager.fitToPage();
            }
        });

        updateProgress();
        if (uiStateManager != null) {
            uiStateManager.updateStatus(lang().getString("presentation.entered"));
        }
    }

    public void exit() {
        if (!active || exiting) {
            return;
        }
        exiting = true;

        Stage stage = stageSupplier.get();

        detachInputHandlers();
        detachContextListeners();
        closePresenterView();
        removeProgressIndicator();
        restoreContext();
        restoreChrome();

        if (stage != null && stage.isFullScreen() && !wasFullscreenBeforeEnter) {
            stage.setFullScreen(false);
        }

        active = false;
        exiting = false;
        if (uiStateManager != null) {
            uiStateManager.updateStatus(lang().getString("presentation.exited"));
        }
    }

    public void navigatePrevious() {
        runNavigation(previousPageAction);
    }

    public void navigateNext() {
        runNavigation(nextPageAction);
    }

    private void runNavigation(Runnable action) {
        if (!active || action == null) {
            return;
        }
        action.run();
        Platform.runLater(this::updateProgress);
    }

    private void rememberAndHideChrome() {
        if (!rootPane.getStyleClass().contains("presentation-mode")) {
            rootPane.getStyleClass().add("presentation-mode");
        }

        if (rootPane.getTop() != null) {
            topWasVisible = rootPane.getTop().isVisible();
            topWasManaged = rootPane.getTop().isManaged();
            rootPane.getTop().setVisible(false);
            rootPane.getTop().setManaged(false);
        }
        if (rootPane.getBottom() != null) {
            bottomWasVisible = rootPane.getBottom().isVisible();
            bottomWasManaged = rootPane.getBottom().isManaged();
            rootPane.getBottom().setVisible(false);
            rootPane.getBottom().setManaged(false);
        }
    }

    private void restoreChrome() {
        rootPane.getStyleClass().remove("presentation-mode");
        if (rootPane.getTop() != null) {
            rootPane.getTop().setVisible(topWasVisible);
            rootPane.getTop().setManaged(topWasManaged);
        }
        if (rootPane.getBottom() != null) {
            rootPane.getBottom().setVisible(bottomWasVisible);
            rootPane.getBottom().setManaged(bottomWasManaged);
        }
    }

    private void prepareContextForPresentation(DocumentContext context) {
        previousPagesStyle = context.getPagesContainer().getStyle();
        previousPagesAlignment = context.getPagesContainer().getAlignment();

        context.getPagesContainer().setAlignment(Pos.TOP_CENTER);
        context.getPagesContainer().setStyle("-fx-background-color: transparent; -fx-padding: 24 80 40 80;");

        sidebarWasExpanded = !context.isSidebarCollapsed()
                && context.getSplitPane() != null
                && context.getSidebarContainer() != null
                && context.getSplitPane().getItems().contains(context.getSidebarContainer());

        if (sidebarWasExpanded) {
            if (!context.getSplitPane().getDividers().isEmpty()) {
                context.setSidebarDividerPosition(context.getSplitPane().getDividerPositions()[0]);
            }
            context.getSplitPane().getItems().remove(context.getSidebarContainer());
            context.setSidebarCollapsed(true);
        }
    }

    private void restoreContext() {
        if (activeContext == null) {
            return;
        }

        activeContext.getPagesContainer().setStyle(previousPagesStyle == null ? "" : previousPagesStyle);
        activeContext.getPagesContainer().setAlignment(previousPagesAlignment == null ? Pos.TOP_CENTER : previousPagesAlignment);

        if (sidebarWasExpanded
                && activeContext.getSplitPane() != null
                && activeContext.getSidebarContainer() != null
                && !activeContext.getSplitPane().getItems().contains(activeContext.getSidebarContainer())) {
            activeContext.getSplitPane().getItems().add(0, activeContext.getSidebarContainer());
            activeContext.setSidebarCollapsed(false);
            activeContext.getSplitPane().setDividerPositions(activeContext.getSidebarDividerPosition());
        }

        activeContext = null;
        sidebarWasExpanded = false;
    }

    private void attachProgressIndicator(DocumentContext context) {
        progressLabel = new Label();
        progressLabel.getStyleClass().add("presentation-progress-indicator");
        StackPane.setAlignment(progressLabel, Pos.BOTTOM_CENTER);
        StackPane.setMargin(progressLabel, new Insets(0, 0, 20, 0));
        context.getContentPane().getChildren().add(progressLabel);
    }

    private void removeProgressIndicator() {
        if (progressLabel != null && activeContext != null) {
            activeContext.getContentPane().getChildren().remove(progressLabel);
        }
        progressLabel = null;
    }

    private void updateProgress() {
        PDFDocument document = activeContext == null ? null : activeContext.getDocument();
        if (document == null) {
            return;
        }

        String progress = MessageFormat.format(
                lang().getString("presentation.progress"),
                document.getCurrentPage() + 1,
                document.getTotalPages()
        );

        if (progressLabel != null) {
            progressLabel.setText(progress);
        }
        if (presenterProgressLabel != null) {
            presenterProgressLabel.setText(progress);
        }
    }

    private void attachInputHandlers() {
        Scene scene = rootPane.getScene();
        if (scene == null) {
            return;
        }

        keyHandler = event -> {
            if (!active) {
                return;
            }
            if (event.getCode() == KeyCode.ESCAPE) {
                exit();
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.UP || event.getCode() == KeyCode.PAGE_UP) {
                navigatePrevious();
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.RIGHT
                    || event.getCode() == KeyCode.DOWN
                    || event.getCode() == KeyCode.PAGE_DOWN
                    || event.getCode() == KeyCode.SPACE
                    || event.getCode() == KeyCode.ENTER) {
                navigateNext();
                event.consume();
            }
        };

        clickHandler = event -> {
            if (!active) {
                return;
            }
            if (event.getButton() == MouseButton.PRIMARY) {
                navigateNext();
                event.consume();
            } else if (event.getButton() == MouseButton.SECONDARY) {
                navigatePrevious();
                event.consume();
            }
        };

        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        scene.addEventFilter(MouseEvent.MOUSE_CLICKED, clickHandler);
    }

    private void detachInputHandlers() {
        Scene scene = rootPane.getScene();
        if (scene == null) {
            return;
        }
        if (keyHandler != null) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        }
        if (clickHandler != null) {
            scene.removeEventFilter(MouseEvent.MOUSE_CLICKED, clickHandler);
        }
        keyHandler = null;
        clickHandler = null;
    }

    private void attachContextListeners(DocumentContext context, Stage stage) {
        scrollListener = (obs, oldVal, newVal) -> Platform.runLater(this::updateProgress);
        context.getScrollPane().vvalueProperty().addListener(scrollListener);

        fullscreenListener = (obs, oldVal, isFullscreen) -> {
            if (!isFullscreen && active && !exiting) {
                Platform.runLater(this::exit);
            }
        };
        stage.fullScreenProperty().addListener(fullscreenListener);
    }

    private void detachContextListeners() {
        if (activeContext != null && scrollListener != null) {
            activeContext.getScrollPane().vvalueProperty().removeListener(scrollListener);
        }
        Stage stage = stageSupplier.get();
        if (stage != null && fullscreenListener != null) {
            stage.fullScreenProperty().removeListener(fullscreenListener);
        }
        scrollListener = null;
        fullscreenListener = null;
    }

    private void openPresenterView(Stage presentationStage) {
        List<Screen> screens = Screen.getScreens();
        if (screens.size() < 2) {
            return;
        }

        Screen currentScreen = Screen.getScreensForRectangle(
                presentationStage.getX(),
                presentationStage.getY(),
                Math.max(1, presentationStage.getWidth()),
                Math.max(1, presentationStage.getHeight())
        ).stream().findFirst().orElse(Screen.getPrimary());

        Screen presenterScreen = screens.stream()
                .filter(screen -> screen != currentScreen)
                .findFirst()
                .orElse(null);
        if (presenterScreen == null) {
            return;
        }

        presenterProgressLabel = new Label();
        presenterProgressLabel.getStyleClass().add("shortcut-group-title");

        Label hintLabel = new Label(lang().getString("presentation.presenterHint"));
        hintLabel.getStyleClass().add("shortcut-subtitle");
        hintLabel.setWrapText(true);
        hintLabel.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(10, presenterProgressLabel, hintLabel);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(16));
        content.getStyleClass().add("shortcut-help-content");

        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(0, 16, 12, 16));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getChildren().add(spacer);

        presenterStage = new Stage();
        presenterStage.initStyle(StageStyle.TRANSPARENT);
        presenterStage.setAlwaysOnTop(true);
        presenterStage.setResizable(false);
        presenterStage.initOwner(presentationStage);

        VBox root = new VBox();
        root.getStyleClass().addAll("custom-info-dialog", "shortcut-help-dialog");
        DialogTitleBar titleBar = new DialogTitleBar(lang().getString("presentation.presenterTitle"), presenterStage);
        root.getChildren().addAll(titleBar.getTitleBar(), content, footer);

        Scene presenterScene = new Scene(root, 420, 140);
        if (rootPane.getScene() != null) {
            presenterScene.getStylesheets().addAll(rootPane.getScene().getStylesheets());
        }
        presenterScene.setFill(Color.TRANSPARENT);
        presenterStage.setScene(presenterScene);

        Rectangle2D bounds = presenterScreen.getVisualBounds();
        presenterStage.setX(bounds.getMinX() + 24);
        presenterStage.setY(bounds.getMinY() + 24);
        presenterStage.show();
    }

    private void closePresenterView() {
        if (presenterStage != null) {
            presenterStage.close();
        }
        presenterStage = null;
        presenterProgressLabel = null;
    }
}
