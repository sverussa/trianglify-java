package com.zyn.trianglify;

import com.zyn.trianglify.color.ColorBrewer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ponto de entrada de linha de comando.
 *
 * <pre>
 *   trianglify [--config=arquivo.properties] [--chave=valor ...]
 * </pre>
 *
 * <p>Sem argumentos, lê {@code trianglify.properties} do diretório atual (ou, se não
 * existir ali, do diretório do jar). Qualquer propriedade pode ser sobrescrita na
 * linha de comando, o que é prático para gerar variações sem editar o arquivo.
 */
public final class Main {

    private static final String DEFAULT_CONFIG = "trianglify.properties";

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            System.err.println("erro: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("erro: " + e);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        Map<String, String> overrides = new LinkedHashMap<>();
        Path configPath = null;
        String dumpJson = null;
        boolean quiet = false;

        for (String arg : args) {
            if (arg.equals("-h") || arg.equals("--help")) {
                printHelp();
                return;
            } else if (arg.equals("--listar-paletas")) {
                ColorBrewer.PALETTES.keySet().forEach(System.out::println);
                return;
            } else if (arg.equals("-q") || arg.equals("--quiet")) {
                quiet = true;
            } else if (arg.startsWith("--config=")) {
                configPath = Paths.get(arg.substring("--config=".length()));
            } else if (arg.startsWith("--dump-json=")) {
                dumpJson = arg.substring("--dump-json=".length());
            } else if (arg.startsWith("--") && arg.contains("=")) {
                int eq = arg.indexOf('=');
                overrides.put(arg.substring(2, eq), arg.substring(eq + 1));
            } else {
                throw new IllegalArgumentException("argumento não reconhecido: " + arg
                        + " (use --help)");
            }
        }

        if (configPath == null) configPath = findDefaultConfig();

        Options opts = Options.load(configPath, overrides);
        long t0 = System.nanoTime();
        Pattern pattern = Trianglify.generate(opts);
        long genMs = (System.nanoTime() - t0) / 1_000_000;

        Path out = Paths.get(opts.outputFile);
        if (out.getParent() != null) Files.createDirectories(out.getParent());

        boolean wantPng = opts.outputFormat.equals("png") || opts.outputFormat.equals("both");
        boolean wantSvg = opts.outputFormat.equals("svg") || opts.outputFormat.equals("both");

        if (wantSvg) {
            Path svgPath = withExtension(out, "svg");
            Files.writeString(svgPath, pattern.toSVG(), StandardCharsets.UTF_8);
            if (!quiet) System.out.println("SVG  -> " + svgPath.toAbsolutePath());
        }
        if (wantPng) {
            Path pngPath = withExtension(out, "png");
            BufferedImage img = pattern.toImage(opts.outputScale);
            if (!ImageIO.write(img, "png", pngPath.toFile())) {
                throw new IOException("nenhum codificador PNG disponível");
            }
            if (!quiet) {
                System.out.println("PNG  -> " + pngPath.toAbsolutePath()
                        + "  (" + img.getWidth() + "x" + img.getHeight() + ")");
            }
        }
        if (dumpJson != null) {
            Files.writeString(Paths.get(dumpJson), toJson(pattern), StandardCharsets.UTF_8);
        }

        if (!quiet) {
            System.out.println("config: " + (configPath == null ? "(padrões)" : configPath.toAbsolutePath()));
            System.out.println(opts.width + "x" + opts.height
                    + "  cellSize=" + opts.cellSize
                    + "  variance=" + opts.variance
                    + "  seed=" + (opts.seed == null ? "(aleatória)" : opts.seed));
            System.out.println(pattern.points.length + " pontos, "
                    + pattern.polys.size() + " triângulos, " + genMs + " ms");
        }
    }

    private static Path findDefaultConfig() {
        Path cwd = Paths.get(DEFAULT_CONFIG);
        if (Files.isRegularFile(cwd)) return cwd;
        try {
            // sobe a partir do jar/classes: cobre build/libs, build/install/<app>/lib etc.
            Path dir = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (int i = 0; i < 8 && dir != null; i++) {
                Path candidate = dir.resolve(DEFAULT_CONFIG);
                if (Files.isRegularFile(candidate)) return candidate;
                dir = dir.getParent();
            }
        } catch (URISyntaxException | NullPointerException ignored) {
            // sem informação de origem: segue para a mensagem de erro
        }
        throw new IllegalArgumentException(DEFAULT_CONFIG + " não encontrado; "
                + "informe --config=<arquivo>");
    }

    private static Path withExtension(Path p, String ext) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        Path parent = p.getParent();
        String target = base + "." + ext;
        return parent == null ? Paths.get(target) : parent.resolve(target);
    }

    /** Dump de geometria e cores, usado pelos testes de paridade com a versão JS. */
    private static String toJson(Pattern p) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"points\":[");
        for (int i = 0; i < p.points.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('[').append(p.points[i][0]).append(',').append(p.points[i][1]).append(']');
        }
        sb.append("],\"polys\":[");
        for (int i = 0; i < p.polys.size(); i++) {
            if (i > 0) sb.append(',');
            Poly poly = p.polys.get(i);
            sb.append("{\"v\":[").append(poly.vertexIndices[0]).append(',')
                    .append(poly.vertexIndices[1]).append(',')
                    .append(poly.vertexIndices[2]).append("],\"c\":\"")
                    .append(poly.color.css()).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void printHelp() {
        System.out.println("""
                Trianglify — gerador de malhas triangulares coloridas

                uso:
                  trianglify [--config=arquivo.properties] [--chave=valor ...]

                opções:
                  --config=ARQ      arquivo de propriedades (padrão: ./trianglify.properties)
                  --chave=valor     sobrescreve uma propriedade (ex.: --seed=abc --width=1920)
                  --listar-paletas  lista as paletas de cores disponíveis, uma por linha
                  --dump-json=ARQ   grava geometria e cores em JSON (para verificação)
                  -q, --quiet       não imprime o resumo
                  -h, --help        mostra esta ajuda

                propriedades (veja trianglify.properties para a descrição de cada uma):
                  width, height, cellSize, variance, seed, points,
                  xColors, yColors, colorSpace, colorFunction, colorFunction.arg,
                  fill, strokeWidth,
                  output.file, output.format, output.scale,
                  svg.coordinateDecimals, svg.includeNamespace

                exemplos:
                  trianglify
                  trianglify --seed=praia --xColors=Spectral --output.file=praia.png
                  trianglify --width=2560 --height=1440 --output.scale=2
                """);
    }
}
