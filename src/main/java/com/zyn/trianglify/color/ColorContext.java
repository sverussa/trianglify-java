package com.zyn.trianglify.color;

import com.zyn.trianglify.util.Mulberry32;

/**
 * Dados passados a uma {@link ColorFunction} para cada triângulo — equivalente ao
 * objeto que o Trianglify monta em {@code opts.colorFunction({...})}.
 */
public final class ColorContext {

    /** Centroide do triângulo, em pixels. */
    public final double centroidX;
    public final double centroidY;
    /** Centroide normalizado em [0,1] em relação à largura/altura. */
    public final double xPercent;
    public final double yPercent;
    /** Índices dos três vértices no array de pontos. */
    public final int[] vertexIndices;
    /** Coordenadas dos três vértices. */
    public final double[][] vertices;
    /** Escala de cores do eixo X. */
    public final ColorScale xScale;
    /** Escala de cores do eixo Y. */
    public final ColorScale yScale;
    /** Todos os pontos do padrão. */
    public final double[][] points;
    /** Espaço de cor usado na interpolação ({@code lab} por padrão). */
    public final String colorSpace;
    /** Largura/altura do padrão. */
    public final int width;
    public final int height;
    /** Gerador pseudo-aleatório dedicado às cores (semente "salgada"). */
    public final Mulberry32 random;

    public ColorContext(double centroidX, double centroidY, double xPercent, double yPercent,
                        int[] vertexIndices, double[][] vertices, ColorScale xScale, ColorScale yScale,
                        double[][] points, String colorSpace, int width, int height, Mulberry32 random) {
        this.centroidX = centroidX;
        this.centroidY = centroidY;
        this.xPercent = xPercent;
        this.yPercent = yPercent;
        this.vertexIndices = vertexIndices;
        this.vertices = vertices;
        this.xScale = xScale;
        this.yScale = yScale;
        this.points = points;
        this.colorSpace = colorSpace;
        this.width = width;
        this.height = height;
        this.random = random;
    }
}
