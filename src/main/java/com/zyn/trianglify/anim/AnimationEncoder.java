package com.zyn.trianglify.anim;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Destino dos quadros de uma animação.
 *
 * <p>Os quadros chegam em ordem, um a um, todos do mesmo tamanho e sempre opacos
 * ({@code TYPE_INT_RGB}). O arquivo só está completo depois do {@link #close()}.
 */
public interface AnimationEncoder extends AutoCloseable {

    void addFrame(BufferedImage frame) throws IOException;

    /** Como este codificador se identifica no resumo da exportação. */
    String describe();

    @Override
    void close() throws IOException;
}
