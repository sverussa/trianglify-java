package com.zyn.trianglify.gui;

import com.zyn.trianglify.Options;
import com.zyn.trianglify.Pattern;
import com.zyn.trianglify.Trianglify;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

/**
 * Roda o gerador fora da thread da interface.
 *
 * <p>Com {@code cellSize} pequeno o padrão passa da casa das dezenas de milhares
 * de triângulos, o que travaria a janela por alguns segundos se fosse feito na
 * thread do JavaFX. Como {@link Service} reinicia a si mesmo, mexer num controle
 * enquanto a geração anterior ainda roda simplesmente descarta o resultado antigo.
 */
final class GeneratorService extends Service<GeneratedPattern> {

    private Options pending;

    /** Agenda uma nova geração, cancelando a que estiver em andamento. */
    void generate(Options options) {
        this.pending = options;
        restart();
    }

    @Override
    protected Task<GeneratedPattern> createTask() {
        Options opts = pending;
        return new Task<>() {
            @Override
            protected GeneratedPattern call() {
                long t0 = System.nanoTime();
                Pattern pattern = Trianglify.generate(opts);
                // A pré-visualização usa sempre escala 1; o output.scale só entra
                // na exportação, onde o custo do supersampling se justifica.
                var image = FxImages.toFxImage(pattern.toImage(1));
                long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
                return new GeneratedPattern(opts, pattern, image, elapsedMs);
            }
        };
    }
}
