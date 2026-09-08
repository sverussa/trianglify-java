package com.zyn.trianglify.color;

/**
 * Funções de cor embutidas, porte de {@code src/utils/colorFunctions.js}.
 */
public final class ColorFunctions {

    private ColorFunctions() {
    }

    /**
     * Interpolação linear entre o gradiente do eixo X e o do eixo Y (padrão do Trianglify).
     * O {@code bias} controla o peso do eixo Y sobre o eixo X.
     */
    public static ColorFunction interpolateLinear(double bias) {
        return ctx -> ChromaColor.mix(
                ctx.xScale.at(ctx.xPercent),
                ctx.yScale.at(ctx.yPercent),
                bias,
                ctx.colorSpace);
    }

    /**
     * Efeito "sparkle": injeta ruído nos gradientes antes de amostrá-los,
     * aumentando o contraste entre células vizinhas.
     */
    public static ColorFunction sparkle(double jitterFactor) {
        return ctx -> {
            double a = ctx.xPercent + (ctx.random.next() - 0.5) * jitterFactor;
            double b = ctx.yPercent + (ctx.random.next() - 0.5) * jitterFactor;
            return ChromaColor.mix(ctx.xScale.at(a), ctx.yScale.at(b), 0.5, ctx.colorSpace);
        };
    }

    /**
     * Efeito "shadows": mesma mistura da interpolação linear, mas escurecendo cada
     * célula por um fator aleatório, o que simula sombreamento.
     */
    public static ColorFunction shadows(double shadowIntensity) {
        return ctx -> {
            ChromaColor color = ChromaColor.mix(
                    ctx.xScale.at(ctx.xPercent),
                    ctx.yScale.at(ctx.yPercent),
                    0.5,
                    ctx.colorSpace);
            return color.darken(shadowIntensity * ctx.random.next());
        };
    }

    /** Resolve uma função pelo nome usado no arquivo de propriedades. */
    public static ColorFunction byName(String name, Double arg) {
        switch (name) {
            case "interpolateLinear":
                return interpolateLinear(arg == null ? 0.5 : arg);
            case "sparkle":
                return sparkle(arg == null ? 0.15 : arg);
            case "shadows":
                return shadows(arg == null ? 0.8 : arg);
            default:
                throw new IllegalArgumentException("colorFunction desconhecida: " + name
                        + " (use interpolateLinear, sparkle ou shadows)");
        }
    }
}
