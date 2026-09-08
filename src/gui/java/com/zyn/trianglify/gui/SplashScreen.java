package com.zyn.trianglify.gui;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Tela de abertura, com uma imagem gerada na hora como plano de fundo.
 *
 * <p>Não é decorativa apenas: enquanto ela está na tela, a aplicação monta a
 * janela principal e carrega a folha de estilo do tema, que é a parte cara da
 * inicialização.
 */
final class SplashScreen {

    static final int WIDTH = 560;
    static final int HEIGHT = 340;

    private static final double CORNER_RADIUS = 14;
    private static final Duration FADE_IN = Duration.millis(420);
    private static final Duration FADE_OUT = Duration.millis(280);

    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    private final StackPane root;
    private final Label statusLabel = new Label("Gerando o padrão de exemplo…");

    SplashScreen(Image artwork) {
        ImageView art = new ImageView(artwork);
        art.setFitWidth(WIDTH);
        art.setFitHeight(HEIGHT);
        art.setPreserveRatio(false);

        // Escurece a metade de baixo para o texto ficar legível sobre qualquer paleta.
        Region scrim = new Region();
        scrim.getStyleClass().add("splash-scrim");

        Label title = new Label("Trianglify");
        title.getStyleClass().add("splash-title");

        Label subtitle = new Label("gerador de padrões low poly · porte Java");
        subtitle.getStyleClass().add("splash-subtitle");

        statusLabel.getStyleClass().add("splash-status");

        ProgressBar progress = new ProgressBar();
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setPrefHeight(4);

        VBox text = new VBox(2, title, subtitle, new Region(), statusLabel, progress);
        text.setAlignment(Pos.BOTTOM_LEFT);
        text.setPadding(new Insets(0, 32, 30, 32));
        StackPane.setAlignment(text, Pos.BOTTOM_LEFT);

        root = new StackPane(art, scrim, text);
        root.getStyleClass().add("splash-root");
        root.setPrefSize(WIDTH, HEIGHT);
        root.setOpacity(0);

        // O arredondamento do CSS não recorta os filhos, então a imagem de fundo
        // precisa de um clip explícito para não vazar nos cantos.
        Rectangle clip = new Rectangle(WIDTH, HEIGHT);
        clip.setArcWidth(CORNER_RADIUS * 2);
        clip.setArcHeight(CORNER_RADIUS * 2);
        root.setClip(clip);

        Scene scene = new Scene(root, WIDTH, HEIGHT, Color.TRANSPARENT);
        scene.getStylesheets().add(AppStyles.CSS);

        stage.setScene(scene);
        stage.setTitle("Trianglify");
        stage.getIcons().add(artwork);
        stage.centerOnScreen();
    }

    void show() {
        stage.show();
        fade(0, 1, FADE_IN, null);
    }

    void setStatus(String text) {
        statusLabel.setText(text);
    }

    /** Some com a janela e chama {@code onHidden} quando a animação termina. */
    void dismiss(Runnable onHidden) {
        fade(1, 0, FADE_OUT, () -> {
            stage.close();
            onHidden.run();
        });
    }

    private void fade(double from, double to, Duration duration, Runnable onFinished) {
        FadeTransition fade = new FadeTransition(duration, root);
        fade.setFromValue(from);
        fade.setToValue(to);
        if (onFinished != null) fade.setOnFinished(e -> onFinished.run());
        fade.play();
    }
}
