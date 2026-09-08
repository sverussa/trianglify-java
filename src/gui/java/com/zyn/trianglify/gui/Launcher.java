package com.zyn.trianglify.gui;

/**
 * Ponto de entrada da interface gráfica.
 *
 * <p>Existe separado de {@link TrianglifyApp} de propósito: quando a classe
 * {@code main} estende {@code Application}, o lançador da JVM verifica se os
 * módulos do JavaFX estão no module path e aborta com
 * <em>"JavaFX runtime components are missing"</em> em qualquer configuração
 * fora do padrão. Delegando a partir de uma classe comum, essa verificação
 * não acontece e a aplicação sobe igual pelo Gradle, por {@code java -cp} ou
 * a partir de uma IDE.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        TrianglifyApp.main(args);
    }
}
