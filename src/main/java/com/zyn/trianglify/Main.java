package com.zyn.trianglify;

import com.zyn.trianglify.anim.Animation;
import com.zyn.trianglify.anim.AnimationExporter;
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
        // Precisa ser a primeira coisa, antes de qualquer classe do AWT carregar.
        //
        // Sem isto o Java2D acorda o toolkit gráfico (no macOS, a AppKit) só para
        // rasterizar um BufferedImage, e com ele vem a thread AWT-Shutdown, que
        // não é daemon: o programa termina o trabalho e a JVM fica presa esperando
        // por ela — 8 segundos numa geração simples, minutos ao exportar uma
        // animação. Nada aqui abre janela, então o modo headless é o certo.
        System.setProperty("java.awt.headless", "true");
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
        boolean animate = false;

        for (String arg : args) {
            if (arg.equals("-h") || arg.equals("--help")) {
                printHelp();
                return;
            } else if (arg.equals("--listar-paletas")) {
                ColorBrewer.PALETTES.keySet().forEach(System.out::println);
                return;
            } else if (arg.equals("-a") || arg.equals("--animar")) {
                animate = true;
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

        if (animate) {
            animate(opts, configPath, quiet);
            return;
        }

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

    /** Gera e grava o arquivo animado descrito pelas propriedades {@code anim.*}. */
    private static void animate(Options opts, Path configPath, boolean quiet) throws IOException {
        Animation animation = new Animation(opts);
        Path target = AnimationExporter.withExtension(Paths.get(opts.animFile), opts.animFormat);
        int[] size = AnimationExporter.frameSize(opts);
        int frames = AnimationExporter.frameCount(opts);

        if (!quiet) {
            System.out.println("config: " + (configPath == null ? "(padrões)" : configPath.toAbsolutePath()));
            System.out.println("sementes: " + String.join(" → ", animation.seeds())
                    + " → " + animation.seeds().get(0));
            System.out.printf("%d × %d px  ·  %d quadros a %d fps  ·  %.1f s%n",
                    size[0], size[1], frames, opts.animFps, frames / (double) opts.animFps);
            System.out.printf("transição %.2f s  ·  pausa %.2f s  ·  velocidade %s×  ·  volta completa %.1f s%n",
                    opts.animTransition / opts.animSpeed, opts.animHold / opts.animSpeed,
                    Pattern.number(opts.animSpeed), animation.cycleSeconds());

            double aligned = animation.loopFriendlyDuration(opts.animDuration);
            if (Math.abs(aligned - opts.animDuration) > 0.05) {
                System.out.printf(
                        "dica: com anim.duration=%s o fim emenda no começo sem solavanco%n",
                        Pattern.number(Math.round(aligned * 10) / 10.0));
            }
        }

        AnimationExporter.Progress progress = quiet
                ? (done, total) -> true
                : new ConsoleProgress();
        AnimationExporter.Result result = AnimationExporter.export(opts, target, progress);

        if (!quiet) {
            System.out.println();
            System.out.println(result.encoder());
            System.out.printf("%s -> %s  (%.1f MB, %d ms)%n",
                    opts.animFormat.toUpperCase(), result.file().toAbsolutePath(),
                    result.fileSize() / 1_048_576.0, result.elapsedMs());
        }
    }

    /** Barra de progresso de uma linha só, reescrita a cada ponto percentual. */
    private static final class ConsoleProgress implements AnimationExporter.Progress {
        private int lastPercent = -1;

        @Override
        public boolean frame(int done, int total) {
            int percent = done * 100 / total;
            if (percent == lastPercent && done < total) return true;
            lastPercent = percent;
            int filled = percent * 30 / 100;
            System.out.print("\r[" + "#".repeat(filled) + " ".repeat(30 - filled) + "] "
                    + percent + "%  quadro " + done + "/" + total);
            System.out.flush();
            return true;
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
                  trianglify --animar [--chave=valor ...]

                opções:
                  --config=ARQ      arquivo de propriedades (padrão: ./trianglify.properties)
                  --chave=valor     sobrescreve uma propriedade (ex.: --seed=abc --width=1920)
                  -a, --animar      gera o arquivo animado descrito pelas propriedades anim.*
                  --listar-paletas  lista as paletas de cores disponíveis, uma por linha
                  --dump-json=ARQ   grava geometria e cores em JSON (para verificação)
                  -q, --quiet       não imprime o resumo
                  -h, --help        mostra esta ajuda

                propriedades (veja trianglify.properties para a descrição de cada uma):
                  width, height, cellSize, variance, seed, points,
                  xColors, yColors, colorSpace, colorFunction, colorFunction.arg,
                  fill, strokeWidth,
                  output.file, output.format, output.scale,
                  svg.coordinateDecimals, svg.includeNamespace,
                  anim.seeds, anim.seedCount, anim.transition, anim.hold, anim.speed,
                  anim.duration, anim.fps, anim.scale, anim.format, anim.file, anim.encoder

                exemplos:
                  trianglify
                  trianglify --seed=praia --xColors=Spectral --output.file=praia.png
                  trianglify --width=2560 --height=1440 --output.scale=2
                  trianglify --animar --anim.format=gif --anim.duration=8
                  trianglify --animar --anim.format=mp4 --anim.speed=2 --anim.file=malha.mp4
                """);
    }
}
