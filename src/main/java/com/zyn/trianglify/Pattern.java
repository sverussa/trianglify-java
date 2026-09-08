package com.zyn.trianglify;

import com.zyn.trianglify.color.ChromaColor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Resultado da geração: os pontos, os triângulos coloridos e os renderizadores.
 * Porte de {@code src/pattern.js}.
 */
public final class Pattern {

    public final double[][] points;
    public final List<Poly> polys;
    public final Options opts;

    public Pattern(double[][] points, List<Poly> polys, Options opts) {
        this.points = points;
        this.polys = polys;
        this.opts = opts;
    }

    // ------------------------------------------------------------------
    // SVG
    // ------------------------------------------------------------------

    /**
     * Serializa o padrão em SVG.
     *
     * <p>A saída é byte a byte idêntica à de {@code pattern.toSVGTree().toString()}
     * na biblioteca JavaScript: mesma ordem de atributos, mesmas aspas simples e
     * mesmo arredondamento de coordenadas.
     */
    public String toSVG() {
        StringBuilder sb = new StringBuilder(polys.size() * 96);
        sb.append("<svg ");
        if (opts.svgIncludeNamespace) {
            sb.append("xmlns='http://www.w3.org/2000/svg' ");
        }
        sb.append("width='").append(opts.width).append("' ");
        sb.append("height='").append(opts.height).append("'>");

        boolean hasStroke = opts.strokeWidth > 0;
        int decimals = opts.svgCoordinateDecimals;

        for (Poly poly : polys) {
            sb.append("<path d='M");
            for (int i = 0; i < 3; i++) {
                if (i > 0) sb.append('L');
                double[] p = points[poly.vertexIndices[i]];
                sb.append(coord(p[0], decimals)).append(',').append(coord(p[1], decimals));
            }
            sb.append("Z'");

            String css = poly.color.css();
            if (opts.fill) sb.append(" fill='").append(css).append('\'');
            if (hasStroke) {
                sb.append(" stroke='").append(css).append('\'');
                sb.append(" stroke-width='").append(number(opts.strokeWidth)).append('\'');
                sb.append(" stroke-linejoin='round'");
            }
            // crispEdges resolve as falhas de antialiasing entre triângulos vizinhos
            if (opts.fill) sb.append(" shape-rendering='crispEdges'");
            sb.append("></path>");
        }

        sb.append("</svg>");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Bitmap (Java2D)
    // ------------------------------------------------------------------

    /**
     * Rasteriza o padrão com Java2D, seguindo a mesma estratégia de
     * {@code pattern.toCanvas()}.
     *
     * @param scale fator de supersampling (equivalente ao {@code devicePixelRatio})
     */
    public BufferedImage toImage(double scale) {
        int w = Math.max(1, (int) Math.round(opts.width * scale));
        int h = Math.max(1, (int) Math.round(opts.height * scale));

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(scale, scale);

            // Passada 1: contorna cada triângulo com a própria cor de preenchimento.
            // Sem isso, o antialiasing deixa fissuras claras nas arestas compartilhadas
            // (mesmo truque usado no toCanvas do original).
            if (opts.fill && opts.strokeWidth < 1) {
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                for (Poly poly : polys) {
                    g.setColor(awtColor(poly.color));
                    g.draw(path(poly));
                }
            }

            // Passada 2: preenchimentos e traços visíveis
            boolean hasStroke = opts.strokeWidth > 0;
            BasicStroke stroke = hasStroke
                    ? new BasicStroke((float) opts.strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)
                    : null;
            for (Poly poly : polys) {
                Path2D.Double p = path(poly);
                g.setColor(awtColor(poly.color));
                if (opts.fill) g.fill(p);
                if (hasStroke) {
                    g.setStroke(stroke);
                    g.draw(p);
                }
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    private Path2D.Double path(Poly poly) {
        Path2D.Double p = new Path2D.Double();
        double[] a = points[poly.vertexIndices[0]];
        double[] b = points[poly.vertexIndices[1]];
        double[] c = points[poly.vertexIndices[2]];
        p.moveTo(a[0], a[1]);
        p.lineTo(b[0], b[1]);
        p.lineTo(c[0], c[1]);
        p.closePath();
        return p;
    }

    private static Color awtColor(ChromaColor c) {
        int alpha = (int) Math.round(c.alpha() * 255);
        return new Color(c.red(), c.green(), c.blue(), alpha);
    }

    // ------------------------------------------------------------------
    // Formatação numérica compatível com JavaScript
    // ------------------------------------------------------------------

    /**
     * Equivalente a {@code +x.toFixed(decimals)} seguido da conversão para string
     * do JavaScript: arredonda e descarta zeros à direita ({@code 539.0} vira
     * {@code "539"}). Com {@code decimals < 0} o arredondamento é desativado.
     *
     * <p>O empate é resolvido para o inteiro maior (e não "para longe do zero"),
     * que é o critério do {@code Number.prototype.toFixed}.
     */
    static String coord(double v, int decimals) {
        if (decimals < 0) return number(v);
        BigDecimal bd = new BigDecimal(v)
                .setScale(decimals, v >= 0 ? RoundingMode.HALF_UP : RoundingMode.HALF_DOWN);
        if (bd.compareTo(BigDecimal.ZERO) == 0) return "0";
        return bd.stripTrailingZeros().toPlainString();
    }

    /** Converte um double para string como o JavaScript faria ({@code 2.0} vira {@code "2"}). */
    static String number(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v) && Math.abs(v) < 1e21) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }
}
