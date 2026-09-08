package com.zyn.trianglify.gui;

import com.zyn.trianglify.Trianglify;
import com.zyn.trianglify.color.ChromaColor;
import com.zyn.trianglify.color.ColorBrewer;
import com.zyn.trianglify.color.ColorScale;
import com.zyn.trianglify.util.Mulberry32;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;

import java.util.ArrayList;
import java.util.List;

/** Resolução e pré-visualização das paletas usadas na barra lateral. */
final class Palettes {

    /** Quantidade de amostras da faixa de gradiente mostrada sob cada combo. */
    private static final int STOPS = 48;

    private static final String RANDOM = "random";

    /** Opções de paleta oferecidas nos combos, na mesma ordem do gerador. */
    static final List<String> X_OPTIONS = options(RANDOM);
    static final List<String> Y_OPTIONS = options("match", RANDOM);

    private Palettes() {
    }

    private static List<String> options(String... prefix) {
        List<String> out = new ArrayList<>(List.of(prefix));
        out.addAll(ColorBrewer.PALETTES.keySet());
        return List.copyOf(out);
    }

    /**
     * Cores efetivas dos dois eixos, como {@code Trianglify.generate} as calcularia.
     *
     * <p>O sorteio de {@code random} é a primeira coisa que consome o gerador
     * pseudo-aleatório, então repetir aqui a mesma sequência de chamadas devolve
     * exatamente a paleta que a imagem vai usar — desde que a semente seja fixa.
     * Sem semente, cada geração sorteia de novo e não há resposta certa a dar:
     * o método devolve {@code null} em vez de mostrar uma paleta que não é a da
     * imagem.
     *
     * @return {@code {xColors, yColors}}, ou {@code null} quando não há resultado
     *         previsível (opção inválida, ou {@code random} sem semente)
     */
    static String[][] resolve(String xColors, String yColors, String seed) {
        boolean seeded = seed != null && !seed.isEmpty();
        if (!seeded && (RANDOM.equals(xColors) || RANDOM.equals(yColors))) return null;

        try {
            Mulberry32 rand = new Mulberry32(seeded ? seed : null);
            String[] x = Trianglify.processColorOpts(xColors, rand);
            String[] y = "match".equals(yColors) ? x : Trianglify.processColorOpts(yColors, rand);
            return new String[][]{x, y};
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Gradiente equivalente ao que a escala de cor produz — amostrado da própria
     * {@link ColorScale}, e não interpolado pelo JavaFX, para que a faixa mostre
     * as cores do espaço L*a*b* quando é esse o espaço escolhido.
     */
    static Paint gradient(String[] colors, String colorSpace) {
        ColorScale scale = new ColorScale(colors, colorSpace);
        List<Stop> stops = new ArrayList<>(STOPS);
        for (int i = 0; i < STOPS; i++) {
            double t = (double) i / (STOPS - 1);
            stops.add(new Stop(t, toFxColor(scale.at(t))));
        }
        return new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, stops);
    }

    private static Color toFxColor(ChromaColor c) {
        return Color.rgb(c.red(), c.green(), c.blue(), c.alpha());
    }
}
