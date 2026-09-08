package com.zyn.trianglify;

import com.zyn.trianglify.color.ChromaColor;

/** Um triângulo do padrão: seus vértices, o centroide e a cor resolvida. */
public final class Poly {

    public final int[] vertexIndices;
    public final double centroidX;
    public final double centroidY;
    public final ChromaColor color;

    public Poly(int[] vertexIndices, double centroidX, double centroidY, ChromaColor color) {
        this.vertexIndices = vertexIndices;
        this.centroidX = centroidX;
        this.centroidY = centroidY;
        this.color = color;
    }
}
