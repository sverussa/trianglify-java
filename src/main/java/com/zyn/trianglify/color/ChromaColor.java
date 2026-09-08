package com.zyn.trianglify.color;

/**
 * Porte das partes do chroma-js 2.1.0 usadas pelo Trianglify: conversão
 * sRGB &harr; CIE L*a*b*, interpolação e serialização CSS.
 *
 * <p>chroma.js © 2011-2019 Gregor Aisch, licença BSD 3-Clause. Veja
 * {@code AVISOS-DE-TERCEIROS.md}.
 *
 * <p>Assim como no chroma-js, os canais RGB são mantidos como {@code double}
 * (apenas <em>clampados</em> em [0,255], nunca arredondados); o arredondamento
 * só acontece na saída {@link #css()}. Guardar os canais como inteiros aqui
 * mudaria as cores em ~1 unidade por causa da ida e volta para L*a*b*.
 *
 * <p>Todas as potências usam {@link StrictMath#pow} em vez de {@link Math#pow}:
 * o StrictMath usa o algoritmo fdlibm, o mesmo que o V8 usa em {@code Math.pow},
 * o que garante resultados idênticos bit a bit. {@code Math.pow} do HotSpot pode
 * divergir em 1 ulp e alterar o canal arredondado em casos de fronteira.
 */
public final class ChromaColor {

    // constantes L*a*b* (referente D65), iguais às de io/lab/lab-constants.js
    private static final double KN = 18;
    private static final double XN = 0.950470;
    private static final double YN = 1;
    private static final double ZN = 1.088830;
    private static final double T0 = 0.137931034;  // 4 / 29
    private static final double T1 = 0.206896552;  // 6 / 29
    private static final double T2 = 0.12841855;   // 3 * t1 * t1
    private static final double T3 = 0.008856452;  // t1 * t1 * t1

    /** [r, g, b, a] — r/g/b em [0,255] como double, a em [0,1]. */
    private final double[] rgb;

    private ChromaColor(double[] rgba) {
        this.rgb = clipRgb(rgba);
    }

    public static ChromaColor rgb(double r, double g, double b, double a) {
        return new ChromaColor(new double[]{r, g, b, a});
    }

    public static ChromaColor lab(double l, double a, double b) {
        return new ChromaColor(lab2rgb(l, a, b));
    }

    /** Aceita {@code #rgb}, {@code #rrggbb}, {@code #rrggbbaa} (com ou sem #). */
    public static ChromaColor hex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (!h.matches("[A-Fa-f0-9]{3}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{4}|[A-Fa-f0-9]{8}")) {
            throw new IllegalArgumentException("cor hexadecimal inválida: " + hex);
        }
        if (h.length() == 3 || h.length() == 4) {
            StringBuilder sb = new StringBuilder();
            for (char c : h.toCharArray()) sb.append(c).append(c);
            h = sb.toString();
        }
        long u = Long.parseLong(h, 16);
        if (h.length() == 6) {
            return rgb((u >> 16) & 0xFF, (u >> 8) & 0xFF, u & 0xFF, 1);
        }
        double alpha = Math.round((u & 0xFF) / 255.0 * 100) / 100.0;
        return rgb((u >> 24) & 0xFF, (u >> 16) & 0xFF, (u >> 8) & 0xFF, alpha);
    }

    public double alpha() {
        return rgb[3];
    }

    /** Novo objeto com o mesmo RGB e o alpha informado (equivale a Color#alpha). */
    public ChromaColor withAlpha(double a) {
        return rgb(rgb[0], rgb[1], rgb[2], a);
    }

    public int red() {
        return (int) Math.round(rgb[0]);
    }

    public int green() {
        return (int) Math.round(rgb[1]);
    }

    public int blue() {
        return (int) Math.round(rgb[2]);
    }

    /** Serialização igual à de {@code Color#css()}: {@code rgb(r,g,b)} ou {@code rgba(...)}. */
    public String css() {
        int r = red(), g = green(), b = blue();
        if (rgb[3] < 1) {
            return "rgba(" + r + "," + g + "," + b + "," + jsNumber(rgb[3]) + ")";
        }
        return "rgb(" + r + "," + g + "," + b + ")";
    }

    /** Escurece em L*, como {@code Color#darken(amount)}. */
    public ChromaColor darken(double amount) {
        double[] lab = lab();
        return lab(lab[0] - KN * amount, lab[1], lab[2]).withAlpha(rgb[3]);
    }

    /** Interpolação linear no espaço informado, como {@code chroma.mix(a, b, f, mode)}. */
    public static ChromaColor mix(ChromaColor c1, ChromaColor c2, double f, String mode) {
        ChromaColor out;
        switch (mode) {
            case "lab": {
                double[] a = c1.lab();
                double[] b = c2.lab();
                out = lab(a[0] + f * (b[0] - a[0]),
                          a[1] + f * (b[1] - a[1]),
                          a[2] + f * (b[2] - a[2]));
                break;
            }
            case "rgb": {
                out = rgb(c1.rgb[0] + f * (c2.rgb[0] - c1.rgb[0]),
                          c1.rgb[1] + f * (c2.rgb[1] - c1.rgb[1]),
                          c1.rgb[2] + f * (c2.rgb[2] - c1.rgb[2]), 1);
                break;
            }
            default:
                throw new IllegalArgumentException(
                        "colorSpace não suportado: " + mode + " (use 'lab' ou 'rgb')");
        }
        return out.withAlpha(c1.rgb[3] + f * (c2.rgb[3] - c1.rgb[3]));
    }

    /** Converte o RGB corrente para [L*, a*, b*]. */
    public double[] lab() {
        double r = rgbXyz(rgb[0]);
        double g = rgbXyz(rgb[1]);
        double b = rgbXyz(rgb[2]);
        double x = xyzLab((0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / XN);
        double y = xyzLab((0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / YN);
        double z = xyzLab((0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / ZN);
        double l = 116 * y - 16;
        return new double[]{l < 0 ? 0 : l, 500 * (x - y), 200 * (y - z)};
    }

    private static double[] lab2rgb(double l, double a, double bb) {
        double y = (l + 16) / 116;
        double x = y + a / 500;
        double z = y - bb / 200;

        y = YN * labXyz(y);
        x = XN * labXyz(x);
        z = ZN * labXyz(z);

        double r = xyzRgb(3.2404542 * x - 1.5371385 * y - 0.4985314 * z);   // D65 -> sRGB
        double g = xyzRgb(-0.9692660 * x + 1.8760108 * y + 0.0415560 * z);
        double b = xyzRgb(0.0556434 * x - 0.2040259 * y + 1.0572252 * z);

        return new double[]{r, g, b, 1};
    }

    private static double rgbXyz(double c) {
        double r = c / 255;
        if (r <= 0.04045) return r / 12.92;
        return StrictMath.pow((r + 0.055) / 1.055, 2.4);
    }

    private static double xyzLab(double t) {
        if (t > T3) return StrictMath.pow(t, 1.0 / 3);
        return t / T2 + T0;
    }

    private static double xyzRgb(double r) {
        return 255 * (r <= 0.00304 ? 12.92 * r : 1.055 * StrictMath.pow(r, 1.0 / 2.4) - 0.055);
    }

    private static double labXyz(double t) {
        return t > T1 ? t * t * t : T2 * (t - T0);
    }

    private static double[] clipRgb(double[] rgba) {
        double[] out = new double[4];
        for (int i = 0; i < 3; i++) out[i] = limit(rgba[i], 0, 255);
        out[3] = rgba.length > 3 ? limit(rgba[3], 0, 1) : 1;
        return out;
    }

    private static double limit(double x, double min, double max) {
        return x < min ? min : (x > max ? max : x);
    }

    private static String jsNumber(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return Long.toString((long) v);
        return Double.toString(v);
    }

    @Override
    public String toString() {
        return css();
    }
}
