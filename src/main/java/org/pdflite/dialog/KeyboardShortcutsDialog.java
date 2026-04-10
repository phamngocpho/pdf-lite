package org.pdflite.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.input.KeyCode;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.pdflite.manager.LanguageManager;
import org.pdflite.manager.ShortcutCatalog;
import org.pdflite.manager.ThemeManager;
import org.pdflite.util.DialogTitleBar;

import java.util.Optional;

/**
 * Displays the keyboard shortcuts cheat sheet.
 */
public final class KeyboardShortcutsDialog {
    private static Stage currentDialogStage;

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private KeyboardShortcutsDialog() {
    }

    public static void show(ThemeManager themeManager) {
        if (currentDialogStage != null && currentDialogStage.isShowing()) {
            currentDialogStage.requestFocus();
            return;
        }

        Stage dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initModality(Modality.NONE);
        dialogStage.setTitle(lang().getString("shortcuts.title"));

        Optional<Window> owner = Window.getWindows().stream().filter(Window::isFocused).findFirst();
        owner.ifPresent(dialogStage::initOwner);

        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().addAll("custom-info-dialog", "shortcut-help-dialog");

        DialogTitleBar titleBar = new DialogTitleBar(lang().getString("shortcuts.title"), dialogStage);
        mainContainer.getChildren().add(titleBar.getTitleBar());

        ScrollPane contentScroll = new ScrollPane(buildContent());
        contentScroll.setFitToWidth(true);
        contentScroll.setPrefViewportWidth(640);
        contentScroll.setPrefViewportHeight(520);
        contentScroll.getStyleClass().add("shortcut-scroll");
        VBox.setVgrow(contentScroll, Priority.ALWAYS);

        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 16, 16, 16));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeButton = new Button(lang().getString("dialog.close"));
        closeButton.setOnAction(e -> dialogStage.close());
        footer.getChildren().addAll(spacer, closeButton);

        mainContainer.getChildren().addAll(contentScroll, footer);

        Scene scene = new Scene(mainContainer, 700, 620);
        scene.setFill(Color.TRANSPARENT);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                dialogStage.close();
                event.consume();
            }
        });

        if (themeManager != null) {
            themeManager.applyThemeToScene(scene);
        }

        dialogStage.setScene(scene);
        dialogStage.setOnHidden(event -> currentDialogStage = null);
        currentDialogStage = dialogStage;
        dialogStage.show();
    }

    private static VBox buildContent() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("shortcut-help-content");

        Label subtitle = new Label(lang().getString("shortcuts.subtitle"));
        subtitle.getStyleClass().add("shortcut-subtitle");
        root.getChildren().add(subtitle);

        for (ShortcutCatalog.ShortcutGroup group : ShortcutCatalog.groups()) {
            VBox card = new VBox(8);
            card.getStyleClass().add("shortcut-group-card");
            card.setPadding(new Insets(10));

            Label title = new Label(lang().getString(group.titleKey()));
            title.getStyleClass().add("shortcut-group-title");

            GridPane rows = new GridPane();
            rows.setHgap(14);
            rows.setVgap(6);
            ColumnConstraints c1 = new ColumnConstraints();
            c1.setHgrow(Priority.ALWAYS);
            ColumnConstraints c2 = new ColumnConstraints();
            c2.setMinWidth(150);
            c2.setPrefWidth(180);
            rows.getColumnConstraints().addAll(c1, c2);

            int rowIndex = 0;
            for (ShortcutCatalog.ShortcutEntry entry : group.entries()) {
                Label actionLabel = new Label(lang().getString(entry.labelKey()));
                actionLabel.getStyleClass().add("shortcut-action-label");

                Label keyLabel = new Label(ShortcutCatalog.displayAccelerator(entry.accelerator()));
                keyLabel.getStyleClass().add("shortcut-key-label");

                HBox keyWrap = new HBox(keyLabel);
                keyWrap.setAlignment(Pos.CENTER_RIGHT);
                rows.add(actionLabel, 0, rowIndex);
                rows.add(keyWrap, 1, rowIndex);
                rowIndex++;
            }

            card.getChildren().addAll(title, rows);
            root.getChildren().add(card);
        }

        return root;
    }
}
