package com.zyn.trianglify.color;

/** Estratégia de coloração de um triângulo. Veja {@link ColorFunctions}. */
@FunctionalInterface
public interface ColorFunction {
    ChromaColor apply(ColorContext ctx);
}
