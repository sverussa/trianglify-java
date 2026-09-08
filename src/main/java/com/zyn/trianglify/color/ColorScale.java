package com.zyn.trianglify.color;

import java.util.HashMap;
import java.util.Map;

/**
 * Porte de {@code chroma.scale(colors).mode(space)} — a escala de gradiente que o
 * Trianglify usa nos eixos X e Y.
 *
 * <p>Reproduz também o cache do chroma-js, que <em>não</em> é apenas uma otimização:
 * a chave do cache é {@code floor(t * 10000)}, mas a cor guardada foi calculada com o
 * {@code t} exato da <em>primeira</em> chamada que caiu naquele balde. Dois valores de
 * {@code t} dentro do mesmo balde devolvem a cor de quem chegou primeiro, então o
 * resultado depende da ordem das chamadas. Sem esse comportamento, algumas células
 * sairiam com cores levemente diferentes das do original.
 */
public final class ColorScale {

    private final ChromaColor[] colors;
    private final double[] pos;
    private final String mode;
    private final Map<Integer, ChromaColor> cache = new HashMap<>();

    public ColorScale(String[] hexColors, String mode) {
        String[] src = hexColors;
        if (src.length == 0) {
            src = new String[]{"#fff", "#000"};
        } else if (src.length == 1) {
            src = new String[]{src[0], src[0]};
        }
        this.colors = new ChromaColor[src.length];
        this.pos = new double[src.length];
        for (int i = 0; i < src.length; i++) {
            this.colors[i] = ChromaColor.hex(src[i].trim());
            this.pos[i] = (double) i / (src.length - 1);
        }
        this.mode = mode;
    }

    /** Avalia a escala em {@code t}; valores fora de [0,1] são clampados. */
    public ChromaColor at(double t) {
        if (Double.isNaN(t)) return ChromaColor.hex("#ccc");

        t = Math.min(1, Math.max(0, t));

        int k = (int) Math.floor(t * 10000);
        ChromaColor cached = cache.get(k);
        if (cached != null) return cached;

        ChromaColor col = null;
        for (int i = 0; i < pos.length; i++) {
            double p = pos[i];
            if (t <= p) {
                col = colors[i];
                break;
            }
            if (t >= p && i == pos.length - 1) {
                col = colors[i];
                break;
            }
            if (t > p && t < pos[i + 1]) {
                double tt = (t - p) / (pos[i + 1] - p);
                col = ChromaColor.mix(colors[i], colors[i + 1], tt, mode);
                break;
            }
        }
        cache.put(k, col);
        return col;
    }
}
