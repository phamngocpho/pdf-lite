package org.pdflite.manager;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager for auto-hiding UI elements (toolbar, menubar) when not in use.
 * Uses height animation for smooth layout transitions.
 */
public class AutoHideUIManager {

    private static final Logger logger = LoggerFactory.getLogger(AutoHideUIManager.class);
    private static final double TRIGGER_ZONE_HEIGHT = 10;
    private static final double HIDE_DELAY_MS = 1500;
    private static final double ANIMATION_DURATION_MS = 200;

    private final MenuBar menuBar;
    private final ToolBar toolBar;
    private final Region rootPane;
    private TabPane tabPane;

    private boolean autoHideEnabled = false;
    private boolean isUIVisible = true;
    private boolean isAnimating = false;
    private Timeline hideTimeline;

    // Store original heights
    private double menuBarHeight = -1;
    private double toolBarHeight = -1;

    public AutoHideUIManager(MenuBar menuBar, ToolBar toolBar, Region rootPane) {
        this.menuBar = menuBar;
        this.toolBar = toolBar;
        this.rootPane = rootPane;

        setupHideTimeline();
    }

    public void setTabPane(TabPane tabPane) {
        this.tabPane = tabPane;
    }

    private void setupHideTimeline() {
        hideTimeline = new Timeline(new KeyFrame(Duration.millis(HIDE_DELAY_MS), e -> {
            if (autoHideEnabled && !isAnimating) {
                hideUI();
            }
        }));
        hideTimeline.setCycleCount(1);
    }

    public void setupSceneTracking(Scene scene) {
        if (scene == null) return;

        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, e -> {
            if (!autoHideEnabled || isAnimating) return;

            double mouseY = e.getSceneY();
            boolean isOverUIArea = isMouseOverUIArea(e.getSceneX(), e.getSceneY());

            if (mouseY <= TRIGGER_ZONE_HEIGHT || isOverUIArea) {
                hideTimeline.stop();
                if (!isUIVisible) {
                    showUI();
                }
            } else {
                if (isUIVisible) {
                    hideTimeline.stop();
                    hideTimeline.playFromStart();
                }
            }
        });

        setupComponentTracking(menuBar);
        setupComponentTracking(toolBar);

        logger.info("AutoHideUIManager: Scene tracking setup complete");
    }

    private void setupComponentTracking(Node node) {
        if (node == null) return;

        node.setOnMouseEntered(e -> {
            if (autoHideEnabled && !isAnimating) {
                hideTimeline.stop();
                if (!isUIVisible) {
                    showUI();
                }
            }
        });

        node.setOnMouseExited(e -> {
            if (autoHideEnabled && isUIVisible && !isAnimating) {
                hideTimeline.stop();
                hideTimeline.playFromStart();
            }
        });
    }

    private boolean isMouseOverUIArea(double sceneX, double sceneY) {
        if (isOverNode(menuBar, sceneX, sceneY)) return true;
        if (isOverNode(toolBar, sceneX, sceneY)) return true;
        return isOverTabHeader(sceneX, sceneY);
    }

    private boolean isOverTabHeader(double sceneX, double sceneY) {
        if (tabPane == null) return false;

        try {
            Node tabHeaderArea = tabPane.lookup(".tab-header-area");
            if (tabHeaderArea != null) {
                Bounds bounds = tabHeaderArea.localToScene(tabHeaderArea.getBoundsInLocal());
                return bounds.contains(sceneX, sceneY);
            }
        } catch (Exception e) {
            try {
                Bounds tabBounds = tabPane.localToScene(tabPane.getBoundsInLocal());
                return sceneX >= tabBounds.getMinX() && sceneX <= tabBounds.getMaxX()
                        && sceneY >= tabBounds.getMinY() && sceneY <= tabBounds.getMinY() + 35;
            } catch (Exception ex) {
                return false;
            }
        }
        return false;
    }

    private boolean isOverNode(Node node, double sceneX, double sceneY) {
        if (node == null) return false;
        // Check even if not visible (for trigger zone)
        try {
            Bounds bounds = node.localToScene(node.getBoundsInLocal());
            return bounds.contains(sceneX, sceneY);
        } catch (Exception e) {
            return false;
        }
    }

    private void captureOriginalHeights() {
        if (menuBarHeight < 0 && menuBar != null) {
            menuBarHeight = menuBar.getHeight();
            if (menuBarHeight <= 0) menuBarHeight = 25; // fallback
        }
        if (toolBarHeight < 0 && toolBar != null) {
            toolBarHeight = toolBar.getHeight();
            if (toolBarHeight <= 0) toolBarHeight = 40; // fallback
        }
    }

    private void showUI() {
        if (isUIVisible || isAnimating) return;

        isAnimating = true;
        isUIVisible = true;

        Platform.runLater(() -> {
            captureOriginalHeights();

            ParallelTransition parallel = new ParallelTransition();
            Duration duration = Duration.millis(ANIMATION_DURATION_MS);

            if (menuBar != null) {
                menuBar.setVisible(true);
                menuBar.setManaged(true);
                menuBar.setOpacity(0);

                // Animate height
                Timeline heightAnim = createHeightAnimation(menuBar, 0, menuBarHeight, duration);
                
                // Fade in
                FadeTransition fade = new FadeTransition(duration, menuBar);
                fade.setFromValue(0);
                fade.setToValue(1);
                fade.setInterpolator(Interpolator.EASE_OUT);

                parallel.getChildren().addAll(heightAnim, fade);
            }

            if (toolBar != null) {
                toolBar.setVisible(true);
                toolBar.setManaged(true);
                toolBar.setOpacity(0);

                Timeline heightAnim = createHeightAnimation(toolBar, 0, toolBarHeight, duration);

                FadeTransition fade = new FadeTransition(duration, toolBar);
                fade.setFromValue(0);
                fade.setToValue(1);
                fade.setInterpolator(Interpolator.EASE_OUT);

                parallel.getChildren().addAll(heightAnim, fade);
            }

            parallel.setOnFinished(e -> {
                // Reset to auto-size
                if (menuBar != null) {
                    menuBar.setMinHeight(Region.USE_COMPUTED_SIZE);
                    menuBar.setPrefHeight(Region.USE_COMPUTED_SIZE);
                    menuBar.setMaxHeight(Region.USE_COMPUTED_SIZE);
                }
                if (toolBar != null) {
                    toolBar.setMinHeight(Region.USE_COMPUTED_SIZE);
                    toolBar.setPrefHeight(Region.USE_COMPUTED_SIZE);
                    toolBar.setMaxHeight(Region.USE_COMPUTED_SIZE);
                }
                isAnimating = false;
            });

            parallel.play();
        });

        logger.debug("UI showing with animation");
    }

    private void hideUI() {
        if (!isUIVisible || isAnimating) return;

        isAnimating = true;
        isUIVisible = false;

        Platform.runLater(() -> {
            captureOriginalHeights();

            ParallelTransition parallel = new ParallelTransition();
            Duration duration = Duration.millis(ANIMATION_DURATION_MS);

            if (menuBar != null) {
                Timeline heightAnim = createHeightAnimation(menuBar, menuBarHeight, 0, duration);

                FadeTransition fade = new FadeTransition(duration, menuBar);
                fade.setFromValue(1);
                fade.setToValue(0);
                fade.setInterpolator(Interpolator.EASE_IN);

                parallel.getChildren().addAll(heightAnim, fade);
            }

            if (toolBar != null) {
                Timeline heightAnim = createHeightAnimation(toolBar, toolBarHeight, 0, duration);

                FadeTransition fade = new FadeTransition(duration, toolBar);
                fade.setFromValue(1);
                fade.setToValue(0);
                fade.setInterpolator(Interpolator.EASE_IN);

                parallel.getChildren().addAll(heightAnim, fade);
            }

            parallel.setOnFinished(e -> {
                if (menuBar != null) {
                    menuBar.setVisible(false);
                    menuBar.setManaged(false);
                }
                if (toolBar != null) {
                    toolBar.setVisible(false);
                    toolBar.setManaged(false);
                }
                isAnimating = false;
            });

            parallel.play();
        });

        logger.debug("UI hiding with animation");
    }

    private Timeline createHeightAnimation(Region node, double fromHeight, double toHeight, Duration duration) {
        // Set initial state
        node.setMinHeight(fromHeight);
        node.setPrefHeight(fromHeight);
        node.setMaxHeight(fromHeight);

        DoubleProperty heightProperty = new SimpleDoubleProperty(fromHeight);
        heightProperty.addListener((obs, oldVal, newVal) -> {
            double h = newVal.doubleValue();
            node.setMinHeight(h);
            node.setPrefHeight(h);
            node.setMaxHeight(h);
        });

        return new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(heightProperty, fromHeight)),
                new KeyFrame(duration, new KeyValue(heightProperty, toHeight, Interpolator.EASE_BOTH))
        );
    }

    public void toggle() {
        setEnabled(!autoHideEnabled);
    }

    public void setEnabled(boolean enabled) {
        this.autoHideEnabled = enabled;

        if (!enabled) {
            hideTimeline.stop();
            isAnimating = false;

            Platform.runLater(() -> {
                if (menuBar != null) {
                    menuBar.setVisible(true);
                    menuBar.setManaged(true);
                    menuBar.setOpacity(1.0);
                    menuBar.setMinHeight(Region.USE_COMPUTED_SIZE);
                    menuBar.setPrefHeight(Region.USE_COMPUTED_SIZE);
                    menuBar.setMaxHeight(Region.USE_COMPUTED_SIZE);
                }
                if (toolBar != null) {
                    toolBar.setVisible(true);
                    toolBar.setManaged(true);
                    toolBar.setOpacity(1.0);
                    toolBar.setMinHeight(Region.USE_COMPUTED_SIZE);
                    toolBar.setPrefHeight(Region.USE_COMPUTED_SIZE);
                    toolBar.setMaxHeight(Region.USE_COMPUTED_SIZE);
                }
                isUIVisible = true;
            });
        }

        logger.info("Auto-hide UI: {}", enabled ? "enabled" : "disabled");
    }

    public boolean isEnabled() {
        return autoHideEnabled;
    }
}
