package com.zyn.trianglify.gui;

import java.util.Objects;

/**
 * Folha de estilo própria da aplicação, aplicada por cima do tema do AtlantaFX.
 *
 * <p>Ela só define o que o tema não cobre (cabeçalho, barra lateral, splash) e
 * usa as variáveis do próprio AtlantaFX — {@code -color-bg-*}, {@code -color-border-*} —
 * para acompanhar automaticamente o tema escolhido.
 */
final class AppStyles {

    static final String CSS = Objects.requireNonNull(
            AppStyles.class.getResource("app.css"), "app.css não encontrado no classpath")
            .toExternalForm();

    private AppStyles() {
    }
}
