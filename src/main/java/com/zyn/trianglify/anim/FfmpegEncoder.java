package com.zyn.trianglify.anim;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Codificação via {@code ffmpeg}, alimentado por um cano de pixels crus.
 *
 * <p>Os quadros não passam por arquivos temporários: cada um é escrito na entrada
 * do processo como BGRA sem compressão e consumido na hora. Uma animação de dois
 * minutos em Full HD daria alguns gigabytes de PNGs intermediários; assim não
 * ocupa disco nenhum.
 *
 * <p>Para MP4 o codec é o H.264 em {@code yuv420p}, que é o par que toca em
 * qualquer lugar. Para GIF, o {@code ffmpeg} monta uma paleta a partir do próprio
 * vídeo e aplica <em>dithering</em> ao reduzi-lo a 256 cores — a diferença é
 * visível nos degradês, e é por isso que ele é preferido ao codificador do JDK
 * quando está instalado.
 */
final class FfmpegEncoder implements AnimationEncoder {

    /** Qualidade do H.264: 18 é "visualmente sem perda" na escala do x264. */
    private static final String CRF = "18";

    /** Quanto da saída do ffmpeg guardar para a mensagem de erro. */
    private static final int LOG_LIMIT = 4000;

    private static Boolean available;

    private final Process process;
    private final OutputStream input;
    private final StringBuilder log = new StringBuilder();
    private final Thread drain;
    private final String description;
    private final int width;
    private final int height;
    private byte[] buffer;

    /** {@code true} se há um {@code ffmpeg} utilizável no PATH (verificado uma vez). */
    static synchronized boolean available() {
        if (available == null) {
            try {
                Process p = new ProcessBuilder("ffmpeg", "-version")
                        .redirectErrorStream(true)
                        .start();
                p.getInputStream().readAllBytes();
                available = p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
            } catch (IOException | RuntimeException e) {
                available = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                available = false;
            }
        }
        return available;
    }

    FfmpegEncoder(Path file, String format, int width, int height, int fps) throws IOException {
        this.width = width;
        this.height = height;
        this.description = "gif".equals(format)
                ? "GIF (ffmpeg, paleta com dithering)"
                : "MP4 (ffmpeg, H.264 crf " + CRF + ")";

        List<String> command = new ArrayList<>(List.of(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "rawvideo",
                "-pixel_format", "bgra",
                "-video_size", width + "x" + height,
                "-framerate", String.valueOf(fps),
                "-i", "-"));
        command.addAll("gif".equals(format) ? gifArgs() : mp4Args());
        command.add(file.toString());

        this.process = new ProcessBuilder(command).redirectErrorStream(true).start();
        this.input = new BufferedOutputStream(process.getOutputStream(), 1 << 16);

        // O ffmpeg trava se ninguém ler a saída dele enquanto escrevemos na entrada.
        this.drain = new Thread(this::drainOutput, "ffmpeg-log");
        drain.setDaemon(true);
        drain.start();
    }

    /**
     * Uma passada só: o vídeo é duplicado, um ramo gera a paleta e o outro a
     * consome. {@code stats_mode=diff} favorece o que muda de um quadro para o
     * outro, que é exatamente onde o degradê da malha precisa de mais cores.
     */
    private static List<String> gifArgs() {
        return List.of(
                "-filter_complex",
                "[0:v]split[a][b];[a]palettegen=stats_mode=diff[p];"
                        + "[b][p]paletteuse=dither=sierra2_4a:diff_mode=rectangle",
                "-loop", "0");
    }

    private static List<String> mp4Args() {
        return List.of(
                "-c:v", "libx264",
                "-preset", "medium",
                "-crf", CRF,
                // O yuv420p exige lados pares; cortar meio pixel é imperceptível e
                // evita recusar dimensões ímpares perfeitamente razoáveis.
                "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
                "-pix_fmt", "yuv420p",
                "-movflags", "+faststart");
    }

    @Override
    public void addFrame(BufferedImage frame) throws IOException {
        if (frame.getWidth() != width || frame.getHeight() != height) {
            throw new IOException("quadro com tamanho inesperado: "
                    + frame.getWidth() + "x" + frame.getHeight() + ", esperado " + width + "x" + height);
        }
        int[] pixels = ((DataBufferInt) frame.getRaster().getDataBuffer()).getData();
        if (buffer == null) buffer = new byte[pixels.length * 4];

        for (int i = 0, o = 0; i < pixels.length; i++) {
            int p = pixels[i];
            buffer[o++] = (byte) p;          // B
            buffer[o++] = (byte) (p >> 8);   // G
            buffer[o++] = (byte) (p >> 16);  // R
            buffer[o++] = (byte) 0xFF;       // A — os quadros já chegam opacos
        }

        try {
            input.write(buffer);
        } catch (IOException e) {
            // Cano quebrado quer dizer que o ffmpeg desistiu; o log diz por quê.
            throw new IOException("ffmpeg encerrou durante a codificação" + tail(), e);
        }
    }

    @Override
    public String describe() {
        return description;
    }

    @Override
    public void close() throws IOException {
        try {
            input.close();
        } catch (IOException ignored) {
            // o processo já pode ter saído; o código de saída abaixo é o que conta
        }
        try {
            int status = process.waitFor();
            drain.join(TimeUnit.SECONDS.toMillis(5));
            if (status != 0) {
                throw new IOException("ffmpeg terminou com erro (código " + status + ")" + tail());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroy();
            throw new IOException("codificação interrompida", e);
        }
    }

    /** Interrompe o processo sem esperar — usado quando a exportação é cancelada. */
    void abort() {
        process.destroy();
        try {
            input.close();
        } catch (IOException ignored) {
            // nada a fazer: o destino do arquivo parcial é ser descartado
        }
    }

    private void drainOutput() {
        try (InputStream out = process.getInputStream()) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = out.read(chunk)) >= 0) {
                synchronized (log) {
                    if (log.length() < LOG_LIMIT) {
                        log.append(new String(chunk, 0, read, StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (IOException ignored) {
            // o processo terminou; o que já foi lido basta para a mensagem
        }
    }

    private String tail() {
        synchronized (log) {
            String text = log.toString().trim();
            return text.isEmpty() ? "" : ":\n" + text;
        }
    }
}
