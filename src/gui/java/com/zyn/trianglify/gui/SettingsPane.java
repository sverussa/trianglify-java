package com.zyn.trianglify.gui;

import atlantafx.base.controls.Spacer;
import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.layout.InputGroup;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.IntegerStringConverter;
import com.zyn.trianglify.Options;
import com.zyn.trianglify.anim.Animation;
import com.zyn.trianglify.anim.AnimationExporter;
import com.zyn.trianglify.color.ColorBrewer;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Barra lateral de configuração: um controle para cada parâmetro do gerador.
 *
 * <p>Os parâmetros que não têm controle aqui ({@code svg.*}, {@code output.file},
 * {@code points}) são preservados em {@link #base} para que abrir e salvar um
 * {@code .properties} não descarte o que o usuário tinha configurado na mão.
 */
final class SettingsPane extends VBox {

    private static final String[] SEED_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789".split("");

    // geometria
    private final Spinner<Integer> widthSpinner = intSpinner(64, 8192, 1200, 20);
    private final Spinner<Integer> heightSpinner = intSpinner(64, 8192, 800, 20);
    private final ComboBox<Preset> presetCombo = new ComboBox<>();
    private final Slider cellSizeSlider = new Slider(5, 250, 75);
    private final Slider varianceSlider = new Slider(0, 1, 0.75);
    private final TextField seedField = new TextField();

    // cor
    private final ComboBox<String> xColorsCombo = new ComboBox<>();
    private final ComboBox<String> yColorsCombo = new ComboBox<>();
    private final Region xStrip = strip();
    private final Region yStrip = strip();
    private final ComboBox<String> colorSpaceCombo = new ComboBox<>();
    private final ComboBox<ColorFn> colorFunctionCombo = new ComboBox<>();
    private final Label colorFnArgLabel = new Label();
    private final Slider colorFnArgSlider = new Slider(0, 1, 0.5);

    // traço
    private final ToggleSwitch fillSwitch = new ToggleSwitch("Preencher os triângulos");
    private final Slider strokeWidthSlider = new Slider(0, 8, 0);

    // animação
    private final Slider seedCountSlider = new Slider(2, 12, 6);
    private final Slider transitionSlider = new Slider(0.3, 8, 2.5);
    private final Slider holdSlider = new Slider(0, 4, 0.8);
    private final Slider speedSlider = new Slider(0.25, 4, 1);
    private final Label cycleLabel = new Label();

    // vídeo
    private final Slider durationSlider = new Slider(1, Options.MAX_ANIM_DURATION, 12);
    private final Slider fpsSlider = new Slider(10, 60, 30);
    private final Slider animScaleSlider = new Slider(0.25, 2, 1);
    private final Label videoLabel = new Label();

    // exportação
    private final Spinner<Double> outputScaleSpinner = scaleSpinner();

    /** Opções sem controle na tela, mantidas como vieram do arquivo. */
    private Options base = new Options();

    /** Evita disparar uma regeração a cada campo enquanto {@link #applyFrom} roda. */
    private boolean updating;

    private Runnable onChange = () -> {
    };

    private Runnable onAnimationChange = () -> {
    };

    SettingsPane() {
        getStyleClass().add("settings-pane");
        setSpacing(6);
        setPadding(new Insets(18, 20, 24, 20));
        setFillWidth(true);

        buildDimensions();
        buildGeometry();
        buildSeed();
        buildColors();
        buildStroke();
        buildAnimation();
        buildExport();

        wireChangeListeners();
        applyFrom(new Options());
    }

    /** Chamado quando muda algo que altera a imagem. */
    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * Chamado quando muda só o ritmo da animação. Fica separado do
     * {@link #setOnChange} porque mexer na velocidade não precisa refazer a
     * imagem parada — basta a prévia animada adotar o novo tempo.
     */
    void setOnAnimationChange(Runnable onAnimationChange) {
        this.onAnimationChange = onAnimationChange;
    }

    // ------------------------------------------------------------------
    // Seções
    // ------------------------------------------------------------------

    private void buildDimensions() {
        section("Dimensões");

        presetCombo.getItems().setAll(Preset.values());
        presetCombo.setMaxWidth(Double.MAX_VALUE);
        presetCombo.setValue(Preset.CUSTOM);
        presetCombo.valueProperty().addListener((obs, old, preset) -> {
            if (preset == null || preset == Preset.CUSTOM || updating) return;
            widthSpinner.getValueFactory().setValue(preset.width);
            heightSpinner.getValueFactory().setValue(preset.height);
        });
        add(presetCombo);

        HBox wh = new HBox(10, labeled("Largura", widthSpinner), labeled("Altura", heightSpinner));
        HBox.setHgrow(wh.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(wh.getChildren().get(1), Priority.ALWAYS);
        add(wh);
    }

    private void buildGeometry() {
        section("Malha");

        cellSizeSlider.setBlockIncrement(5);
        add(sliderRow("Tamanho da célula", cellSizeSlider, v -> Math.round(v) + " px"));
        add(hint("Células menores geram mais triângulos."));

        add(sliderRow("Variação", varianceSlider, v -> String.format("%.2f", v)));
        add(hint("0 mantém a grade regular; 1 espalha os pontos ao máximo."));
    }

    private void buildSeed() {
        section("Semente");

        seedField.setPromptText("aleatória a cada geração");
        HBox.setHgrow(seedField, Priority.ALWAYS);

        // O estilo BUTTON_ICON é "graphic-only", então o símbolo precisa vir como
        // gráfico — o texto do botão seria descartado pelo tema.
        Button dice = new Button();
        Label diceGlyph = new Label("↻");
        diceGlyph.getStyleClass().add("glyph");
        dice.setGraphic(diceGlyph);
        dice.getStyleClass().add(Styles.BUTTON_ICON);
        dice.setTooltip(new Tooltip("Sortear uma nova semente"));
        dice.setOnAction(e -> seedField.setText(randomSeed()));

        InputGroup group = new InputGroup(seedField, dice);
        seedField.getStyleClass().add(Styles.LEFT_PILL);
        dice.getStyleClass().add(Styles.RIGHT_PILL);
        add(group);
        add(hint("A mesma semente reproduz exatamente o mesmo padrão."));
    }

    private void buildColors() {
        section("Cores");

        configureColorCombo(xColorsCombo, Palettes.X_OPTIONS);
        configureColorCombo(yColorsCombo, Palettes.Y_OPTIONS);

        add(new Label("Eixo horizontal"));
        add(xColorsCombo);
        add(xStrip);

        add(new Label("Eixo vertical"));
        add(yColorsCombo);
        add(yStrip);
        add(hint("\"match\" reaproveita a paleta do eixo horizontal. "
                + "Também aceita uma lista de hexadecimais: #fff,#08f,#003."));

        colorSpaceCombo.getItems().setAll("lab", "rgb");
        colorSpaceCombo.setMaxWidth(Double.MAX_VALUE);
        add(new Label("Espaço de interpolação"));
        add(colorSpaceCombo);

        colorFunctionCombo.getItems().setAll(ColorFn.values());
        colorFunctionCombo.setMaxWidth(Double.MAX_VALUE);
        colorFunctionCombo.valueProperty().addListener((obs, old, fn) -> {
            if (fn == null) return;
            colorFnArgLabel.setText(fn.argLabel);
            if (!updating && old != null) colorFnArgSlider.setValue(fn.argDefault);
        });
        add(new Label("Função de cor"));
        add(colorFunctionCombo);

        add(sliderRow(colorFnArgLabel, colorFnArgSlider, v -> String.format("%.2f", v)));
    }

    private void buildStroke() {
        section("Traço");

        fillSwitch.setLabelPosition(HorizontalDirection.LEFT);
        fillSwitch.setMaxWidth(Double.MAX_VALUE);
        add(fillSwitch);

        strokeWidthSlider.setBlockIncrement(0.5);
        add(sliderRow("Espessura do contorno", strokeWidthSlider, v -> String.format("%.1f px", v)));
        add(hint("Com o preenchimento desligado e uma espessura acima de zero, "
                + "o resultado vira um wireframe."));
    }

    private void buildAnimation() {
        section("Animação");

        add(sliderRow("Sementes visitadas", seedCountSlider, v -> Math.round(v) + " sementes"));
        add(hint("A malha passeia por estas sementes e volta à primeira. Como a grade tem sempre "
                + "as mesmas linhas e colunas, cada ponto se desloca até a posição correspondente "
                + "na semente seguinte."));

        add(sliderRow("Duração da transição", transitionSlider, v -> String.format("%.1f s", v)));
        add(sliderRow("Pausa em cada semente", holdSlider, v -> String.format("%.1f s", v)));
        add(sliderRow("Velocidade", speedSlider, v -> String.format("%.2f×", v)));
        add(hint("A velocidade divide os dois tempos acima, para acelerar tudo de uma vez."));

        cycleLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        cycleLabel.setWrapText(true);
        add(cycleLabel);

        section("Vídeo");

        add(sliderRow("Duração do arquivo", durationSlider, v -> String.format("%.0f s", v)));
        add(sliderRow("Quadros por segundo", fpsSlider, v -> Math.round(v) + " fps"));
        add(sliderRow("Escala do vídeo", animScaleSlider, v -> String.format("%.2f×", v)));

        videoLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_SUBTLE);
        videoLabel.setWrapText(true);
        add(videoLabel);
    }

    private void buildExport() {
        section("Exportação");

        outputScaleSpinner.setMaxWidth(Double.MAX_VALUE);
        add(labeled("Escala do PNG", outputScaleSpinner));
        add(hint("Multiplica a resolução do arquivo salvo. "
                + "A pré-visualização é sempre em 1×."));
    }

    // ------------------------------------------------------------------
    // Conversão de/para Options
    // ------------------------------------------------------------------

    /** Monta um {@link Options} novo a partir do estado atual dos controles. */
    Options toOptions() {
        Options o = new Options();

        o.width = widthSpinner.getValue();
        o.height = heightSpinner.getValue();
        o.cellSize = Math.round(cellSizeSlider.getValue());
        o.variance = varianceSlider.getValue();
        String seed = seedField.getText() == null ? "" : seedField.getText().trim();
        o.seed = seed.isEmpty() ? null : seed;
        o.points = null;

        o.xColors = xColorsCombo.getValue();
        o.yColors = yColorsCombo.getValue();
        o.colorSpace = colorSpaceCombo.getValue();
        o.colorFunction = colorFunctionCombo.getValue().key;
        o.colorFunctionArg = colorFnArgSlider.getValue();

        o.fill = fillSwitch.isSelected();
        o.strokeWidth = strokeWidthSlider.getValue();

        o.outputScale = outputScaleSpinner.getValue();

        o.animSeedCount = (int) Math.round(seedCountSlider.getValue());
        o.animTransition = transitionSlider.getValue();
        o.animHold = holdSlider.getValue();
        o.animSpeed = speedSlider.getValue();
        o.animDuration = Math.round(durationSlider.getValue());
        o.animFps = (int) Math.round(fpsSlider.getValue());
        o.animScale = animScaleSlider.getValue();

        // preservados do arquivo carregado
        o.outputFile = base.outputFile;
        o.outputFormat = base.outputFormat;
        o.svgCoordinateDecimals = base.svgCoordinateDecimals;
        o.svgIncludeNamespace = base.svgIncludeNamespace;
        // A lista explícita de sementes não tem controle aqui, mas é preservada:
        // quando o arquivo traz uma, ela manda e o controle de quantidade fica
        // desligado. O formato e o destino do arquivo animado vêm do menu Exportar.
        o.animSeeds = base.animSeeds;
        o.animFormat = base.animFormat;
        o.animFile = base.animFile;
        o.animEncoder = base.animEncoder;

        return o;
    }

    /** Preenche os controles a partir de um {@link Options} (arquivo carregado). */
    void applyFrom(Options o) {
        updating = true;
        try {
            base = o;

            widthSpinner.getValueFactory().setValue(o.width);
            heightSpinner.getValueFactory().setValue(o.height);
            presetCombo.setValue(Preset.matching(o.width, o.height));
            cellSizeSlider.setValue(clamp(o.cellSize, cellSizeSlider));
            varianceSlider.setValue(clamp(o.variance, varianceSlider));
            seedField.setText(o.seed == null ? "" : o.seed);

            selectOrAdd(xColorsCombo, o.xColors);
            selectOrAdd(yColorsCombo, o.yColors);
            colorSpaceCombo.setValue(o.colorSpace);

            ColorFn fn = ColorFn.of(o.colorFunction);
            colorFunctionCombo.setValue(fn);
            colorFnArgLabel.setText(fn.argLabel);
            colorFnArgSlider.setValue(clamp(
                    o.colorFunctionArg == null ? fn.argDefault : o.colorFunctionArg, colorFnArgSlider));

            fillSwitch.setSelected(o.fill);
            strokeWidthSlider.setValue(clamp(o.strokeWidth, strokeWidthSlider));

            outputScaleSpinner.getValueFactory().setValue(clampScale(o.outputScale));

            seedCountSlider.setValue(clamp(o.animSeedCount, seedCountSlider));
            seedCountSlider.setDisable(o.animSeeds != null && !o.animSeeds.isBlank());
            transitionSlider.setValue(clamp(o.animTransition, transitionSlider));
            holdSlider.setValue(clamp(o.animHold, holdSlider));
            speedSlider.setValue(clamp(o.animSpeed, speedSlider));
            durationSlider.setValue(clamp(o.animDuration, durationSlider));
            fpsSlider.setValue(clamp(o.animFps, fpsSlider));
            animScaleSlider.setValue(clamp(o.animScale, animScaleSlider));
        } finally {
            updating = false;
        }
        refreshStrips();
        refreshAnimationSummary();
        onChange.run();
    }

    /** Serializa o estado atual no formato do {@code trianglify.properties}. */
    String toPropertiesText() {
        Options o = toOptions();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("width", String.valueOf(o.width));
        p.put("height", String.valueOf(o.height));
        p.put("cellSize", trim(o.cellSize));
        p.put("variance", trim(o.variance));
        p.put("seed", o.seed == null ? "" : o.seed);
        p.put("xColors", o.xColors);
        p.put("yColors", o.yColors);
        p.put("colorSpace", o.colorSpace);
        p.put("colorFunction", o.colorFunction);
        p.put("colorFunction.arg", trim(o.colorFunctionArg));
        p.put("fill", String.valueOf(o.fill));
        p.put("strokeWidth", trim(o.strokeWidth));
        p.put("output.file", o.outputFile);
        p.put("output.format", o.outputFormat);
        p.put("output.scale", trim(o.outputScale));
        p.put("svg.coordinateDecimals", String.valueOf(o.svgCoordinateDecimals));
        p.put("svg.includeNamespace", String.valueOf(o.svgIncludeNamespace));
        p.put("anim.seeds", o.animSeeds == null ? "" : o.animSeeds);
        p.put("anim.seedCount", String.valueOf(o.animSeedCount));
        p.put("anim.transition", trim(round(o.animTransition, 2)));
        p.put("anim.hold", trim(round(o.animHold, 2)));
        p.put("anim.speed", trim(round(o.animSpeed, 2)));
        p.put("anim.duration", trim(o.animDuration));
        p.put("anim.fps", String.valueOf(o.animFps));
        p.put("anim.scale", trim(round(o.animScale, 2)));
        p.put("anim.format", o.animFormat);
        p.put("anim.file", o.animFile);
        p.put("anim.encoder", o.animEncoder);

        StringBuilder sb = new StringBuilder("# Gerado pela interface gráfica do trianglify-java.\n");
        sb.append("# Serve tanto para a janela quanto para a linha de comando:\n");
        sb.append("#   ./run.sh --config=este-arquivo.properties\n\n");
        p.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Infraestrutura
    // ------------------------------------------------------------------

    private void wireChangeListeners() {
        Runnable fire = () -> {
            if (updating) return;
            refreshStrips();
            refreshAnimationSummary();
            onChange.run();
        };

        widthSpinner.valueProperty().addListener((o, a, b) -> {
            if (!updating) presetCombo.setValue(Preset.matching(widthSpinner.getValue(), heightSpinner.getValue()));
            fire.run();
        });
        heightSpinner.valueProperty().addListener((o, a, b) -> {
            if (!updating) presetCombo.setValue(Preset.matching(widthSpinner.getValue(), heightSpinner.getValue()));
            fire.run();
        });
        cellSizeSlider.valueProperty().addListener((o, a, b) -> fire.run());
        varianceSlider.valueProperty().addListener((o, a, b) -> fire.run());
        seedField.textProperty().addListener((o, a, b) -> fire.run());
        xColorsCombo.valueProperty().addListener((o, a, b) -> fire.run());
        yColorsCombo.valueProperty().addListener((o, a, b) -> fire.run());
        colorSpaceCombo.valueProperty().addListener((o, a, b) -> fire.run());
        colorFunctionCombo.valueProperty().addListener((o, a, b) -> fire.run());
        colorFnArgSlider.valueProperty().addListener((o, a, b) -> fire.run());
        fillSwitch.selectedProperty().addListener((o, a, b) -> fire.run());
        strokeWidthSlider.valueProperty().addListener((o, a, b) -> fire.run());
        // A escala só afeta o arquivo salvo, então não precisa regerar a prévia.

        // O ritmo da animação não mexe na imagem parada: avisa só quem está tocando.
        Runnable retime = () -> {
            if (updating) return;
            refreshAnimationSummary();
            onAnimationChange.run();
        };
        seedCountSlider.valueProperty().addListener((o, a, b) -> retime.run());
        transitionSlider.valueProperty().addListener((o, a, b) -> retime.run());
        holdSlider.valueProperty().addListener((o, a, b) -> retime.run());
        speedSlider.valueProperty().addListener((o, a, b) -> retime.run());

        // Estes três só entram na hora de gravar o arquivo: nem a imagem nem a
        // prévia animada mudam, só o resumo do que vai sair.
        Runnable resummarize = () -> {
            if (!updating) refreshAnimationSummary();
        };
        durationSlider.valueProperty().addListener((o, a, b) -> resummarize.run());
        fpsSlider.valueProperty().addListener((o, a, b) -> resummarize.run());
        animScaleSlider.valueProperty().addListener((o, a, b) -> resummarize.run());
    }

    /**
     * Atualiza os dois resumos da seção de animação: o tempo da volta completa e
     * o que vai sair no arquivo.
     */
    private void refreshAnimationSummary() {
        Options o = toOptions();
        double cycle = Animation.cycleSeconds(o);

        String seeds = o.animSeeds == null || o.animSeeds.isBlank()
                ? ""
                : "  ·  usando a lista anim.seeds do arquivo";
        cycleLabel.setText(String.format("Volta completa: %.1f s%s", cycle, seeds));

        int frames = AnimationExporter.frameCount(o);
        int[] size = AnimationExporter.frameSize(o);
        double aligned = Animation.alignToCycle(o.animDuration, cycle);
        String loop = Math.abs(aligned - o.animDuration) < 0.05
                ? "o fim emenda no começo"
                : String.format("com %.0f s o fim emendaria no começo", aligned);
        videoLabel.setText(String.format("%d quadros  ·  %d × %d px  ·  %s",
                frames, size[0], size[1], loop));
    }

    /** Atualiza as faixas que mostram as paletas efetivamente em uso. */
    private void refreshStrips() {
        String seed = seedField.getText() == null ? "" : seedField.getText().trim();
        String[][] resolved = Palettes.resolve(xColorsCombo.getValue(), yColorsCombo.getValue(), seed);
        if (resolved == null) {
            xStrip.setBackground(null);
            yStrip.setBackground(null);
            return;
        }
        String space = colorSpaceCombo.getValue();
        paint(xStrip, resolved[0], space);
        paint(yStrip, resolved[1], space);
    }

    private static void paint(Region strip, String[] colors, String colorSpace) {
        try {
            strip.setBackground(new Background(new BackgroundFill(
                    Palettes.gradient(colors, colorSpace), new CornerRadii(4), Insets.EMPTY)));
        } catch (RuntimeException e) {
            strip.setBackground(null);
        }
    }

    private void configureColorCombo(ComboBox<String> combo, List<String> options) {
        combo.getItems().setAll(options);
        combo.setEditable(true);
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setVisibleRowCount(12);
        combo.setCellFactory(list -> new PaletteCell());
    }

    /**
     * Seleciona um valor no combo, acrescentando-o à lista se vier de um arquivo
     * com uma lista de hexadecimais em vez de um nome de paleta.
     */
    private static void selectOrAdd(ComboBox<String> combo, String value) {
        if (value != null && !combo.getItems().contains(value)) {
            combo.getItems().add(value);
        }
        combo.setValue(value);
    }

    /** Célula do combo de paletas: o nome com uma amostra do gradiente ao lado. */
    private final class PaletteCell extends ListCell<String> {
        private final Region swatch = new Region();
        private final Label name = new Label();
        private final HBox box = new HBox(10, swatch, name);

        PaletteCell() {
            swatch.setMinSize(44, 14);
            swatch.setPrefSize(44, 14);
            swatch.getStyleClass().add("palette-swatch");
            box.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            name.setText(item);
            String[] colors = ColorBrewer.PALETTES.get(item);
            if (colors == null) {
                // "random" e "match" não têm paleta fixa
                swatch.setBackground(null);
                swatch.setVisible(false);
            } else {
                swatch.setVisible(true);
                paint(swatch, colors, colorSpaceCombo.getValue() == null ? "lab" : colorSpaceCombo.getValue());
            }
            setGraphic(box);
        }
    }

    // ------------------------------------------------------------------
    // Construtores de controles
    // ------------------------------------------------------------------

    private void section(String title) {
        if (!getChildren().isEmpty()) {
            Separator sep = new Separator();
            VBox.setMargin(sep, new Insets(14, 0, 8, 0));
            getChildren().add(sep);
        }
        Label label = new Label(title.toUpperCase());
        label.getStyleClass().addAll(Styles.TEXT_CAPTION, Styles.TEXT_BOLD, Styles.TEXT_MUTED);
        VBox.setMargin(label, new Insets(0, 0, 4, 0));
        getChildren().add(label);
    }

    private void add(Node node) {
        VBox.setMargin(node, new Insets(2, 0, 2, 0));
        getChildren().add(node);
    }

    private static Node labeled(String text, Node control) {
        Label label = new Label(text);
        label.getStyleClass().add(Styles.TEXT_SMALL);
        if (control instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(4, label, control);
        box.setFillWidth(true);
        return box;
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_SUBTLE);
        label.setWrapText(true);
        return label;
    }

    private static Node sliderRow(String title, Slider slider, ValueFormat format) {
        return sliderRow(new Label(title), slider, format);
    }

    private static Node sliderRow(Label title, Slider slider, ValueFormat format) {
        Label value = new Label(format.apply(slider.getValue()));
        value.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        slider.valueProperty().addListener((o, a, b) -> value.setText(format.apply(b.doubleValue())));

        title.getStyleClass().add(Styles.TEXT_SMALL);
        HBox header = new HBox(title, new Spacer(), value);
        header.setAlignment(Pos.CENTER_LEFT);

        slider.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(2, header, slider);
        box.setFillWidth(true);
        return box;
    }

    private static Region strip() {
        Region r = new Region();
        r.getStyleClass().add("palette-strip");
        r.setMinHeight(10);
        r.setPrefHeight(10);
        r.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(r, new Insets(2, 0, 6, 0));
        return r;
    }

    private static Spinner<Integer> intSpinner(int min, int max, int initial, int step) {
        Spinner<Integer> spinner = new Spinner<>(min, max, initial, step);
        spinner.setEditable(true);
        // Comita o valor digitado ao perder o foco, em vez de reverter em silêncio.
        IntegerStringConverter.createFor(spinner);
        return spinner;
    }

    private static Spinner<Double> scaleSpinner() {
        var factory = new SpinnerValueFactory.DoubleSpinnerValueFactory(0.5, 8, 1, 0.5);
        factory.setConverter(new StringConverter<>() {
            @Override
            public String toString(Double v) {
                return v == null ? "" : trim(v) + "×";
            }

            @Override
            public Double fromString(String s) {
                return Double.valueOf(s.replace("×", "").trim());
            }
        });
        return new Spinner<>(factory);
    }

    private static double clamp(double v, Slider slider) {
        return Math.max(slider.getMin(), Math.min(slider.getMax(), v));
    }

    private static double clampScale(double v) {
        return Math.max(0.5, Math.min(8, Math.round(v * 2) / 2.0));
    }

    /** Arredonda o valor contínuo de um slider antes de gravá-lo no arquivo. */
    private static double round(double v, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(v * factor) / factor;
    }

    /** Formata um double sem o ".0" supérfluo, como o arquivo de propriedades faz. */
    private static String trim(Double v) {
        if (v == null) return "";
        return v == Math.rint(v) ? String.valueOf((long) (double) v) : String.valueOf((double) v);
    }

    private static String randomSeed() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(SEED_ALPHABET[random.nextInt(SEED_ALPHABET.length)]);
        }
        return sb.toString();
    }

    @FunctionalInterface
    private interface ValueFormat {
        String apply(double value);
    }

    /** Resoluções comuns, como atalho para os dois campos de dimensão. */
    private enum Preset {
        CUSTOM("Personalizado", 0, 0),
        HD("1280 × 720 — HD", 1280, 720),
        FULL_HD("1920 × 1080 — Full HD", 1920, 1080),
        QHD("2560 × 1440 — QHD", 2560, 1440),
        UHD("3840 × 2160 — 4K", 3840, 2160),
        SQUARE("1080 × 1080 — quadrado", 1080, 1080),
        PHONE("1170 × 2532 — celular", 1170, 2532);

        final String label;
        final int width;
        final int height;

        Preset(String label, int width, int height) {
            this.label = label;
            this.width = width;
            this.height = height;
        }

        static Preset matching(int w, int h) {
            for (Preset p : values()) {
                if (p != CUSTOM && p.width == w && p.height == h) return p;
            }
            return CUSTOM;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** As funções de cor do gerador, com o rótulo e o padrão do seu parâmetro. */
    private enum ColorFn {
        LINEAR("interpolateLinear", "Interpolação linear", "Peso do eixo vertical", 0.5),
        SPARKLE("sparkle", "Sparkle", "Intensidade do ruído", 0.15),
        SHADOWS("shadows", "Shadows", "Intensidade da sombra", 0.8);

        final String key;
        final String label;
        final String argLabel;
        final double argDefault;

        ColorFn(String key, String label, String argLabel, double argDefault) {
            this.key = key;
            this.label = label;
            this.argLabel = argLabel;
            this.argDefault = argDefault;
        }

        static ColorFn of(String key) {
            for (ColorFn fn : values()) {
                if (fn.key.equals(key)) return fn;
            }
            return LINEAR;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
