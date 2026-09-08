package com.zyn.trianglify.gui;

import com.zyn.trianglify.Options;
import com.zyn.trianglify.Trianglify;
import javafx.scene.image.Image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * As imagens decorativas da própria aplicação — a arte do splash, o ícone da
 * janela e o do Dock / barra de tarefas — geradas pelo Trianglify na abertura.
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
        return FxImages.toFxImage(iconRaster(size, seed));
    }

    /**
     * O mesmo ícone, preparado para a barra de tarefas / Dock: recuado e com os
     * cantos arredondados na proporção que o macOS usa (área útil em 80% da tela
     * do ícone, raio de canto em 22,5% dessa área). Sem isso o quadrado cru
     * destoa dos vizinhos no Dock.
     */
    static BufferedImage taskbarIcon(int size, String seed) {
        int content = Math.round(size * 0.80f);
        int inset = (size - content) / 2;
        float arc = content * 0.45f;

        BufferedImage art = iconRaster(content, seed);

        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setClip(new RoundRectangle2D.Float(inset, inset, content, content, arc, arc));
            g.drawImage(art, inset, inset, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage iconRaster(int size, String seed) {
        Options o = base(seed);
        o.width = size;
        o.height = size;
        o.cellSize = size / 5.0;
        return Trianglify.generate(o).toImage(1);
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
