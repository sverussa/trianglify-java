package com.zyn.trianglify.gui;

import com.zyn.trianglify.Options;
import com.zyn.trianglify.anim.Animation;
import javafx.application.Platform;
import javafx.scene.image.Image;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Toca a animação na prévia, desenhando os quadros numa thread própria.
 *
 * <p>O relógio é o tempo real, não a contagem de quadros: se um quadro demora
 * mais que o intervalo, o próximo simplesmente pula à frente em vez de a
 * animação inteira andar em câmera lenta. Num padrão pesado — dezenas de milhares
 * de triângulos — a prévia perde fluidez, mas continua correndo na velocidade
 * configurada, que é o que importa para julgar o resultado.
 *
 * <p>A entrega para a interface é a passo: enquanto um quadro não foi mostrado, o
 * seguinte não é enfileirado. Sem isso, um {@code Platform.runLater} por quadro
 * acumularia trabalho atrasado na fila do JavaFX até a janela travar.
 */
final class AnimationPlayer {

    /** Teto da taxa da prévia; passar disso só gastaria CPU. */
    private static final int TARGET_FPS = 30;

    private static final long FRAME_NANOS = 1_000_000_000L / TARGET_FPS;

    private final Consumer<Frame> sink;
    private final Consumer<String> onError;
    private final AtomicBoolean idle = new AtomicBoolean(true);

    private volatile Options options;
    private volatile boolean running;
    private Thread thread;

    /** Um quadro pronto, com o que o rodapé precisa mostrar. */
    record Frame(Image image, double seconds, double cycleSeconds, int triangles, double fps) {
    }

    /**
     * @param sink    recebe cada quadro, já na thread do JavaFX
     * @param onError recebe a mensagem quando a configuração não gera animação
     */
    AnimationPlayer(Consumer<Frame> sink, Consumer<String> onError) {
        this.sink = sink;
        this.onError = onError;
    }

    boolean isRunning() {
        return running;
    }

    /** Começa a tocar do zero com esta configuração. */
    void start(Options opts) {
        stop();
        this.options = opts;
        this.running = true;
        this.thread = new Thread(this::loop, "trianglify-preview");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Troca a configuração sem reiniciar o relógio, para que mexer num controle
     * não jogue a animação de volta ao começo.
     */
    void update(Options opts) {
        this.options = opts;
    }

    void stop() {
        running = false;
        Thread current = thread;
        thread = null;
        if (current != null) current.interrupt();
    }

    private void loop() {
        long clockStart = System.nanoTime();
        Options applied = null;
        Animation animation = null;
        double measuredFps = 0;

        while (running) {
            long frameStart = System.nanoTime();
            try {
                Options wanted = options;
                if (wanted != applied) {
                    animation = new Animation(wanted);
                    applied = wanted;
                }

                double seconds = (frameStart - clockStart) / 1e9;
                var pattern = animation.frameAt(seconds);
                Image image = FxImages.toFxImage(pattern.toImage(1));

                if (idle.compareAndSet(true, false)) {
                    Frame frame = new Frame(image, seconds % animation.cycleSeconds(),
                            animation.cycleSeconds(), pattern.polys.size(), measuredFps);
                    Platform.runLater(() -> {
                        idle.set(true);
                        if (running) sink.accept(frame);
                    });
                }
            } catch (RuntimeException e) {
                running = false;
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                Platform.runLater(() -> onError.accept(message));
                return;
            }

            long elapsed = System.nanoTime() - frameStart;
            measuredFps = elapsed > 0 ? Math.min(TARGET_FPS, 1e9 / elapsed) : TARGET_FPS;
            long remaining = FRAME_NANOS - elapsed;
            if (remaining > 0) {
                try {
                    Thread.sleep(remaining / 1_000_000, (int) (remaining % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
