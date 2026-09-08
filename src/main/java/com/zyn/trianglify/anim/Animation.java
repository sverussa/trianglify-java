package com.zyn.trianglify.anim;

import com.zyn.trianglify.Delaunator;
import com.zyn.trianglify.Options;
import com.zyn.trianglify.Pattern;
import com.zyn.trianglify.Poly;
import com.zyn.trianglify.Trianglify;
import com.zyn.trianglify.color.ChromaColor;
import com.zyn.trianglify.color.ColorContext;
import com.zyn.trianglify.color.ColorFunction;
import com.zyn.trianglify.color.ColorFunctions;
import com.zyn.trianglify.color.ColorScale;
import com.zyn.trianglify.util.Mulberry32;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Animação: um passeio contínuo por várias sementes.
 *
 * <h2>Como o movimento acontece</h2>
 *
 * <p>A grade de pontos do Trianglify tem sempre o mesmo número de linhas e colunas
 * para as mesmas largura, altura e {@code cellSize} — a semente muda apenas o
 * <em>jitter</em> de cada ponto. Logo, o ponto {@code i} da semente A e o ponto
 * {@code i} da semente B são o mesmo ponto da grade em dois lugares diferentes, e
 * interpolar entre os dois é um movimento natural, sem pontos aparecendo ou
 * sumindo. A triangulação é refeita a cada quadro: as arestas trocam sozinhas
 * quando os pontos se cruzam, que é justamente o que dá a impressão de uma malha
 * viva em vez de um <em>morph</em> rígido.
 *
 * <p>As cores acompanham. Quando as duas sementes caem em paletas diferentes (o
 * caso de {@code xColors=random}), cada triângulo é colorido pelas duas escalas e
 * as duas cores são misturadas no mesmo espaço de interpolação do gerador, então a
 * paleta atravessa de uma para a outra junto com a geometria.
 *
 * <p>A curva de interpolação é um <em>smoothstep</em>: a transição começa e termina
 * parada, o que evita o solavanco de um movimento linear ao entrar e sair de cada
 * pausa.
 *
 * <h2>Fidelidade ao gerador</h2>
 *
 * <p>Nos instantes de pausa o quadro é idêntico, pixel a pixel, ao que
 * {@link Trianglify#generate} produz para aquela semente: os pontos são os mesmos
 * objetos, as escalas de cor são construídas do zero a cada quadro (o cache
 * dependente de ordem da {@link ColorScale} não atravessa quadros) e a mistura de
 * cores é pulada quando não há o que misturar.
 */
public final class Animation {

    private final Options opts;
    private final List<Keyframe> keys;

    /** Pausa e transição já corrigidas pela velocidade, em segundos. */
    private final double hold;
    private final double transition;

    public Animation(Options opts) {
        if (opts.points != null) {
            throw new IllegalArgumentException(
                    "a animação não funciona com 'points' fixos: são os pontos da grade que se movem");
        }
        this.opts = opts;
        this.hold = opts.animHold / opts.animSpeed;
        this.transition = opts.animTransition / opts.animSpeed;

        List<String> seeds = seeds(opts);
        List<Keyframe> built = new ArrayList<>(seeds.size());
        for (String seed : seeds) built.add(Keyframe.of(opts, seed));
        this.keys = List.copyOf(built);
    }

    // ------------------------------------------------------------------
    // Linha do tempo
    // ------------------------------------------------------------------

    /** Sementes visitadas, na ordem. */
    public List<String> seeds() {
        return keys.stream().map(k -> k.seed).toList();
    }

    /** Duração de um trecho: a pausa sobre uma semente mais a transição seguinte. */
    public double segmentSeconds() {
        return hold + transition;
    }

    /**
     * Tempo de uma volta completa, terminando de volta na primeira semente.
     * Um arquivo com duração múltipla deste valor emenda o fim no começo.
     */
    public double cycleSeconds() {
        return keys.size() * segmentSeconds();
    }

    /**
     * A duração pedida, ajustada para o múltiplo inteiro de {@link #cycleSeconds()}
     * mais próximo — o que faz o GIF emendar sem solavanco. Nunca passa do limite
     * de {@link Options#MAX_ANIM_DURATION} nem devolve menos de uma volta.
     */
    public double loopFriendlyDuration(double wanted) {
        double cycle = cycleSeconds();
        long laps = Math.max(1, Math.round(wanted / cycle));
        while (laps > 1 && laps * cycle > Options.MAX_ANIM_DURATION) laps--;
        return Math.min(laps * cycle, Options.MAX_ANIM_DURATION);
    }

    // ------------------------------------------------------------------
    // Quadros
    // ------------------------------------------------------------------

    /** O padrão no instante {@code seconds}; o tempo dá a volta sozinho no fim do ciclo. */
    public Pattern frameAt(double seconds) {
        double cycle = cycleSeconds();
        double u = seconds % cycle;
        if (u < 0) u += cycle;

        double segment = segmentSeconds();
        int i = Math.min(keys.size() - 1, (int) (u / segment));
        double local = u - i * segment;

        Keyframe from = keys.get(i);
        Keyframe to = keys.get((i + 1) % keys.size());

        double e = local <= hold ? 0 : smoothstep((local - hold) / transition);
        return render(e == 0 ? from.points : lerp(from.points, to.points, e), from, to, e);
    }

    /**
     * Aceleração e desaceleração suaves ({@code 3t² − 2t³}). A derivada é zero nas
     * duas pontas, então o movimento nasce e morre parado.
     */
    private static double smoothstep(double t) {
        double x = Math.max(0, Math.min(1, t));
        return x * x * (3 - 2 * x);
    }

    private static double[][] lerp(double[][] a, double[][] b, double t) {
        double[][] out = new double[a.length][2];
        for (int i = 0; i < a.length; i++) {
            out[i][0] = a[i][0] + t * (b[i][0] - a[i][0]);
            out[i][1] = a[i][1] + t * (b[i][1] - a[i][1]);
        }
        return out;
    }

    /**
     * Monta o padrão de um quadro. Espelha o laço de {@link Trianglify#generate},
     * com a diferença de misturar as cores das duas sementes quando as paletas
     * sorteadas são diferentes.
     */
    private Pattern render(double[][] points, Keyframe from, Keyframe to, double e) {
        int[] geomIndices = Delaunator.from(points).triangles;

        ColorFunction colorFunction =
                ColorFunctions.byName(opts.colorFunction, opts.colorFunctionArg);

        // Escalas novas a cada quadro, de propósito: o cache da ColorScale devolve
        // a cor de quem chegou primeiro no balde, então reaproveitá-las faria a
        // cor de um triângulo depender dos quadros anteriores.
        ColorScale fromX = new ColorScale(from.xColors, opts.colorSpace);
        ColorScale fromY = new ColorScale(from.yColors, opts.colorSpace);
        Mulberry32 fromRand = Trianglify.colorRandom(from.seed);

        boolean blend = e > 0 && !from.sameColorsAs(to);
        ColorScale toX = blend ? new ColorScale(to.xColors, opts.colorSpace) : null;
        ColorScale toY = blend ? new ColorScale(to.yColors, opts.colorSpace) : null;
        Mulberry32 toRand = blend ? Trianglify.colorRandom(to.seed) : null;

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

            ChromaColor color = colorFunction.apply(context(
                    centroidX, centroidY, xPercent, yPercent, vertexIndices, vertices,
                    fromX, fromY, points, fromRand));

            if (blend) {
                ChromaColor target = colorFunction.apply(context(
                        centroidX, centroidY, xPercent, yPercent, vertexIndices, vertices,
                        toX, toY, points, toRand));
                color = ChromaColor.mix(color, target, e, opts.colorSpace);
            }

            polys.add(new Poly(vertexIndices, centroidX, centroidY, color));
        }

        return new Pattern(points, polys, opts);
    }

    private ColorContext context(double centroidX, double centroidY, double xPercent, double yPercent,
                                 int[] vertexIndices, double[][] vertices,
                                 ColorScale xScale, ColorScale yScale,
                                 double[][] points, Mulberry32 random) {
        return new ColorContext(centroidX, centroidY, xPercent, yPercent, vertexIndices, vertices,
                xScale, yScale, points, opts.colorSpace, opts.width, opts.height, random);
    }

    private static double norm(double num) {
        return Math.max(0, Math.min(1, num));
    }

    // ------------------------------------------------------------------
    // Sementes
    // ------------------------------------------------------------------

    /**
     * A lista de sementes: a de {@code anim.seeds}, se houver, ou uma sequência
     * derivada de {@code seed}.
     *
     * <p>A primeira semente derivada é a própria {@code seed}, para que a animação
     * comece exatamente na imagem parada que está configurada, e as demais saem de
     * um sufixo — o que mantém a animação inteira reproduzível a partir de uma
     * única semente.
     */
    private static List<String> seeds(Options opts) {
        if (opts.animSeeds != null && !opts.animSeeds.isBlank()) {
            List<String> listed = Arrays.stream(opts.animSeeds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (listed.size() < 2) {
                throw new IllegalArgumentException(
                        "anim.seeds precisa de pelo menos duas sementes: " + opts.animSeeds);
            }
            return listed;
        }

        String base = opts.seed == null || opts.seed.isEmpty()
                ? Long.toString(Double.doubleToLongBits(Math.random()), 36)
                : opts.seed;

        List<String> derived = new ArrayList<>(opts.animSeedCount);
        derived.add(base);
        for (int i = 1; i < opts.animSeedCount; i++) derived.add(base + "~" + i);
        return derived;
    }

    /** Tudo o que uma semente determina e que não muda de um quadro para o outro. */
    private record Keyframe(String seed, String[] xColors, String[] yColors, double[][] points) {

        static Keyframe of(Options opts, String seed) {
            Options forSeed = shallowCopyWithSeed(opts, seed);
            Mulberry32 rand = new Mulberry32(seed);
            // A ordem importa: as paletas consomem o RNG antes dos pontos.
            String[][] colors = Trianglify.resolveColors(forSeed, rand);
            return new Keyframe(seed, colors[0], colors[1], Trianglify.gridPoints(forSeed, rand));
        }

        boolean sameColorsAs(Keyframe other) {
            return Arrays.equals(xColors, other.xColors) && Arrays.equals(yColors, other.yColors);
        }
    }

    /**
     * Cópia de {@code opts} com outra semente. Só a semente muda entre os quadros;
     * o resto da configuração é o mesmo para todos.
     */
    private static Options shallowCopyWithSeed(Options src, String seed) {
        Options o = new Options();
        o.width = src.width;
        o.height = src.height;
        o.cellSize = src.cellSize;
        o.variance = src.variance;
        o.seed = seed;
        o.xColors = src.xColors;
        o.yColors = src.yColors;
        o.colorSpace = src.colorSpace;
        return o;
    }
}
