package com.zyn.trianglify.gui;

import atlantafx.base.controls.RingProgressIndicator;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/** Área central: mostra a imagem gerada, o indicador de progresso e os erros. */
final class PreviewPane extends StackPane {

    /** Respiro entre a imagem e as bordas da área. */
    private static final double PADDING = 32;

    private final ImageView imageView = new ImageView();
    private final StackPane frame = new StackPane(imageView);
    private final StackPane busyOverlay = new StackPane(new RingProgressIndicator(-1, false));
    private final Label message = new Label();

    PreviewPane() {
        getStyleClass().add("preview-pane");

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        frame.getStyleClass().add("preview-frame");
        frame.setMaxSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);
        frame.setEffect(new DropShadow(28, 0, 8, Color.rgb(0, 0, 0, 0.35)));

        message.getStyleClass().addAll(Styles.TITLE_4, Styles.TEXT_MUTED);
        message.setWrapText(true);
        message.setMaxWidth(420);
        message.setVisible(false);

        busyOverlay.getStyleClass().add("busy-overlay");
        busyOverlay.setVisible(false);
        busyOverlay.setPadding(new Insets(24));
        StackPane.setAlignment(busyOverlay, Pos.CENTER);

        getChildren().addAll(frame, message, busyOverlay);
        setAlignment(Pos.CENTER);

        // A imagem nunca é ampliada além do tamanho real: o que se vê na tela é
        // pixel a pixel o que vai para o arquivo.
        widthProperty().addListener((o, a, b) -> fitImage());
        heightProperty().addListener((o, a, b) -> fitImage());
    }

    void setImage(Image image) {
        imageView.setImage(image);
        message.setVisible(false);
        frame.setVisible(true);
        fitImage();
    }

    void setBusy(boolean busy) {
        busyOverlay.setVisible(busy);
    }

    void showError(String text) {
        frame.setVisible(false);
        message.setText(text);
        message.setVisible(true);
    }

    private void fitImage() {
        Image image = imageView.getImage();
        if (image == null) return;

        double availableWidth = Math.max(1, getWidth() - 2 * PADDING);
        double availableHeight = Math.max(1, getHeight() - 2 * PADDING);
        double scale = Math.min(1, Math.min(availableWidth / image.getWidth(),
                                            availableHeight / image.getHeight()));

        imageView.setFitWidth(image.getWidth() * scale);
        imageView.setFitHeight(image.getHeight() * scale);
    }

    /** Fator de exibição atual, para o rodapé mostrar quando há redução. */
    double displayScale() {
        Image image = imageView.getImage();
        if (image == null || image.getWidth() == 0) return 1;
        return imageView.getFitWidth() / image.getWidth();
    }
}
