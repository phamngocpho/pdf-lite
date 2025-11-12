package org.pdflite.manager;

public class ViewModeManager {
    public enum ViewMode { SINGLE_PAGE, DOUBLE_PAGE }

    private ViewMode currentMode = ViewMode.SINGLE_PAGE;
    // Ngưỡng zoom tuyệt đối (so với 100%)
    private static final double DOUBLE_PAGE_THRESHOLD = 0.6; // <= 60% của kích thước gốc

    public ViewMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Cập nhật view mode dựa trên zoom level.
     *
     * @param zoomLevel zoom level hiện tại (1.0 = 100% kích thước gốc PDF)
     */
    public void updateViewMode(double zoomLevel) {
        ViewMode newMode;

        // Nếu zoom <= 60%, chuyển sang double page
        if (zoomLevel <= DOUBLE_PAGE_THRESHOLD) {
            newMode = ViewMode.DOUBLE_PAGE;
        } else {
            newMode = ViewMode.SINGLE_PAGE;
        }

        // Chỉ log khi mode thay đổi
        if (newMode != currentMode) {
            currentMode = newMode;
            System.out.println("View mode changed to: " + currentMode + " (zoom: " + String.format("%.2f", zoomLevel * 100) + "%)");
        }
    }

    public boolean isDoublePage() {
        return currentMode == ViewMode.DOUBLE_PAGE;
    }

    /**
     * Set mode trực tiếp (dùng cho testing hoặc manual control)
     */
    public void setViewMode(ViewMode mode) {
        this.currentMode = mode;
    }
}