package com.zyn.trianglify.gui;

import com.zyn.trianglify.Options;
import com.zyn.trianglify.Trianglify;
import javafx.scene.image.Image;

import java.util.Random;

/**
 * As imagens decorativas da própria aplicação — a arte do splash e o ícone da
 * janela — geradas pelo Trianglify na abertura.
 *
 * <p>Nada é lido do disco: o programa se apresenta com um exemplo do que ele
 * faz, diferente a cada execução, e o repositório não precisa carregar nenhum
 * arquivo de imagem.
 */
final class Artwork {

    private Artwork() {
    }

    /** Semente sorteada uma vez por execução, compartilhada pelo splash e pelo ícone. */
    static String randomSeed() {
        return Long.toString(new Random().nextLong() & 0xFFFFFFFFL, 36);
    }

    /** Arte de fundo do splash, em 2× para ficar nítida em telas de alta densidade. */
    static Image splash(int width, int height, String seed) {
        Options o = base(seed);
        o.width = width;
        o.height = height;
        o.cellSize = 58;
        return render(o, 2);
    }

    /** Ícone da janela, com a mesma paleta e semente do splash. */
    static Image icon(int size, String seed) {
        Options o = base(seed);
        o.width = size;
        o.height = size;
        o.cellSize = size / 5.0;
        return render(o, 1);
    }

    private static Options base(String seed) {
        Options o = new Options();
        o.seed = seed;
        o.xColors = "random";
        o.yColors = "match";
        o.variance = 0.85;
        return o;
    }

    private static Image render(Options o, double scale) {
        return FxImages.toFxImage(Trianglify.generate(o).toImage(scale));
    }
}
