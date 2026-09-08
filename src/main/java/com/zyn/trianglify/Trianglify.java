package com.zyn.trianglify;

import com.zyn.trianglify.color.ChromaColor;
import com.zyn.trianglify.color.ColorBrewer;
import com.zyn.trianglify.color.ColorContext;
import com.zyn.trianglify.color.ColorFunction;
import com.zyn.trianglify.color.ColorFunctions;
import com.zyn.trianglify.color.ColorScale;
import com.zyn.trianglify.util.Mulberry32;

import java.util.ArrayList;
import java.util.List;

/**
 * Núcleo do gerador — porte de {@code src/trianglify.js}.
 *
 * <p>Executa, na mesma ordem do original:
 * <ol>
 *   <li>resolve as opções de cor (o modo {@code random} consome números do RNG);</li>
 *   <li>monta as escalas de cor dos eixos X e Y;</li>
 *   <li>gera a grade de pontos com jitter pseudo-aleatório;</li>
 *   <li>roda a triangulação de Delaunay;</li>
 *   <li>colore cada triângulo a partir do seu centroide.</li>
 * </ol>
 *
 * <p>A ordem importa: o mesmo gerador pseudo-aleatório alimenta o sorteio da paleta
 * <em>e</em> o jitter dos pontos, então inverter as etapas mudaria todo o padrão.
 */
public final class Trianglify {

    /** Salt aplicado à semente do RNG de cores (mesmo valor do original). */
    private static final String COLOR_SEED_SALT = "42";

    private Trianglify() {
    }

    public static Pattern generate(Options opts) {
        // RNG padrão, usado para sorteio de paleta e geração de pontos
        Mulberry32 rand = new Mulberry32(opts.seed);

        String[] xColors = processColorOpts(opts.xColors, rand);
        String[] yColors = "match".equals(opts.yColors)
                ? xColors
                : processColorOpts(opts.yColors, rand);

        ColorScale xScale = new ColorScale(xColors, opts.colorSpace);
        ColorScale yScale = new ColorScale(yColors, opts.colorSpace);

        double[][] points = opts.points != null ? opts.points : getPoints(opts, rand);

        int[] geomIndices = Delaunator.from(points).triangles;

        // RNG separado (semente "salgada") para as cores, de modo que trocar a
        // colorFunction não altere a geometria do padrão
        Mulberry32 cRand = new Mulberry32(
                opts.seed == null || opts.seed.isEmpty() ? null : opts.seed + COLOR_SEED_SALT);

        ColorFunction colorFunction = ColorFunctions.byName(opts.colorFunction, opts.colorFunctionArg);

        List<Poly> polys = new ArrayList<>(geomIndices.length / 3);

        for (int i = 0; i < geomIndices.length; i += 3) {
            int[] vertexIndices = {geomIndices[i], geomIndices[i + 1], geomIndices[i + 2]};

            double[][] vertices = {
                    points[vertexIndices[0]],
                    points[vertexIndices[1]],
                    points[vertexIndices[2]]
            };

            double centroidX = (vertices[0][0] + vertices[1][0] + vertices[2][0]) / 3;
            double centroidY = (vertices[0][1] + vertices[1][1] + vertices[2][1]) / 3;

            double xPercent = norm(centroidX / opts.width);
            double yPercent = norm(centroidY / opts.height);

            ChromaColor color = colorFunction.apply(new ColorContext(
                    centroidX, centroidY, xPercent, yPercent,
                    vertexIndices, vertices, xScale, yScale, points,
                    opts.colorSpace, opts.width, opts.height, cRand));

            polys.add(new Poly(vertexIndices, centroidX, centroidY, color));
        }

        return new Pattern(points, polys, opts);
    }

    private static double norm(double num) {
        return Math.max(0, Math.min(1, num));
    }

    /**
     * Traduz atalhos como {@code random} ou um nome de paleta em uma lista de cores.
     * Também aceita uma lista de hexadecimais separada por vírgulas.
     */
    private static String[] processColorOpts(String colorOpt, Mulberry32 rand) {
        if (colorOpt.contains(",")) {
            return colorOpt.split(",");
        }
        String[] fromPalette = ColorBrewer.PALETTES.get(colorOpt);
        if (fromPalette != null) return fromPalette;
        if (colorOpt.startsWith("#")) return new String[]{colorOpt};
        if ("random".equals(colorOpt)) return randomFromPalette(rand);
        throw new IllegalArgumentException("opção de cor não reconhecida: " + colorOpt);
    }

    private static String[] randomFromPalette(Mulberry32 rand) {
        String[] keys = ColorBrewer.PALETTES.keySet().toArray(new String[0]);
        return ColorBrewer.PALETTES.get(keys[(int) Math.floor(rand.next() * keys.length)]);
    }

    /**
     * Grade pseudo-aleatória de pontos: cada célula contribui com um ponto no seu
     * centro, deslocado por um jitter proporcional a {@code cellSize * variance}.
     * A grade é estendida em 2 células para cada lado ("bleed") para garantir que
     * a triangulação cubra as bordas da imagem.
     */
    private static double[][] getPoints(Options opts, Mulberry32 random) {
        double cellSize = opts.cellSize;

        int colCount = (int) Math.floor(opts.width / cellSize) + 4;
        int rowCount = (int) Math.floor(opts.height / cellSize) + 4;

        // centraliza a grade estendida sobre a área visível
        double bleedX = ((colCount * cellSize) - opts.width) / 2;
        double bleedY = ((rowCount * cellSize) - opts.height) / 2;

        double cellJitter = cellSize * opts.variance;
        double halfCell = cellSize / 2;

        int pointCount = colCount * rowCount;
        double[][] points = new double[pointCount][2];

        for (int i = 0; i < pointCount; i++) {
            int col = i % colCount;
            int row = i / colCount;

            // a ordem das duas chamadas ao RNG (x, depois y) precisa ser preservada
            double jx = (random.next() - 0.5) * cellJitter;
            double jy = (random.next() - 0.5) * cellJitter;

            points[i][0] = -bleedX + col * cellSize + halfCell + jx;
            points[i][1] = -bleedY + row * cellSize + halfCell + jy;
        }

        return points;
    }
}
