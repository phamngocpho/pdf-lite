package org.pdflite.manager;

import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.Effect;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Manager for reading modes (Night Mode, Sepia, etc.)
 * Applies color filters to PDF pages for comfortable reading.
 */
public class ReadingModeManager {

    private static final Logger logger = LoggerFactory.getLogger(ReadingModeManager.class);

    public enum ReadingMode {
        NORMAL,      // No filter
        NIGHT,       // Dark mode - inverted colors
        SEPIA,       // Warm sepia tone
        LOW_BLUE     // Reduced blue light
    }

    private ReadingMode currentMode = ReadingMode.NORMAL;
    private final UIStateManager uiStateManager;
    private Supplier<VBox> pagesContainerSupplier;

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    public ReadingModeManager(UIStateManager uiStateManager) {
        this.uiStateManager = uiStateManager;
    }

    /**
     * Sets the supplier for getting the current pages container.
     */
    public void setPagesContainerSupplier(Supplier<VBox> supplier) {
        this.pagesContainerSupplier = supplier;
    }

    public ReadingMode getCurrentMode() {
        return currentMode;
    }

    public void setMode(ReadingMode mode) {
        this.currentMode = mode;
        logger.info("Reading mode changed to: {}", mode);
        
        String statusKey = switch (mode) {
            case NORMAL -> "readingMode.normal";
            case NIGHT -> "readingMode.night";
            case SEPIA -> "readingMode.sepia";
            case LOW_BLUE -> "readingMode.lowBlue";
        };
        uiStateManager.updateStatus(lang().getString(statusKey));
    }

    /**
     * Gets the effect to apply to PDF pages based on current reading mode.
     * @return Effect to apply, or null for normal mode
     */
    public Effect getEffect() {
        return switch (currentMode) {
            case NORMAL -> null;
            case NIGHT -> createNightModeEffect();
            case SEPIA -> createSepiaEffect();
            case LOW_BLUE -> createLowBlueEffect();
        };
    }

    /**
     * Creates night mode effect - inverts colors for dark background.
     */
    private Effect createNightModeEffect() {
        ColorAdjust colorAdjust = new ColorAdjust();
        colorAdjust.setBrightness(-0.15);
        colorAdjust.setContrast(0.1);
        colorAdjust.setSaturation(-0.1);
        
        // Invert colors using a combination
        ColorAdjust invert = new ColorAdjust();
        invert.setBrightness(-1.0);
        invert.setContrast(-1.0);
        
        return colorAdjust;
    }

    /**
     * Creates sepia effect - warm brownish tone.
     */
    private Effect createSepiaEffect() {
        ColorAdjust sepia = new ColorAdjust();
        sepia.setHue(0.05);        // Slight shift towards yellow/brown
        sepia.setSaturation(-0.3); // Reduce saturation
        sepia.setBrightness(0.05); // Slightly brighter
        sepia.setContrast(0.05);   // Slight contrast boost
        return sepia;
    }

    /**
     * Creates low blue light effect - reduces blue for eye comfort.
     */
    private Effect createLowBlueEffect() {
        ColorAdjust lowBlue = new ColorAdjust();
        lowBlue.setHue(0.08);       // Shift towards warmer colors
        lowBlue.setSaturation(0.1); // Slight saturation increase
        lowBlue.setBrightness(0);
        lowBlue.setContrast(0);
        return lowBlue;
    }

    /**
     * Cycles through reading modes.
     */
    public void cycleMode() {
        ReadingMode[] modes = ReadingMode.values();
        int nextIndex = (currentMode.ordinal() + 1) % modes.length;
        setMode(modes[nextIndex]);
    }

    /**
     * Checks if any reading mode filter is active.
     */
    public boolean isFilterActive() {
        return currentMode != ReadingMode.NORMAL;
    }

    /**
     * Applies the current reading mode effect to all visible pages.
     * Structure: VBox (pagesContainer) > VBox (pageBox) > StackPane (imageStack) > ImageView
     */
    public void applyToPages() {
        if (pagesContainerSupplier == null) {
            logger.warn("Pages container supplier not set");
            return;
        }
        
        VBox pagesContainer = pagesContainerSupplier.get();
        if (pagesContainer == null) {
            return;
        }
        
        Effect effect = getEffect();
        for (javafx.scene.Node node : pagesContainer.getChildren()) {
            if (node instanceof VBox pageBox && !pageBox.getChildren().isEmpty()) {
                javafx.scene.Node firstChild = pageBox.getChildren().getFirst();
                if (firstChild instanceof StackPane imageStack) {
                    for (javafx.scene.Node stackChild : imageStack.getChildren()) {
                        if (stackChild instanceof ImageView imageView) {
                            imageView.setEffect(effect);
                        }
                    }
                }
            }
        }
        logger.debug("Applied {} mode to all pages", currentMode);
    }
}
