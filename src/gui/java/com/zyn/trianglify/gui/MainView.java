package com.zyn.trianglify.gui;

import atlantafx.base.controls.Spacer;
import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import com.zyn.trianglify.Options;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/** Janela principal: cabeçalho, barra lateral de configuração, prévia e rodapé. */
final class MainView extends BorderPane {

    private static final double SIDEBAR_WIDTH = 320;
    /** Espera antes de regerar, para que arrastar um slider não dispare a cada pixel. */
    private static final Duration DEBOUNCE = Duration.millis(220);

    private static final NumberFormat INTEGERS =
            NumberFormat.getIntegerInstance(Locale.of("pt", "BR"));

    private final SettingsPane settings = new SettingsPane();
    private final PreviewPane preview = new PreviewPane();
    private final GeneratorService generator = new GeneratorService();
    private final PauseTransition debounce = new PauseTransition(DEBOUNCE);

    private final ToggleSwitch autoUpdate = new ToggleSwitch("Atualizar automaticamente");
    private final Button generateButton = new Button("Gerar");
    private final Label statusLabel = new Label();
    private final Label zoomLabel = new Label();

    private GeneratedPattern current;

    MainView(Image logo, Options initial) {
        getStyleClass().add("main-view");

        setTop(buildHeader(logo));
        setLeft(buildSidebar());
        setCenter(preview);
        setBottom(buildStatusBar());

        wireGenerator();

        debounce.setOnFinished(e -> generate());
        settings.setOnChange(() -> {
            if (autoUpdate.isSelected()) {
                debounce.playFromStart();
            } else {
                statusLabel.setText("Alterações pendentes — clique em Gerar.");
            }
        });

        settings.applyFrom(initial);
    }

    /** Primeira geração, disparada depois que a janela aparece. */
    void generate() {
        debounce.stop();
        try {
            generator.generate(settings.toOptions());
        } catch (RuntimeException e) {
            preview.setBusy(false);
            preview.showError("Configuração inválida: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private HBox buildHeader(Image logo) {
        ImageView mark = new ImageView(logo);
        mark.setFitWidth(34);
        mark.setFitHeight(34);
        mark.getStyleClass().add("app-mark");

        Label title = new Label("Trianglify");
        title.getStyleClass().addAll(Styles.TITLE_4);
        Label subtitle = new Label("gerador de padrões low poly");
        subtitle.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_SUBTLE);
        VBox titles = new VBox(title, subtitle);
        titles.setAlignment(Pos.CENTER_LEFT);

        ComboBox<AppTheme> themeCombo = new ComboBox<>();
        themeCombo.getItems().setAll(AppTheme.values());
        themeCombo.setValue(AppTheme.saved());
        themeCombo.setPrefWidth(180);
        themeCombo.setOnAction(e -> themeCombo.getValue().apply());

        Label themeLabel = new Label("Tema");
        themeLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);

        MenuButton file = new MenuButton("Arquivo");
        MenuItem open = new MenuItem("Abrir .properties…");
        open.setOnAction(e -> openProperties());
        MenuItem save = new MenuItem("Salvar .properties…");
        save.setOnAction(e -> saveProperties());
        file.getItems().setAll(open, save);

        HBox header = new HBox(12, mark, titles, new Spacer(),
                themeLabel, themeCombo, new Separator(Orientation.VERTICAL), file);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 20, 12, 20));
        return header;
    }

    private VBox buildSidebar() {
        ScrollPane scroll = new ScrollPane(settings);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        autoUpdate.setSelected(true);
        autoUpdate.setMaxWidth(Double.MAX_VALUE);

        generateButton.getStyleClass().addAll(Styles.ACCENT);
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setDefaultButton(true);
        generateButton.setOnAction(e -> generate());
        HBox.setHgrow(generateButton, Priority.ALWAYS);

        MenuButton export = new MenuButton("Exportar");
        MenuItem png = new MenuItem("Salvar PNG…");
        png.setOnAction(e -> exportPng());
        MenuItem svg = new MenuItem("Salvar SVG…");
        svg.setOnAction(e -> exportSvg());
        export.getItems().setAll(png, svg);

        HBox buttons = new HBox(10, generateButton, export);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox actions = new VBox(10, autoUpdate, buttons);
        actions.getStyleClass().add("sidebar-actions");
        actions.setPadding(new Insets(14, 20, 16, 20));

        VBox sidebar = new VBox(scroll, actions);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(SIDEBAR_WIDTH);
        sidebar.setMinWidth(SIDEBAR_WIDTH);
        return sidebar;
    }

    private HBox buildStatusBar() {
        statusLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        zoomLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_SUBTLE);

        HBox bar = new HBox(statusLabel, new Spacer(), zoomLabel);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 20, 8, 20));
        return bar;
    }

    // ------------------------------------------------------------------
    // Geração
    // ------------------------------------------------------------------

    private void wireGenerator() {
        generator.runningProperty().addListener((o, a, running) -> {
            preview.setBusy(running);
            generateButton.setDisable(running);
        });
        generator.setOnSucceeded(e -> {
            current = generator.getValue();
            preview.setImage(current.image());
            updateStatus();
        });
        generator.setOnFailed(e -> {
            Throwable error = generator.getException();
            preview.showError(error == null ? "Falha ao gerar o padrão."
                    : "Falha ao gerar o padrão: " + error.getMessage());
            statusLabel.setText("");
        });

        // O rótulo de zoom depende do tamanho da janela, não só da geração.
        preview.widthProperty().addListener((o, a, b) -> updateZoom());
        preview.heightProperty().addListener((o, a, b) -> updateZoom());
    }

    private void updateStatus() {
        Options o = current.options();
        statusLabel.setText(String.format("%d × %d px  ·  %s triângulos  ·  %d ms",
                o.width, o.height, INTEGERS.format(current.triangleCount()), current.elapsedMs()));
        updateZoom();
    }

    private void updateZoom() {
        double scale = preview.displayScale();
        zoomLabel.setText(scale >= 0.999 ? "tamanho real" : "exibindo a " + Math.round(scale * 100) + "%");
    }

    // ------------------------------------------------------------------
    // Arquivos
    // ------------------------------------------------------------------

    private void exportPng() {
        if (current == null) return;
        Options o = current.options();
        File file = saveDialog("Salvar PNG", "trianglify.png",
                new FileChooser.ExtensionFilter("Imagem PNG", "*.png"));
        if (file == null) return;
        try {
            // Rasteriza de novo na escala escolhida: a prévia é sempre 1×.
            ImageIO.write(current.pattern().toImage(o.outputScale), "png", file);
            statusLabel.setText("PNG salvo em " + file.getAbsolutePath());
        } catch (IOException e) {
            error("Não foi possível salvar o PNG", e.getMessage());
        }
    }

    private void exportSvg() {
        if (current == null) return;
        File file = saveDialog("Salvar SVG", "trianglify.svg",
                new FileChooser.ExtensionFilter("Imagem SVG", "*.svg"));
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), current.pattern().toSVG(), StandardCharsets.UTF_8);
            statusLabel.setText("SVG salvo em " + file.getAbsolutePath());
        } catch (IOException e) {
            error("Não foi possível salvar o SVG", e.getMessage());
        }
    }

    private void openProperties() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir configuração");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Arquivo de propriedades", "*.properties"));
        File file = chooser.showOpenDialog(window());
        if (file == null) return;
        try {
            Options loaded = Options.load(file.toPath(), Map.of());
            boolean hadPoints = loaded.points != null;
            loaded.points = null;
            settings.applyFrom(loaded);
            statusLabel.setText(hadPoints
                    ? "Configuração carregada. A lista 'points' foi ignorada: a janela sempre usa a grade."
                    : "Configuração carregada de " + file.getName());
        } catch (IOException | RuntimeException e) {
            error("Não foi possível abrir a configuração", e.getMessage());
        }
    }

    private void saveProperties() {
        File file = saveDialog("Salvar configuração", "trianglify.properties",
                new FileChooser.ExtensionFilter("Arquivo de propriedades", "*.properties"));
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), settings.toPropertiesText(), StandardCharsets.UTF_8);
            statusLabel.setText("Configuração salva em " + file.getAbsolutePath());
        } catch (IOException e) {
            error("Não foi possível salvar a configuração", e.getMessage());
        }
    }

    private File saveDialog(String title, String suggestedName, FileChooser.ExtensionFilter filter) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(suggestedName);
        chooser.getExtensionFilters().add(filter);
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.isDirectory(cwd)) chooser.setInitialDirectory(cwd.toFile());
        return chooser.showSaveDialog(window());
    }

    private void error(String header, String detail) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(window());
        alert.setTitle("Trianglify");
        alert.setHeaderText(header);
        alert.setContentText(detail);
        alert.showAndWait();
    }

    private Window window() {
        return getScene() == null ? null : getScene().getWindow();
    }

    /** Encerra a thread de geração para que a JVM possa sair. */
    void shutdown() {
        debounce.stop();
        generator.cancel();
    }
}
