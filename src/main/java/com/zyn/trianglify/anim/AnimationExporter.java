package com.zyn.trianglify.anim;

import com.zyn.trianglify.Options;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Grava uma {@link Animation} em GIF ou MP4.
 *
 * <p>Os quadros são desenhados em paralelo e entregues ao codificador em ordem:
 * uma fila de futuros mantém alguns quadros adiantados, mas o consumo continua
 * sequencial. Rasterizar é a parte cara — a triangulação de cada quadro é refeita
 * do zero — e ela paraleliza bem porque {@link Animation#frameAt} não guarda
 * estado entre chamadas.
 */
public final class AnimationExporter {

    /** Fundo usado para achatar a transparência (só aparece com {@code fill=false}). */
    private static final Color BACKGROUND = Color.WHITE;

    private AnimationExporter() {
    }

    /** Acompanhamento do progresso, com direito a desistir no meio. */
    @FunctionalInterface
    public interface Progress {
        /**
         * @return {@code false} para cancelar a exportação
         */
        boolean frame(int done, int total);
    }

    /** Exportação abandonada a pedido do {@link Progress}. */
    public static final class Cancelled extends IOException {
        Cancelled() {
            super("exportação cancelada");
        }
    }

    /** O que saiu da exportação, para o resumo na tela. */
    public record Result(Path file, int frames, double seconds, int fps,
                         int width, int height, String encoder, long elapsedMs) {

        public long fileSize() {
            try {
                return Files.size(file);
            } catch (IOException e) {
                return -1;
            }
        }
    }

    public static int frameCount(Options opts) {
        return Math.max(1, (int) Math.round(opts.animDuration * opts.animFps));
    }

    /** Largura e altura do arquivo animado, já com {@code anim.scale} aplicado. */
    public static int[] frameSize(Options opts) {
        return new int[]{
                Math.max(1, (int) Math.round(opts.width * opts.animScale)),
                Math.max(1, (int) Math.round(opts.height * opts.animScale))
        };
    }

    /**
     * Gera e grava a animação inteira.
     *
     * @param target  destino; a extensão é ajustada para o formato escolhido
     * @param progress chamado a cada quadro gravado, na thread que chamou
     * @throws Cancelled se o {@code progress} pedir para parar
     */
    public static Result export(Options opts, Path target, Progress progress) throws IOException {
        long started = System.nanoTime();

        Animation animation = new Animation(opts);
        int frames = frameCount(opts);
        int fps = opts.animFps;
        int[] size = frameSize(opts);

        Path file = withExtension(target, opts.animFormat);
        if (file.getParent() != null) Files.createDirectories(file.getParent());

        AnimationEncoder encoder = open(opts, file, size[0], size[1]);
        ExecutorService pool = Executors.newFixedThreadPool(workers(), runnable -> {
            Thread thread = new Thread(runnable, "trianglify-frame");
            thread.setDaemon(true);
            return thread;
        });

        boolean complete = false;
        try {
            render(animation, opts, frames, fps, encoder, pool, progress);
            complete = true;
        } finally {
            pool.shutdownNow();
            finish(encoder, file, complete);
        }

        return new Result(file, frames, frames / (double) fps, fps, size[0], size[1],
                encoder.describe(), (System.nanoTime() - started) / 1_000_000);
    }

    private static void render(Animation animation, Options opts, int frames, int fps,
                               AnimationEncoder encoder, ExecutorService pool, Progress progress)
            throws IOException {
        // Um punhado de quadros adiantados basta para manter os núcleos ocupados
        // sem segurar dezenas de bitmaps grandes na memória ao mesmo tempo.
        int lookahead = workers() + 2;
        Deque<Future<BufferedImage>> pending = new ArrayDeque<>(lookahead);
        int submitted = 0;

        for (int done = 0; done < frames; done++) {
            while (submitted < frames && pending.size() < lookahead) {
                final double seconds = submitted / (double) fps;
                pending.add(pool.submit(
                        () -> opaque(animation.frameAt(seconds).toImage(opts.animScale))));
                submitted++;
            }

            BufferedImage frame;
            try {
                frame = pending.remove().get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Cancelled();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new IOException("falha ao desenhar um quadro", cause);
            }

            encoder.addFrame(frame);
            if (!progress.frame(done + 1, frames)) throw new Cancelled();
        }
    }

    /** Fecha o codificador; se a exportação não terminou, descarta o arquivo pela metade. */
    private static void finish(AnimationEncoder encoder, Path file, boolean complete)
            throws IOException {
        if (complete) {
            encoder.close();
            return;
        }
        if (encoder instanceof FfmpegEncoder ffmpeg) {
            // Fechar esperaria o ffmpeg terminar de codificar o que já recebeu.
            ffmpeg.abort();
        } else {
            try {
                encoder.close();
            } catch (IOException ignored) {
                // o arquivo vai embora logo abaixo de qualquer jeito
            }
        }
        Files.deleteIfExists(file);
    }

    private static int workers() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }

    /**
     * Escolhe o codificador. O MP4 só existe pelo {@code ffmpeg}; o GIF prefere o
     * {@code ffmpeg} quando ele está instalado, pelo <em>dithering</em>, e cai no
     * codificador do JDK quando não está.
     */
    private static AnimationEncoder open(Options opts, Path file, int width, int height)
            throws IOException {
        boolean gif = "gif".equals(opts.animFormat);

        if (!gif) {
            if ("java".equals(opts.animEncoder)) {
                throw new IOException("anim.encoder=java não grava MP4; "
                        + "use anim.format=gif ou instale o ffmpeg");
            }
            requireFfmpeg("o MP4");
            return new FfmpegEncoder(file, opts.animFormat, width, height, opts.animFps);
        }

        boolean useFfmpeg = switch (opts.animEncoder) {
            case "ffmpeg" -> true;
            case "java" -> false;
            default -> FfmpegEncoder.available();
        };
        if (useFfmpeg) {
            requireFfmpeg("anim.encoder=ffmpeg");
            return new FfmpegEncoder(file, opts.animFormat, width, height, opts.animFps);
        }
        return new GifEncoder(file, opts.animFps);
    }

    private static void requireFfmpeg(String what) throws IOException {
        if (FfmpegEncoder.available()) return;
        throw new IOException(what + " precisa do ffmpeg no PATH."
                + "\ninstale com: brew install ffmpeg   (macOS)"
                + "\n             sudo apt install ffmpeg   (Debian/Ubuntu)");
    }

    /**
     * Achata o quadro sobre um fundo opaco. Com {@code fill=true} — o padrão — não
     * há um pixel transparente sequer e isto é só uma cópia; com {@code fill=false}
     * é o que impede o GIF de virar um borrão de meias-transparências, já que o
     * formato só sabe dizer se um pixel está ou não visível.
     */
    private static BufferedImage opaque(BufferedImage source) {
        BufferedImage out = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Troca (ou acrescenta) a extensão do arquivo, como {@code output.file} faz. */
    public static Path withExtension(Path path, String extension) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String target = base + "." + extension;
        return path.getParent() == null ? Path.of(target) : path.getParent().resolve(target);
    }
}
