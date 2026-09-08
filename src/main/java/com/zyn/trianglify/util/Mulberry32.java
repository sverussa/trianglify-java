package com.zyn.trianglify.util;

/**
 * Porte fiel do PRNG mulberry32 usado pelo Trianglify (src/utils/mulberry32.js),
 * originalmente adaptado da implementação de domínio público de @bryc.
 *
 * <p>A aritmética de 32 bits do JavaScript ({@code Math.imul}, {@code |0}, {@code >>>})
 * mapeia diretamente para o {@code int} do Java, que também é 32 bits com overflow
 * por wraparound. Isso garante que a sequência gerada aqui seja idêntica, bit a bit,
 * à sequência gerada pela biblioteca original para a mesma semente.
 */
public final class Mulberry32 {

    private int a;

    public Mulberry32(String seed) {
        if (seed == null || seed.isEmpty()) {
            // equivalente a Math.random().toString(36) no JS: semente aleatória
            seed = Long.toString(Double.doubleToLongBits(Math.random()), 36);
        }
        this.a = xmur3(seed);
    }

    /** Retorna o próximo float pseudo-aleatório em [0, 1). */
    public double next() {
        a |= 0;
        a = a + 0x6D2B79F5;
        int t = (a ^ (a >>> 15)) * (1 | a);
        t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
        return Integer.toUnsignedLong(t ^ (t >>> 14)) / 4294967296.0;
    }

    /** Hash xmur3: transforma a semente textual no estado inicial de 32 bits. */
    private static int xmur3(String str) {
        int h = 1779033703 ^ str.length();
        for (int i = 0; i < str.length(); i++) {
            h = (h ^ str.charAt(i)) * 0xCC9E2D51;
            h = (h << 13) | (h >>> 19);
        }
        h = (h ^ (h >>> 16)) * 0x85EBCA6B;
        h = (h ^ (h >>> 13)) * 0xC2B2AE35;
        return h ^ (h >>> 16);
    }
}
