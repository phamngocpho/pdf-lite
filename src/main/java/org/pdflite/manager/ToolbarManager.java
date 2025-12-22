package org.pdflite.manager;

import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages toolbar visibility and state.
 */
public record ToolbarManager(ToolBar toolbar, MenuItem toggleToolbarMenuItem) {

    private static final Logger logger = LoggerFactory.getLogger(ToolbarManager.class);

    /**
     * Toggles the toolbar visibility.
     */
    public void toggleToolbar() {
        if (toolbar == null) return;

        boolean isToolbarVisible = toolbar.isVisible();

        if (isToolbarVisible) {
            hideToolbar();
        } else {
            showToolbar();
        }

        logger.debug("Toolbar visibility toggled: {}", !isToolbarVisible);
    }

    /**
     * Hides the toolbar.
     */
    public void hideToolbar() {
        if (toolbar == null) return;

        toolbar.setManaged(false);
        toolbar.setVisible(false);

        if (toggleToolbarMenuItem != null) {
            toggleToolbarMenuItem.setText("Show Toolbar");
        }
    }

    /**
     * Shows the toolbar.
     */
    public void showToolbar() {
        if (toolbar == null) return;

        toolbar.setManaged(true);
        toolbar.setVisible(true);

        if (toggleToolbarMenuItem != null) {
            toggleToolbarMenuItem.setText("Hide Toolbar");
        }
    }

    /**
     * Checks if the toolbar is currently visible.
     */
    public boolean isToolbarVisible() {
        return toolbar != null && toolbar.isVisible();
    }
}
