package com.zyn.trianglify.gui;

import com.zyn.trianglify.Options;
import com.zyn.trianglify.Pattern;
import javafx.scene.image.Image;

/**
 * Um padrão pronto: as opções que o produziram, o resultado do gerador, a imagem
 * já convertida para o JavaFX e quanto tempo levou.
 */
record GeneratedPattern(Options options, Pattern pattern, Image image, long elapsedMs) {

    int triangleCount() {
        return pattern.polys.size();
    }
}
