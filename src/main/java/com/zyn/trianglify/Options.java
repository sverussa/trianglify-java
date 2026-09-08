package com.zyn.trianglify;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Parâmetros de geração, carregados de um arquivo {@code .properties}.
 *
 * <p>Os nomes e valores-padrão espelham {@code defaultOptions} de
 * {@code src/trianglify.js}. Chaves desconhecidas são rejeitadas, como no original.
 */
public final class Options {

    // --- geometria ---
    public int width = 600;
    public int height = 400;
    public double cellSize = 75;
    public double variance = 0.75;
    public String seed = null;
    /** Pontos fornecidos manualmente; {@code null} usa a grade com jitter. */
    public double[][] points = null;

    // --- cor ---
    public String xColors = "random";
    public String yColors = "match";
    public String colorSpace = "lab";
    public String colorFunction = "interpolateLinear";
    public Double colorFunctionArg = null;

    // --- traço ---
    public boolean fill = true;
    public double strokeWidth = 0;

    // --- saída ---
    public String outputFile = "trianglify-output.png";
    /** {@code png}, {@code svg} ou {@code both}. */
    public String outputFormat = "png";
    /** Fator de supersampling do PNG (equivale ao devicePixelRatio do canvas). */
    public double outputScale = 1;

    // --- SVG ---
    public int svgCoordinateDecimals = 1;
    public boolean svgIncludeNamespace = true;

    // --- animação ---
    /** Duração máxima aceita para o arquivo animado, em segundos. */
    public static final double MAX_ANIM_DURATION = 120;

    /** Sementes visitadas, separadas por vírgula. {@code null} deriva de {@link #seed}. */
    public String animSeeds = null;
    /** Quantas sementes derivar quando {@link #animSeeds} não é informado. */
    public int animSeedCount = 6;
    /** Duração de uma transição entre duas sementes, em segundos. */
    public double animTransition = 2.5;
    /** Pausa sobre cada semente, entre uma transição e a próxima, em segundos. */
    public double animHold = 0.8;
    /** Multiplicador global de velocidade: divide a transição e a pausa. */
    public double animSpeed = 1;
    /** Duração do arquivo exportado, em segundos (no máximo {@value #MAX_ANIM_DURATION}). */
    public double animDuration = 12;
    public int animFps = 30;
    /** Escala aplicada a largura/altura só no arquivo animado. */
    public double animScale = 1;
    /** {@code gif} ou {@code mp4}. */
    public String animFormat = "gif";
    public String animFile = "trianglify-anim.gif";
    /** {@code auto}, {@code ffmpeg} ou {@code java} — quem codifica o GIF. */
    public String animEncoder = "auto";

    private static final List<String> KNOWN = List.of(
            "width", "height", "cellSize", "variance", "seed", "points",
            "xColors", "yColors", "colorSpace", "colorFunction", "colorFunction.arg",
            "fill", "strokeWidth",
            "output.file", "output.format", "output.scale",
            "svg.coordinateDecimals", "svg.includeNamespace",
            "anim.seeds", "anim.seedCount", "anim.transition", "anim.hold", "anim.speed",
            "anim.duration", "anim.fps", "anim.scale", "anim.format", "anim.file",
            "anim.encoder");

    /** Carrega o arquivo de propriedades e aplica os overrides de linha de comando. */
    public static Options load(Path file, Map<String, String> overrides) throws IOException {
        Properties props = new Properties();
        if (file != null) {
            if (!Files.isRegularFile(file)) {
                throw new IOException("arquivo de propriedades não encontrado: " + file.toAbsolutePath());
            }
            try (InputStream in = Files.newInputStream(file)) {
                props.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
        }
        Map<String, String> merged = new LinkedHashMap<>();
        for (String name : props.stringPropertyNames()) {
            merged.put(name, props.getProperty(name));
        }
        merged.putAll(overrides);

        for (String key : merged.keySet()) {
            if (!KNOWN.contains(key)) {
                throw new IllegalArgumentException("propriedade desconhecida: " + key
                        + "\npropriedades válidas: " + String.join(", ", KNOWN));
            }
        }

        Options o = new Options();
        o.width = intOf(merged, "width", o.width);
        o.height = intOf(merged, "height", o.height);
        o.cellSize = doubleOf(merged, "cellSize", o.cellSize);
        o.variance = doubleOf(merged, "variance", o.variance);
        o.seed = strOf(merged, "seed", o.seed);
        o.points = parsePoints(strOf(merged, "points", null));

        o.xColors = strOf(merged, "xColors", o.xColors);
        o.yColors = strOf(merged, "yColors", o.yColors);
        o.colorSpace = strOf(merged, "colorSpace", o.colorSpace);
        o.colorFunction = strOf(merged, "colorFunction", o.colorFunction);
        String cfArg = strOf(merged, "colorFunction.arg", null);
        o.colorFunctionArg = cfArg == null ? null : Double.valueOf(cfArg);

        o.fill = boolOf(merged, "fill", o.fill);
        o.strokeWidth = doubleOf(merged, "strokeWidth", o.strokeWidth);

        o.outputFile = strOf(merged, "output.file", o.outputFile);
        o.outputFormat = strOf(merged, "output.format", o.outputFormat).toLowerCase();
        o.outputScale = doubleOf(merged, "output.scale", o.outputScale);

        o.svgCoordinateDecimals = intOf(merged, "svg.coordinateDecimals", o.svgCoordinateDecimals);
        o.svgIncludeNamespace = boolOf(merged, "svg.includeNamespace", o.svgIncludeNamespace);

        o.animSeeds = strOf(merged, "anim.seeds", o.animSeeds);
        o.animSeedCount = intOf(merged, "anim.seedCount", o.animSeedCount);
        o.animTransition = doubleOf(merged, "anim.transition", o.animTransition);
        o.animHold = doubleOf(merged, "anim.hold", o.animHold);
        o.animSpeed = doubleOf(merged, "anim.speed", o.animSpeed);
        o.animDuration = doubleOf(merged, "anim.duration", o.animDuration);
        o.animFps = intOf(merged, "anim.fps", o.animFps);
        o.animScale = doubleOf(merged, "anim.scale", o.animScale);
        o.animFormat = strOf(merged, "anim.format", o.animFormat).toLowerCase();
        o.animFile = strOf(merged, "anim.file", o.animFile);
        o.animEncoder = strOf(merged, "anim.encoder", o.animEncoder).toLowerCase();

        o.validate();
        return o;
    }

    private void validate() {
        if (!(height > 0)) throw new IllegalArgumentException("height inválido: " + height);
        if (!(width > 0)) throw new IllegalArgumentException("width inválido: " + width);
        if (!(cellSize > 0)) throw new IllegalArgumentException("cellSize inválido: " + cellSize);
        if (!(outputScale > 0)) throw new IllegalArgumentException("output.scale inválido: " + outputScale);
        if (!List.of("png", "svg", "both").contains(outputFormat)) {
            throw new IllegalArgumentException("output.format inválido: " + outputFormat + " (use png, svg ou both)");
        }
        if (!List.of("lab", "rgb").contains(colorSpace)) {
            throw new IllegalArgumentException("colorSpace inválido: " + colorSpace + " (use lab ou rgb)");
        }
        validateAnimation();
    }

    /**
     * Confere os parâmetros de animação. Roda sempre, mesmo em execuções que só
     * geram a imagem parada: um erro de digitação em {@code anim.*} aparece na
     * hora de carregar o arquivo, e não só quando alguém tenta animar.
     */
    private void validateAnimation() {
        if (animSeedCount < 2) {
            throw new IllegalArgumentException(
                    "anim.seedCount inválido: " + animSeedCount + " (mínimo 2)");
        }
        if (!(animTransition > 0)) {
            throw new IllegalArgumentException("anim.transition inválido: " + animTransition);
        }
        if (!(animHold >= 0)) {
            throw new IllegalArgumentException("anim.hold inválido: " + animHold);
        }
        if (!(animSpeed > 0)) {
            throw new IllegalArgumentException("anim.speed inválido: " + animSpeed);
        }
        if (!(animDuration > 0) || animDuration > MAX_ANIM_DURATION) {
            throw new IllegalArgumentException("anim.duration inválido: " + animDuration
                    + " (use um valor entre 0 e " + (int) MAX_ANIM_DURATION + " segundos)");
        }
        if (animFps < 1 || animFps > 60) {
            throw new IllegalArgumentException("anim.fps inválido: " + animFps + " (use de 1 a 60)");
        }
        if (!(animScale > 0)) {
            throw new IllegalArgumentException("anim.scale inválido: " + animScale);
        }
        if (!List.of("gif", "mp4").contains(animFormat)) {
            throw new IllegalArgumentException(
                    "anim.format inválido: " + animFormat + " (use gif ou mp4)");
        }
        if (!List.of("auto", "ffmpeg", "java").contains(animEncoder)) {
            throw new IllegalArgumentException(
                    "anim.encoder inválido: " + animEncoder + " (use auto, ffmpeg ou java)");
        }
    }

    private static String strOf(Map<String, String> m, String k, String def) {
        String v = m.get(k);
        if (v == null) return def;
        v = v.trim();
        return v.isEmpty() ? def : v;
    }

    private static int intOf(Map<String, String> m, String k, int def) {
        String v = strOf(m, k, null);
        if (v == null) return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(k + " deve ser um inteiro, recebido: " + v);
        }
    }

    private static double doubleOf(Map<String, String> m, String k, double def) {
        String v = strOf(m, k, null);
        if (v == null) return def;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(k + " deve ser um número, recebido: " + v);
        }
    }

    private static boolean boolOf(Map<String, String> m, String k, boolean def) {
        String v = strOf(m, k, null);
        if (v == null) return def;
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(k + " deve ser true ou false, recebido: " + v);
    }

    /** Formato: {@code x1,y1; x2,y2; ...} */
    private static double[][] parsePoints(String raw) {
        if (raw == null) return null;
        List<double[]> out = new ArrayList<>();
        for (String pair : raw.split(";")) {
            String p = pair.trim();
            if (p.isEmpty()) continue;
            String[] xy = p.split(",");
            if (xy.length != 2) {
                throw new IllegalArgumentException("ponto inválido em 'points': " + p + " (esperado x,y)");
            }
            out.add(new double[]{Double.parseDouble(xy[0].trim()), Double.parseDouble(xy[1].trim())});
        }
        return out.isEmpty() ? null : out.toArray(new double[0][]);
    }
}
