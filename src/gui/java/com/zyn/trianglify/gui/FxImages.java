package com.zyn.trianglify.gui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Ponte entre o rasterizador Java2D do núcleo e as imagens do JavaFX.
 *
 * <p>A conversão é feita aqui, e não trocando o renderizador por um {@code Canvas}
 * do JavaFX, para que a imagem exibida na tela seja exatamente a mesma que
 * {@link com.zyn.trianglify.Pattern#toImage(double)} grava em disco.
 */
final class FxImages {

    private FxImages() {
    }

    static Image toFxImage(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();

        // O Pattern sempre devolve TYPE_INT_ARGB, então dá para ler o array de
        // pixels direto, sem a cópia linha a linha que o getRGB faria.
        int[] argb;
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            argb = ((DataBufferInt) src.getRaster().getDataBuffer()).getData();
        } else {
            argb = src.getRGB(0, 0, w, h, null, 0, w);
        }

        WritableImage out = new WritableImage(w, h);
        out.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
        return out;
    }
}
