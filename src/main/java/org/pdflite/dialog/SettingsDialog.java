package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pdflite.manager.LanguageManager;
import org.pdflite.manager.ThemeManager;
import org.pdflite.manager.UserPreferencesManager;
import org.pdflite.manager.UserPreferencesManager.UserPreferences;
import org.pdflite.util.DialogTitleBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Settings dialog with sidebar navigation.
 */
public class SettingsDialog {

    private static final Logger logger = LoggerFactory.getLogger(SettingsDialog.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final Stage dialog;
    private final ThemeManager themeManager;
    private final VBox contentArea;
    private final Map<String, Button> navButtons = new HashMap<>();
    private String currentSection = "general";
    private boolean confirmed = false;

    // Settings controls
    private ComboBox<String> defaultZoomCombo;
    private ComboBox<String> fitModeCombo;
    private ToggleGroup themeGroup;
    private ToggleGroup languageGroup;
    private ComboBox<String> sidebarPositionCombo;
    private CheckBox autoSaveEnabledCheck;
    private Spinner<Integer> autoSaveIntervalSpinner;
    private Spinner<Integer> maxRecentFilesSpinner;

    public SettingsDialog(ThemeManager themeManager) {
        this.themeManager = themeManager;

        dialog = new Stage();
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(lang().getString("settings.title"));

        // Main container
        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("custom-confirm-dialog");

        // Title bar (same as other dialogs)
        DialogTitleBar titleBar = new DialogTitleBar(lang().getString("settings.title"), dialog);
        
        // Content with sidebar
        HBox content = new HBox();
        content.getStyleClass().add("settings-content");
        VBox.setVgrow(content, Priority.ALWAYS);

        // Sidebar
        VBox sidebar = createSidebar();
        
        // Content area
        contentArea = new VBox();
        contentArea.getStyleClass().add("settings-main");
        HBox.setHgrow(contentArea, Priority.ALWAYS);

        content.getChildren().addAll(sidebar, contentArea);
        mainContainer.getChildren().addAll(titleBar.getTitleBar(), content);

        Scene scene = new Scene(mainContainer, 650, 450);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        
        // Apply rounded corners clip
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(scene.widthProperty());
        clip.heightProperty().bind(scene.heightProperty());
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        mainContainer.setClip(clip);
        
        dialog.setScene(scene);

        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }

        // Load current preferences and show general section
        loadCurrentPreferences();
        showSection("general");
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(2);
        sidebar.getStyleClass().add("settings-sidebar");
        sidebar.setPrefWidth(160);
        sidebar.setMinWidth(160);

        // Navigation items
        addNavItem(sidebar, "general", lang().getString("settings.nav.general"));
        addNavItem(sidebar, "appearance", lang().getString("settings.nav.appearance"));
        addNavItem(sidebar, "language", lang().getString("settings.nav.language"));
        addNavItem(sidebar, "files", lang().getString("settings.nav.files"));

        // Spacer
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);

        // Reset button
        Button resetButton = new Button(lang().getString("settings.resetDefaults"));
        resetButton.getStyleClass().add("settings-reset-btn");
        resetButton.setMaxWidth(Double.MAX_VALUE);
        resetButton.setOnAction(e -> resetToDefaults());
        sidebar.getChildren().add(resetButton);

        return sidebar;
    }

    private void addNavItem(VBox sidebar, String sectionId, String label) {
        Button navBtn = new Button(label);
        navBtn.getStyleClass().add("settings-nav-item");
        navBtn.setMaxWidth(Double.MAX_VALUE);
        navBtn.setAlignment(Pos.CENTER_LEFT);
        navBtn.setOnAction(e -> showSection(sectionId));
        
        navButtons.put(sectionId, navBtn);
        sidebar.getChildren().add(navBtn);
    }

    private void showSection(String sectionId) {
        // Update nav button states
        navButtons.values().forEach(btn -> btn.getStyleClass().remove("active"));
        if (navButtons.containsKey(sectionId)) {
            navButtons.get(sectionId).getStyleClass().add("active");
        }

        currentSection = sectionId;
        contentArea.getChildren().clear();

        switch (sectionId) {
            case "general" -> showGeneralSection();
            case "appearance" -> showAppearanceSection();
            case "language" -> showLanguageSection();
            case "files" -> showFilesSection();
        }
    }

    private void showGeneralSection() {
        VBox section = createSectionContainer();

        // Default Zoom
        VBox zoomGroup = createSettingGroup(
            lang().getString("settings.zoom.title"),
            lang().getString("settings.zoom.desc")
        );
        
        defaultZoomCombo = new ComboBox<>();
        defaultZoomCombo.getItems().addAll("50%", "75%", "100%", "125%", "150%", "200%");
        defaultZoomCombo.getStyleClass().add("settings-combo");
        defaultZoomCombo.setPrefWidth(180);
        
        zoomGroup.getChildren().add(defaultZoomCombo);
        section.getChildren().add(zoomGroup);

        // Fit Mode
        VBox fitGroup = createSettingGroup(
            lang().getString("settings.fitMode.title"),
            lang().getString("settings.fitMode.desc")
        );

        fitModeCombo = new ComboBox<>();
        fitModeCombo.getItems().addAll(
            lang().getString("settings.fitMode.none"),
            lang().getString("settings.fitMode.width"),
            lang().getString("settings.fitMode.page")
        );
        fitModeCombo.getStyleClass().add("settings-combo");
        fitModeCombo.setPrefWidth(180);

        fitGroup.getChildren().add(fitModeCombo);
        section.getChildren().add(fitGroup);

        // Sidebar Position
        VBox sidebarGroup = createSettingGroup(
            lang().getString("settings.sidebar.title"),
            lang().getString("settings.sidebar.desc")
        );

        sidebarPositionCombo = new ComboBox<>();
        sidebarPositionCombo.getItems().addAll(
            lang().getString("settings.sidebar.left"),
            lang().getString("settings.sidebar.right")
        );
        sidebarPositionCombo.getStyleClass().add("settings-combo");
        sidebarPositionCombo.setPrefWidth(180);

        sidebarGroup.getChildren().add(sidebarPositionCombo);
        section.getChildren().add(sidebarGroup);

        // Load values
        UserPreferences prefs = UserPreferencesManager.getInstance().getPreferences();
        defaultZoomCombo.setValue(String.format("%.0f%%", prefs.getDefaultZoom() * 100));
        fitModeCombo.getSelectionModel().select(getFitModeIndex(prefs.getFitMode()));
        sidebarPositionCombo.getSelectionModel().select("right".equals(prefs.getSidebarPosition()) ? 1 : 0);

        // Save on change
        defaultZoomCombo.setOnAction(e -> savePreferences());
        fitModeCombo.setOnAction(e -> savePreferences());
        sidebarPositionCombo.setOnAction(e -> savePreferences());

        addScrollWrapper(section);
    }

    private void showAppearanceSection() {
        VBox section = createSectionContainer();

        // Theme selection
        VBox themeGroupBox = createSettingGroup(
            lang().getString("settings.theme.title"),
            lang().getString("settings.theme.desc")
        );

        themeGroup = new ToggleGroup();
        VBox themeOptions = new VBox(10);

        RadioButton lightRadio = createThemeRadio("light", lang().getString("settings.theme.light"));
        RadioButton darkRadio = createThemeRadio("dark", lang().getString("settings.theme.dark"));
        RadioButton systemRadio = createThemeRadio("system", lang().getString("settings.theme.system"));

        themeOptions.getChildren().addAll(lightRadio, darkRadio, systemRadio);
        themeGroupBox.getChildren().add(themeOptions);
        section.getChildren().add(themeGroupBox);

        // Load current theme
        UserPreferences prefs = UserPreferencesManager.getInstance().getPreferences();
        String themeMode = prefs.getThemeMode();
        switch (themeMode) {
            case "LIGHT" -> lightRadio.setSelected(true);
            case "DARK" -> darkRadio.setSelected(true);
            default -> systemRadio.setSelected(true);
        }

        addScrollWrapper(section);
    }

    private RadioButton createThemeRadio(String value, String label) {
        RadioButton radio = new RadioButton(label);
        radio.setToggleGroup(themeGroup);
        radio.getStyleClass().add("settings-radio");
        radio.setUserData(value);
        radio.setOnAction(e -> {
            savePreferences();
            applyThemeImmediately(value);
        });
        return radio;
    }

    private void applyThemeImmediately(String theme) {
        if (themeManager != null) {
            switch (theme) {
                case "light" -> themeManager.setLightTheme();
                case "dark" -> themeManager.setDarkTheme();
                default -> themeManager.setSystemTheme();
            }
            themeManager.applyThemeToScene(dialog.getScene());
        }
    }

    private void showLanguageSection() {
        VBox section = createSectionContainer();

        VBox langGroup = createSettingGroup(
            lang().getString("settings.language.select"),
            lang().getString("settings.language.selectDesc")
        );

        languageGroup = new ToggleGroup();
        VBox langOptions = new VBox(10);

        RadioButton enRadio = new RadioButton("English");
        enRadio.setToggleGroup(languageGroup);
        enRadio.getStyleClass().add("settings-radio");
        enRadio.setUserData("en");
        enRadio.setOnAction(e -> {
            savePreferences();
            applyLanguageImmediately("en");
            refreshDialog();
        });

        RadioButton viRadio = new RadioButton("Tiếng Việt");
        viRadio.setToggleGroup(languageGroup);
        viRadio.getStyleClass().add("settings-radio");
        viRadio.setUserData("vi");
        viRadio.setOnAction(e -> {
            savePreferences();
            applyLanguageImmediately("vi");
            refreshDialog();
        });

        langOptions.getChildren().addAll(enRadio, viRadio);
        langGroup.getChildren().add(langOptions);
        section.getChildren().add(langGroup);

        // Load current language
        UserPreferences prefs = UserPreferencesManager.getInstance().getPreferences();
        if ("vi".equals(prefs.getLanguage())) {
            viRadio.setSelected(true);
        } else {
            enRadio.setSelected(true);
        }

        addScrollWrapper(section);
    }

    private void applyLanguageImmediately(String lang) {
        LanguageManager langManager = LanguageManager.getInstance();
        if ("vi".equals(lang)) {
            langManager.setLocale(LanguageManager.VIETNAMESE);
        } else {
            langManager.setLocale(LanguageManager.ENGLISH);
        }
    }

    private void refreshDialog() {
        // Rebuild sidebar with new language
        VBox mainContainer = (VBox) dialog.getScene().getRoot();
        HBox content = (HBox) mainContainer.getChildren().get(1);
        
        // Update title bar
        DialogTitleBar newTitleBar = new DialogTitleBar(lang().getString("settings.title"), dialog);
        mainContainer.getChildren().set(0, newTitleBar.getTitleBar());
        
        // Update sidebar
        navButtons.clear();
        VBox newSidebar = createSidebar();
        content.getChildren().set(0, newSidebar);
        
        // Re-show current section
        showSection(currentSection);
    }

    private void showFilesSection() {
        VBox section = createSectionContainer();

        // Auto-save
        VBox autoSaveGroup = createSettingGroup(
            lang().getString("settings.autosave.title"),
            lang().getString("settings.autosave.desc")
        );

        autoSaveEnabledCheck = new CheckBox(lang().getString("settings.autosave.enabled"));
        autoSaveEnabledCheck.getStyleClass().add("settings-checkbox");

        HBox intervalBox = new HBox(10);
        intervalBox.setAlignment(Pos.CENTER_LEFT);
        
        autoSaveIntervalSpinner = new Spinner<>(1, 60, 5, 1);
        autoSaveIntervalSpinner.setPrefWidth(80);
        autoSaveIntervalSpinner.getStyleClass().add("settings-spinner");
        
        Label secLabel = new Label(lang().getString("settings.autosave.seconds"));
        secLabel.getStyleClass().add("settings-label-small");

        intervalBox.getChildren().addAll(autoSaveIntervalSpinner, secLabel);

        autoSaveGroup.getChildren().addAll(autoSaveEnabledCheck, intervalBox);
        section.getChildren().add(autoSaveGroup);

        // Recent files
        VBox recentGroup = createSettingGroup(
            lang().getString("settings.recent.title"),
            lang().getString("settings.recent.desc")
        );

        HBox recentBox = new HBox(10);
        recentBox.setAlignment(Pos.CENTER_LEFT);

        Label maxLabel = new Label(lang().getString("settings.recent.maxFiles"));
        maxLabel.getStyleClass().add("settings-label-small");

        maxRecentFilesSpinner = new Spinner<>(5, 50, 10, 5);
        maxRecentFilesSpinner.setPrefWidth(80);
        maxRecentFilesSpinner.getStyleClass().add("settings-spinner");

        recentBox.getChildren().addAll(maxLabel, maxRecentFilesSpinner);
        recentGroup.getChildren().add(recentBox);
        section.getChildren().add(recentGroup);

        // Export/Import
        VBox exportGroup = createSettingGroup(
            lang().getString("settings.export.title"),
            lang().getString("settings.export.desc")
        );

        HBox exportBox = new HBox(10);
        
        Button exportBtn = new Button(lang().getString("settings.export.button"));
        exportBtn.getStyleClass().add("settings-btn");
        exportBtn.setOnAction(e -> exportSettings());

        Button importBtn = new Button(lang().getString("settings.import.button"));
        importBtn.getStyleClass().add("settings-btn");
        importBtn.setOnAction(e -> importSettings());

        exportBox.getChildren().addAll(exportBtn, importBtn);
        exportGroup.getChildren().add(exportBox);
        section.getChildren().add(exportGroup);

        // Load values
        UserPreferences prefs = UserPreferencesManager.getInstance().getPreferences();
        autoSaveEnabledCheck.setSelected(prefs.isAutoSaveEnabled());
        autoSaveIntervalSpinner.getValueFactory().setValue(prefs.getAutoSaveDelaySeconds());
        maxRecentFilesSpinner.getValueFactory().setValue(prefs.getMaxRecentFiles());

        // Save on change
        autoSaveEnabledCheck.setOnAction(e -> savePreferences());
        autoSaveIntervalSpinner.valueProperty().addListener((obs, o, n) -> savePreferences());
        maxRecentFilesSpinner.valueProperty().addListener((obs, o, n) -> savePreferences());

        addScrollWrapper(section);
    }

    private VBox createSectionContainer() {
        VBox container = new VBox(20);
        container.getStyleClass().add("settings-section");
        container.setPadding(new Insets(20));
        return container;
    }

    private VBox createSettingGroup(String title, String description) {
        VBox group = new VBox(8);
        group.getStyleClass().add("settings-group");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("settings-group-title");

        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("settings-group-desc");
        descLabel.setWrapText(true);

        group.getChildren().addAll(titleLabel, descLabel);
        return group;
    }

    private void addScrollWrapper(VBox content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        contentArea.getChildren().add(scroll);
    }

    private int getFitModeIndex(String fitMode) {
        return switch (fitMode) {
            case "fitWidth" -> 1;
            case "fitPage" -> 2;
            default -> 0;
        };
    }

    private void loadCurrentPreferences() {
        // Preferences are loaded when each section is shown
    }

    private void savePreferences() {
        UserPreferences prefs = UserPreferencesManager.getInstance().getPreferences();

        // General
        if (defaultZoomCombo != null && defaultZoomCombo.getValue() != null) {
            String zoomStr = defaultZoomCombo.getValue().replace("%", "");
            prefs.setDefaultZoom(Double.parseDouble(zoomStr) / 100.0);
        }
        if (fitModeCombo != null) {
            int idx = fitModeCombo.getSelectionModel().getSelectedIndex();
            prefs.setFitMode(switch (idx) {
                case 1 -> "fitWidth";
                case 2 -> "fitPage";
                default -> "none";
            });
        }
        if (sidebarPositionCombo != null) {
            prefs.setSidebarPosition(sidebarPositionCombo.getSelectionModel().getSelectedIndex() == 1 ? "right" : "left");
        }

        // Appearance
        if (themeGroup != null && themeGroup.getSelectedToggle() != null) {
            String theme = (String) themeGroup.getSelectedToggle().getUserData();
            prefs.setThemeMode(switch (theme) {
                case "light" -> "LIGHT";
                case "dark" -> "DARK";
                default -> "SYSTEM";
            });
        }

        // Language
        if (languageGroup != null && languageGroup.getSelectedToggle() != null) {
            prefs.setLanguage((String) languageGroup.getSelectedToggle().getUserData());
        }

        // Files
        if (autoSaveEnabledCheck != null) {
            prefs.setAutoSaveEnabled(autoSaveEnabledCheck.isSelected());
        }
        if (autoSaveIntervalSpinner != null) {
            prefs.setAutoSaveDelaySeconds(autoSaveIntervalSpinner.getValue());
        }
        if (maxRecentFilesSpinner != null) {
            prefs.setMaxRecentFiles(maxRecentFilesSpinner.getValue());
        }

        UserPreferencesManager.getInstance().savePreferences();
        confirmed = true;
    }

    private void resetToDefaults() {
        UserPreferencesManager.getInstance().resetToDefaults();
        showSection(currentSection);
        
        // Apply default theme
        if (themeManager != null) {
            themeManager.setSystemTheme();
            themeManager.applyThemeToScene(dialog.getScene());
        }
    }

    private void exportSettings() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(lang().getString("settings.export.title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        fileChooser.setInitialFileName("pdflite-settings.json");

        File file = fileChooser.showSaveDialog(dialog);
        if (file != null) {
            try {
                String json = UserPreferencesManager.getInstance().exportToJson();
                Files.writeString(file.toPath(), json);
                showInfo(lang().getString("settings.export.success"));
            } catch (Exception e) {
                logger.error("Export failed", e);
                showError(lang().getString("settings.export.failed"));
            }
        }
    }

    private void importSettings() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(lang().getString("settings.import.title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));

        File file = fileChooser.showOpenDialog(dialog);
        if (file != null) {
            try {
                String json = Files.readString(file.toPath());
                if (UserPreferencesManager.getInstance().importFromJson(json)) {
                    showSection(currentSection);
                    showInfo(lang().getString("settings.import.success"));
                } else {
                    showError(lang().getString("settings.import.failed"));
                }
            } catch (Exception e) {
                logger.error("Import failed", e);
                showError(lang().getString("settings.import.failed"));
            }
        }
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(lang().getString("dialog.info"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        if (themeManager != null) themeManager.applyThemeToDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(lang().getString("error.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        if (themeManager != null) themeManager.applyThemeToDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    public boolean showAndWait() {
        dialog.showAndWait();
        return confirmed;
    }
}
