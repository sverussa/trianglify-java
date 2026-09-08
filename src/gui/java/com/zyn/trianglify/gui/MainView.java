package com.zyn.trianglify.gui;

import atlantafx.base.controls.Spacer;
import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.layout.InputGroup;
import atlantafx.base.theme.Styles;
import com.zyn.trianglify.Options;
import com.zyn.trianglify.anim.AnimationExporter;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
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
import java.awt.Desktop;
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

    /** O que a área central está mostrando. */
    private enum Mode {IMAGE, LIVE, FILE}

    /**
     * Teto para carregar um GIF exportado na janela.
     *
     * <p>O JavaFX decodifica o GIF animado inteiro para a memória — todos os
     * quadros descomprimidos de uma vez. Um arquivo de 12 s em 900×560 a 30 fps
     * tem 360 quadros, o que dá mais de 700 MB só de pixels. O arquivo em disco
     * não denuncia isso (ele sai com dezenas de megabytes), então a conta é feita
     * a partir do número e do tamanho dos quadros.
     */
    private static final long PREVIEW_DECODE_LIMIT = 256L * 1024 * 1024;

    /**
     * O último arquivo animado gravado. {@code image} vem preenchida quando dá
     * para exibi-lo aqui dentro; quando não dá, {@code note} explica por quê.
     */
    private record Exported(Path file, Image image, String note) {
    }

    private final SettingsPane settings = new SettingsPane();
    private final PreviewPane preview = new PreviewPane();
    private final GeneratorService generator = new GeneratorService();
    private final PauseTransition debounce = new PauseTransition(DEBOUNCE);
    private final AnimationPlayer player;

    private final ToggleSwitch autoUpdate = new ToggleSwitch("Atualizar automaticamente");
    private final Button generateButton = new Button("Gerar");
    private final MenuButton exportMenu = new MenuButton("Exportar");
    private final Label statusLabel = new Label();
    private final Label zoomLabel = new Label();

    private final ToggleButton imageMode = new ToggleButton("Imagem");
    private final ToggleButton liveMode = new ToggleButton("Animação");
    private final ToggleButton fileMode = new ToggleButton("Arquivo");
    private final Label fileLabel = new Label();
    private final Button openFileButton = new Button("Abrir");

    private final ProgressBar exportProgress = new ProgressBar(0);
    private final Button cancelExport = new Button("Cancelar");
    private final HBox exportBox = new HBox(8, exportProgress, cancelExport);

    private GeneratedPattern current;
    private Exported lastExport;
    private Task<AnimationExporter.Result> exportTask;
    private Mode mode = Mode.IMAGE;

    MainView(Image logo, Options initial) {
        getStyleClass().add("main-view");

        player = new AnimationPlayer(this::showFrame, message -> {
            selectMode(Mode.IMAGE);
            preview.showMessage("Não foi possível animar: " + message);
        });

        setTop(buildHeader(logo));
        setLeft(buildSidebar());
        setCenter(buildCenter());
        setBottom(buildStatusBar());

        wireGenerator();

        debounce.setOnFinished(e -> generate());
        settings.setOnChange(() -> {
            if (mode == Mode.LIVE) {
                player.update(settings.toOptions());
            } else if (autoUpdate.isSelected()) {
                debounce.playFromStart();
            } else {
                statusLabel.setText("Alterações pendentes — clique em Gerar.");
            }
        });
        settings.setOnAnimationChange(() -> {
            if (mode == Mode.LIVE) player.update(settings.toOptions());
        });

        settings.applyFrom(initial);
    }

    /** Primeira geração, disparada depois que a janela aparece. */
    void generate() {
        debounce.stop();
        if (mode != Mode.IMAGE) {
            // Trocar para o modo Imagem já refaz a imagem quando não há uma pronta.
            selectMode(Mode.IMAGE);
            if (generator.isRunning()) return;
        }
        requestGeneration();
    }

    private void requestGeneration() {
        try {
            generator.generate(settings.toOptions());
        } catch (RuntimeException e) {
            preview.setBusy(false);
            preview.showMessage("Configuração inválida: " + e.getMessage());
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

    private VBox buildCenter() {
        VBox center = new VBox(buildPreviewBar(), preview);
        VBox.setVgrow(preview, Priority.ALWAYS);
        return center;
    }

    /** Barra da área central: o que exibir e, quando é um arquivo, qual. */
    private HBox buildPreviewBar() {
        ToggleGroup group = new ToggleGroup();
        imageMode.setToggleGroup(group);
        liveMode.setToggleGroup(group);
        fileMode.setToggleGroup(group);
        imageMode.getStyleClass().add(Styles.LEFT_PILL);
        liveMode.getStyleClass().add(Styles.CENTER_PILL);
        fileMode.getStyleClass().add(Styles.RIGHT_PILL);
        imageMode.setSelected(true);

        liveMode.setTooltip(new Tooltip("Anima a prévia, passeando pelas sementes"));
        fileMode.setDisable(true);
        fileMode.setTooltip(new Tooltip("Mostra o último arquivo animado que você exportou"));

        // Um clique no botão já marcado não deve deixar os três desmarcados.
        group.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                old.setSelected(true);
                return;
            }
            selectMode(selected == liveMode ? Mode.LIVE
                    : selected == fileMode ? Mode.FILE : Mode.IMAGE);
        });

        fileLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        openFileButton.getStyleClass().add(Styles.BUTTON_OUTLINED);
        openFileButton.setOnAction(e -> openExportedFile());
        openFileButton.setVisible(false);
        openFileButton.setManaged(false);

        HBox bar = new HBox(10, new InputGroup(imageMode, liveMode, fileMode),
                new Spacer(), fileLabel, openFileButton);
        bar.getStyleClass().add("preview-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 20, 10, 20));
        return bar;
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

        MenuItem png = new MenuItem("Salvar PNG…");
        png.setOnAction(e -> exportPng());
        MenuItem svg = new MenuItem("Salvar SVG…");
        svg.setOnAction(e -> exportSvg());
        MenuItem gif = new MenuItem("Animação em GIF…");
        gif.setOnAction(e -> exportAnimation("gif"));
        MenuItem mp4 = new MenuItem("Animação em MP4…");
        mp4.setOnAction(e -> exportAnimation("mp4"));
        exportMenu.getItems().setAll(png, svg, new SeparatorMenuItem(), gif, mp4);

        HBox buttons = new HBox(10, generateButton, exportMenu);
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

        exportProgress.setPrefWidth(160);
        cancelExport.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        cancelExport.setOnAction(e -> {
            if (exportTask != null) exportTask.cancel();
        });
        exportBox.setAlignment(Pos.CENTER_LEFT);
        exportBox.setVisible(false);
        exportBox.setManaged(false);

        HBox bar = new HBox(12, statusLabel, new Spacer(), exportBox, zoomLabel);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 20, 8, 20));
        return bar;
    }

    // ------------------------------------------------------------------
    // Modos de exibição
    // ------------------------------------------------------------------

    private void selectMode(Mode wanted) {
        if (mode == wanted) return;
        mode = wanted;

        (wanted == Mode.LIVE ? liveMode : wanted == Mode.FILE ? fileMode : imageMode)
                .setSelected(true);

        boolean showingFile = wanted == Mode.FILE && lastExport != null;
        fileLabel.setText(showingFile ? lastExport.file().getFileName().toString() : "");
        openFileButton.setVisible(showingFile);
        openFileButton.setManaged(showingFile);

        switch (wanted) {
            case IMAGE -> {
                player.stop();
                preview.setBusy(false);
                if (current == null) {
                    requestGeneration();
                } else {
                    preview.setImage(current.image());
                    updateStatus();
                }
            }
            case LIVE -> {
                debounce.stop();
                generator.cancel();
                preview.setBusy(false);
                player.start(settings.toOptions());
                statusLabel.setText("Animando…");
            }
            case FILE -> {
                player.stop();
                preview.setBusy(false);
                showExportedFile();
            }
        }
    }

    /** Um quadro pronto da animação, vindo do {@link AnimationPlayer}. */
    private void showFrame(AnimationPlayer.Frame frame) {
        if (mode != Mode.LIVE) return;
        preview.setImage(frame.image());
        statusLabel.setText(String.format("%.1f s de %.1f s  ·  %s triângulos  ·  prévia a %.0f fps",
                frame.seconds(), frame.cycleSeconds(),
                INTEGERS.format(frame.triangles()), frame.fps()));
        updateZoom();
    }

    private void showExportedFile() {
        if (lastExport == null) {
            preview.showMessage("Exporte uma animação para vê-la aqui.");
            return;
        }
        if (lastExport.image() != null) {
            preview.setImage(lastExport.image());
            updateZoom();
        } else {
            preview.showMessage(lastExport.note()
                    + "\n\nUse o botão Abrir para vê-lo no programa do sistema.");
        }
    }

    private void openExportedFile() {
        if (lastExport == null) return;
        File file = lastExport.file().toFile();
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            error("Não foi possível abrir o arquivo", "O sistema não expõe essa ação. "
                    + "O arquivo está em " + file);
            return;
        }
        // open() pode demorar a devolver enquanto o outro programa sobe.
        Thread opener = new Thread(() -> {
            try {
                Desktop.getDesktop().open(file);
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() ->
                        error("Não foi possível abrir o arquivo", String.valueOf(e.getMessage())));
            }
        }, "trianglify-open");
        opener.setDaemon(true);
        opener.start();
    }

    // ------------------------------------------------------------------
    // Geração
    // ------------------------------------------------------------------

    private void wireGenerator() {
        generator.runningProperty().addListener((o, a, running) -> {
            if (mode == Mode.IMAGE) preview.setBusy(running);
            generateButton.setDisable(running);
        });
        generator.setOnSucceeded(e -> {
            current = generator.getValue();
            if (mode != Mode.IMAGE) return;
            preview.setImage(current.image());
            updateStatus();
        });
        generator.setOnFailed(e -> {
            if (mode != Mode.IMAGE) return;
            Throwable error = generator.getException();
            preview.showMessage(error == null ? "Falha ao gerar o padrão."
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

    /**
     * Grava a animação inteira num arquivo, fora da thread da interface.
     *
     * <p>A prévia animada é pausada durante a gravação: ela e a exportação
     * disputariam os mesmos núcleos, e o que interessa nesse momento é o arquivo
     * ficar pronto rápido.
     */
    private void exportAnimation(String format) {
        if (exportTask != null && exportTask.isRunning()) return;

        File file = saveDialog("Exportar animação em " + format.toUpperCase(),
                "trianglify-anim." + format,
                new FileChooser.ExtensionFilter(
                        format.equals("gif") ? "GIF animado" : "Vídeo MP4", "*." + format));
        if (file == null) return;

        runExport(file.toPath(), format);
    }

    /** A exportação em si, já com o destino escolhido. */
    void runExport(Path target, String format) {
        Options opts = settings.toOptions();
        opts.animFormat = format;

        boolean wasLive = mode == Mode.LIVE;
        if (wasLive) player.stop();

        Task<AnimationExporter.Result> task = new Task<>() {
            @Override
            protected AnimationExporter.Result call() throws Exception {
                return AnimationExporter.export(opts, target, (done, total) -> {
                    updateProgress(done, total);
                    return !isCancelled();
                });
            }
        };
        exportTask = task;

        exportProgress.progressProperty().bind(task.progressProperty());
        setExporting(true);
        statusLabel.setText("Gerando a animação…");

        task.setOnSucceeded(e -> {
            finishExport(wasLive);
            AnimationExporter.Result result = task.getValue();
            lastExport = viewable(result, format);
            fileMode.setDisable(false);
            selectMode(Mode.FILE);
            // Depois de trocar de modo, senão o resumo seria sobrescrito.
            statusLabel.setText(String.format(
                    "%s  ·  %d quadros  ·  %.1f s  ·  %.1f MB  ·  %s  ·  %d ms",
                    result.file().getFileName(), result.frames(), result.seconds(),
                    result.fileSize() / 1_048_576.0, result.encoder(), result.elapsedMs()));
        });
        task.setOnCancelled(e -> {
            finishExport(wasLive);
            statusLabel.setText("Exportação cancelada.");
        });
        task.setOnFailed(e -> {
            finishExport(wasLive);
            Throwable cause = task.getException();
            statusLabel.setText("");
            error("Não foi possível exportar a animação",
                    cause == null ? "erro desconhecido" : String.valueOf(cause.getMessage()));
        });

        Thread thread = new Thread(task, "trianglify-export");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Carrega o arquivo gravado quando dá para exibi-lo aqui dentro, o que quer
     * dizer um GIF que caiba na memória. Vídeo precisaria do módulo
     * {@code javafx.media}, que este projeto não carrega.
     */
    private static Exported viewable(AnimationExporter.Result result, String format) {
        Path file = result.file();
        if (!"gif".equals(format)) {
            return new Exported(file, null, "O MP4 não é exibido aqui dentro.\n" + file);
        }

        long decoded = (long) result.frames() * result.width() * result.height() * 4;
        if (decoded > PREVIEW_DECODE_LIMIT) {
            return new Exported(file, null, String.format(
                    "O GIF ficou grande demais para abrir na janela: %d quadros de %d × %d "
                            + "ocupariam cerca de %d MB descomprimidos.\n%s",
                    result.frames(), result.width(), result.height(), decoded / 1_048_576, file));
        }

        try {
            Image image = new Image(file.toUri().toString());
            return image.isError()
                    ? new Exported(file, null, "Não foi possível abrir o GIF na janela.\n" + file)
                    : new Exported(file, image, null);
        } catch (RuntimeException e) {
            return new Exported(file, null, "Não foi possível abrir o GIF na janela.\n" + file);
        }
    }

    private void setExporting(boolean exporting) {
        exportBox.setVisible(exporting);
        exportBox.setManaged(exporting);
        exportMenu.setDisable(exporting);
        generateButton.setDisable(exporting);
        imageMode.setDisable(exporting);
        liveMode.setDisable(exporting);
    }

    private void finishExport(boolean resumeLive) {
        exportProgress.progressProperty().unbind();
        exportProgress.setProgress(0);
        setExporting(false);
        exportTask = null;
        if (resumeLive && mode == Mode.LIVE) player.start(settings.toOptions());
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

    /** Encerra as threads de trabalho para que a JVM possa sair. */
    void shutdown() {
        debounce.stop();
        player.stop();
        if (exportTask != null) exportTask.cancel();
        generator.cancel();
    }
}
