package com.zyn.trianglify.gui;

import com.zyn.trianglify.Options;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Aplicação JavaFX do Trianglify.
 *
 * <p>A abertura acontece em três tempos: o splash aparece com uma imagem gerada
 * na hora, a janela principal é montada por trás dele (é aí que a folha de estilo
 * do tema é carregada, a parte demorada) e, quando tudo está pronto, um vai
 * saindo enquanto o outro entra.
 *
 * <p>Use {@link Launcher} como classe de entrada.
 */
public final class TrianglifyApp extends Application {

    /** Configuração lida na abertura, a mesma usada pela linha de comando. */
    private static final String DEFAULT_CONFIG = "trianglify.properties";

    private static final Duration WARMUP = Duration.millis(500);
    private static final Duration HOLD = Duration.millis(450);
    private static final Duration REVEAL = Duration.millis(320);

    private SplashScreen splash;
    private Stage mainStage;
    private MainView mainView;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.mainStage = stage;

        AppTheme.saved().apply();

        String seed = Artwork.randomSeed();
        splash = new SplashScreen(Artwork.splash(SplashScreen.WIDTH, SplashScreen.HEIGHT, seed));
        splash.show();

        PauseTransition warmup = new PauseTransition(WARMUP);
        warmup.setOnFinished(e -> {
            splash.setStatus("Preparando a interface…");
            buildMainStage(seed);

            PauseTransition hold = new PauseTransition(HOLD);
            hold.setOnFinished(x -> splash.dismiss(this::reveal));
            hold.play();
        });
        warmup.play();
    }

    @Override
    public void stop() {
        if (mainView != null) mainView.shutdown();
    }

    private void buildMainStage(String seed) {
        Image icon = Artwork.icon(256, seed);

        // A primeira geração já começa aqui, ainda por trás do splash, então a
        // janela costuma aparecer com a imagem pronta.
        mainView = new MainView(icon, initialOptions());

        Scene scene = new Scene(mainView, 1280, 820);
        scene.getStylesheets().add(AppStyles.CSS);

        mainStage.setScene(scene);
        mainStage.setTitle("Trianglify");
        mainStage.getIcons().add(icon);
        mainStage.setMinWidth(940);
        mainStage.setMinHeight(620);
        mainStage.centerOnScreen();
        mainStage.setOnCloseRequest(e -> {
            mainView.shutdown();
            Platform.exit();
        });

        mainView.setOpacity(0);
        splash.setStatus("Pronto.");
    }

    private void reveal() {
        mainStage.show();
        FadeTransition fade = new FadeTransition(REVEAL, mainView);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    /**
     * Opções iniciais: o {@code trianglify.properties} do diretório atual, se
     * existir, para que a janela abra com a mesma configuração da linha de comando.
     */
    private static Options initialOptions() {
        Path config = Path.of(DEFAULT_CONFIG);
        if (!Files.isRegularFile(config)) return new Options();
        try {
            Options loaded = Options.load(config, Map.of());
            loaded.points = null;  // a janela sempre trabalha com a grade
            return loaded;
        } catch (Exception e) {
            System.err.println("aviso: " + DEFAULT_CONFIG + " ignorado (" + e.getMessage() + ")");
            return new Options();
        }
    }
}
