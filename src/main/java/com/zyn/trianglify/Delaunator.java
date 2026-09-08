package com.zyn.trianglify;

/**
 * Porte fiel do Delaunator v4.0.1 (Mapbox, ISC License) para Java.
 *
 * <p>Implementa triangulação de Delaunay pelo algoritmo de "sweep-hull" incremental:
 * <ol>
 *   <li>escolhe um triângulo-semente com o menor circumraio possível;</li>
 *   <li>ordena todos os pontos pela distância ao circuncentro desse triângulo;</li>
 *   <li>insere os pontos nessa ordem, ligando cada novo ponto às arestas visíveis
 *       do fecho convexo corrente (localizado via hash angular);</li>
 *   <li>restaura a condição de Delaunay com flips de aresta ({@code legalize}).</li>
 * </ol>
 *
 * <p>O porte preserva a ordem exata das operações — inclusive o quicksort com
 * mediana-de-três e a pilha de flips de tamanho fixo — porque a ordem de inserção
 * determina a ordem dos triângulos no array de saída, e o Trianglify colore os
 * triângulos justamente nessa ordem.
 */
public final class Delaunator {

    private static final double EPSILON = Math.pow(2, -52);

    private final double[] coords;
    private final int[] edgeStack = new int[512];

    private final int[] triangles0;
    private final int[] halfedges0;
    private final int hashSize;
    private final int[] hullPrev;
    private final int[] hullNext;
    private final int[] hullTri;
    private final int[] hullHash;
    private final int[] ids;
    private final double[] dists;

    private double cx, cy;
    private int hullStart;
    private int trianglesLen;

    /** Índices de vértices, agrupados em trios (t*3, t*3+1, t*3+2). */
    public int[] triangles;
    /** Meia-aresta oposta a cada aresta, ou -1 na borda do fecho. */
    public int[] halfedges;
    /** Índices dos pontos que formam o fecho convexo. */
    public int[] hull;

    public static Delaunator from(double[][] points) {
        double[] coords = new double[points.length * 2];
        for (int i = 0; i < points.length; i++) {
            coords[2 * i] = points[i][0];
            coords[2 * i + 1] = points[i][1];
        }
        return new Delaunator(coords);
    }

    public Delaunator(double[] coords) {
        this.coords = coords;
        int n = coords.length >> 1;

        int maxTriangles = Math.max(2 * n - 5, 0);
        this.triangles0 = new int[maxTriangles * 3];
        this.halfedges0 = new int[maxTriangles * 3];

        this.hashSize = (int) Math.ceil(Math.sqrt(n));
        this.hullPrev = new int[n];
        this.hullNext = new int[n];
        this.hullTri = new int[n];
        this.hullHash = new int[Math.max(hashSize, 1)];

        this.ids = new int[n];
        this.dists = new double[n];

        update();
    }

    private void update() {
        int n = coords.length >> 1;

        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < n; i++) {
            double x = coords[2 * i];
            double y = coords[2 * i + 1];
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            ids[i] = i;
        }
        double bcx = (minX + maxX) / 2;
        double bcy = (minY + maxY) / 2;

        double minDist = Double.POSITIVE_INFINITY;
        int i0 = 0, i1 = 0, i2 = 0;

        // ponto-semente mais próximo do centro da bounding box
        for (int i = 0; i < n; i++) {
            double d = dist(bcx, bcy, coords[2 * i], coords[2 * i + 1]);
            if (d < minDist) {
                i0 = i;
                minDist = d;
            }
        }
        double i0x = coords[2 * i0];
        double i0y = coords[2 * i0 + 1];

        minDist = Double.POSITIVE_INFINITY;

        // ponto mais próximo da semente
        for (int i = 0; i < n; i++) {
            if (i == i0) continue;
            double d = dist(i0x, i0y, coords[2 * i], coords[2 * i + 1]);
            if (d < minDist && d > 0) {
                i1 = i;
                minDist = d;
            }
        }
        double i1x = coords[2 * i1];
        double i1y = coords[2 * i1 + 1];

        double minRadius = Double.POSITIVE_INFINITY;

        // terceiro ponto: o que forma o menor circumcírculo com os dois primeiros
        for (int i = 0; i < n; i++) {
            if (i == i0 || i == i1) continue;
            double r = circumradius(i0x, i0y, i1x, i1y, coords[2 * i], coords[2 * i + 1]);
            if (r < minRadius) {
                i2 = i;
                minRadius = r;
            }
        }
        double i2x = coords[2 * i2];
        double i2y = coords[2 * i2 + 1];

        if (minRadius == Double.POSITIVE_INFINITY) {
            // todos os pontos são colineares: devolve apenas o fecho, sem triângulos
            for (int i = 0; i < n; i++) {
                double d = coords[2 * i] - coords[0];
                dists[i] = d != 0 ? d : (coords[2 * i + 1] - coords[1]);
            }
            quicksort(ids, dists, 0, n - 1);
            int[] tmpHull = new int[n];
            int j = 0;
            double d0 = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                int id = ids[i];
                if (dists[id] > d0) {
                    tmpHull[j++] = id;
                    d0 = dists[id];
                }
            }
            hull = new int[j];
            System.arraycopy(tmpHull, 0, hull, 0, j);
            triangles = new int[0];
            halfedges = new int[0];
            return;
        }

        // garante orientação anti-horária do triângulo-semente
        if (orient(i0x, i0y, i1x, i1y, i2x, i2y)) {
            int i = i1;
            double x = i1x, y = i1y;
            i1 = i2;
            i1x = i2x;
            i1y = i2y;
            i2 = i;
            i2x = x;
            i2y = y;
        }

        double[] center = circumcenter(i0x, i0y, i1x, i1y, i2x, i2y);
        this.cx = center[0];
        this.cy = center[1];

        for (int i = 0; i < n; i++) {
            dists[i] = dist(coords[2 * i], coords[2 * i + 1], center[0], center[1]);
        }

        // ordena os pontos pela distância ao circuncentro da semente
        quicksort(ids, dists, 0, n - 1);

        hullStart = i0;
        int hullSize = 3;

        hullNext[i0] = hullPrev[i2] = i1;
        hullNext[i1] = hullPrev[i0] = i2;
        hullNext[i2] = hullPrev[i1] = i0;

        hullTri[i0] = 0;
        hullTri[i1] = 1;
        hullTri[i2] = 2;

        java.util.Arrays.fill(hullHash, -1);
        hullHash[hashKey(i0x, i0y)] = i0;
        hullHash[hashKey(i1x, i1y)] = i1;
        hullHash[hashKey(i2x, i2y)] = i2;

        trianglesLen = 0;
        addTriangle(i0, i1, i2, -1, -1, -1);

        double xp = 0, yp = 0;
        for (int k = 0; k < ids.length; k++) {
            int i = ids[k];
            double x = coords[2 * i];
            double y = coords[2 * i + 1];

            // ignora pontos quase-duplicados
            if (k > 0 && Math.abs(x - xp) <= EPSILON && Math.abs(y - yp) <= EPSILON) continue;
            xp = x;
            yp = y;

            if (i == i0 || i == i1 || i == i2) continue;

            // acha uma aresta visível do fecho usando o hash angular
            int start = 0;
            int key = hashKey(x, y);
            for (int j = 0; j < hashSize; j++) {
                start = hullHash[(key + j) % hashSize];
                if (start != -1 && start != hullNext[start]) break;
            }
            if (start == -1) continue; // fecho degenerado: ponto ignorado

            start = hullPrev[start];
            int e = start, q;
            while (true) {
                q = hullNext[e];
                if (orient(x, y, coords[2 * e], coords[2 * e + 1], coords[2 * q], coords[2 * q + 1])) break;
                e = q;
                if (e == start) {
                    e = -1;
                    break;
                }
            }
            if (e == -1) continue; // provável ponto quase-duplicado

            // primeiro triângulo a partir do novo ponto
            int t = addTriangle(e, i, hullNext[e], -1, -1, hullTri[e]);

            hullTri[i] = legalize(t + 2);
            hullTri[e] = t; // guarda os triângulos de borda do fecho
            hullSize++;

            // avança pelo fecho, criando triângulos e legalizando
            int nx = hullNext[e];
            while (true) {
                q = hullNext[nx];
                if (!orient(x, y, coords[2 * nx], coords[2 * nx + 1], coords[2 * q], coords[2 * q + 1])) break;
                t = addTriangle(nx, i, q, hullTri[i], -1, hullTri[nx]);
                hullTri[i] = legalize(t + 2);
                hullNext[nx] = nx; // marca como removido
                hullSize--;
                nx = q;
            }

            // retrocede pelo outro lado
            if (e == start) {
                while (true) {
                    q = hullPrev[e];
                    if (!orient(x, y, coords[2 * q], coords[2 * q + 1], coords[2 * e], coords[2 * e + 1])) break;
                    t = addTriangle(q, i, e, -1, hullTri[e], hullTri[q]);
                    legalize(t + 2);
                    hullTri[q] = t;
                    hullNext[e] = e; // marca como removido
                    hullSize--;
                    e = q;
                }
            }

            hullStart = hullPrev[i] = e;
            hullNext[e] = hullPrev[nx] = i;
            hullNext[i] = nx;

            hullHash[hashKey(x, y)] = i;
            hullHash[hashKey(coords[2 * e], coords[2 * e + 1])] = e;
        }

        hull = new int[hullSize];
        for (int i = 0, e = hullStart; i < hullSize; i++) {
            hull[i] = e;
            e = hullNext[e];
        }

        triangles = java.util.Arrays.copyOf(triangles0, trianglesLen);
        halfedges = java.util.Arrays.copyOf(halfedges0, trianglesLen);
    }

    private int hashKey(double x, double y) {
        return (int) (Math.floor(pseudoAngle(x - cx, y - cy) * hashSize) % hashSize);
    }

    private int legalize(int a) {
        int i = 0;
        int ar = 0;

        // recursão substituída por pilha de tamanho fixo (igual ao original)
        while (true) {
            int b = halfedges0[a];

            int a0 = a - a % 3;
            ar = a0 + (a + 2) % 3;

            if (b == -1) { // aresta do fecho convexo
                if (i == 0) break;
                a = edgeStack[--i];
                continue;
            }

            int b0 = b - b % 3;
            int al = a0 + (a + 1) % 3;
            int bl = b0 + (b + 2) % 3;

            int p0 = triangles0[ar];
            int pr = triangles0[a];
            int pl = triangles0[al];
            int p1 = triangles0[bl];

            boolean illegal = inCircle(
                    coords[2 * p0], coords[2 * p0 + 1],
                    coords[2 * pr], coords[2 * pr + 1],
                    coords[2 * pl], coords[2 * pl + 1],
                    coords[2 * p1], coords[2 * p1 + 1]);

            if (illegal) {
                triangles0[a] = p1;
                triangles0[b] = p0;

                int hbl = halfedges0[bl];

                // aresta trocada do outro lado do fecho (raro): corrige a referência
                if (hbl == -1) {
                    int e = hullStart;
                    do {
                        if (hullTri[e] == bl) {
                            hullTri[e] = a;
                            break;
                        }
                        e = hullPrev[e];
                    } while (e != hullStart);
                }
                link(a, hbl);
                link(b, halfedges0[ar]);
                link(ar, bl);

                int br = b0 + (b + 1) % 3;

                if (i < edgeStack.length) {
                    edgeStack[i++] = br;
                }
            } else {
                if (i == 0) break;
                a = edgeStack[--i];
            }
        }

        return ar;
    }

    private void link(int a, int b) {
        halfedges0[a] = b;
        if (b != -1) halfedges0[b] = a;
    }

    private int addTriangle(int i0, int i1, int i2, int a, int b, int c) {
        int t = trianglesLen;

        triangles0[t] = i0;
        triangles0[t + 1] = i1;
        triangles0[t + 2] = i2;

        link(t, a);
        link(t + 1, b);
        link(t + 2, c);

        trianglesLen += 3;
        return t;
    }

    // ---- primitivas geométricas ----

    /** Cresce monotonicamente com o ângulo real, sem trigonometria. */
    private static double pseudoAngle(double dx, double dy) {
        double p = dx / (Math.abs(dx) + Math.abs(dy));
        return (dy > 0 ? 3 - p : 1 + p) / 4; // [0..1]
    }

    private static double dist(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return dx * dx + dy * dy;
    }

    /** Sinal da orientação 2D, só quando confiável (limite de erro de J. Shewchuk). */
    private static double orientIfSure(double px, double py, double rx, double ry, double qx, double qy) {
        double l = (ry - py) * (qx - px);
        double r = (rx - px) * (qy - py);
        return Math.abs(l - r) >= 3.3306690738754716e-16 * Math.abs(l + r) ? l - r : 0;
    }

    private static boolean orient(double rx, double ry, double qx, double qy, double px, double py) {
        double sign = orientIfSure(px, py, rx, ry, qx, qy);
        if (sign == 0) sign = orientIfSure(rx, ry, qx, qy, px, py);
        if (sign == 0) sign = orientIfSure(qx, qy, px, py, rx, ry);
        return sign < 0;
    }

    private static boolean inCircle(double ax, double ay, double bx, double by,
                                    double cx, double cy, double px, double py) {
        double dx = ax - px;
        double dy = ay - py;
        double ex = bx - px;
        double ey = by - py;
        double fx = cx - px;
        double fy = cy - py;

        double ap = dx * dx + dy * dy;
        double bp = ex * ex + ey * ey;
        double cp = fx * fx + fy * fy;

        return dx * (ey * cp - bp * fy)
                - dy * (ex * cp - bp * fx)
                + ap * (ex * fy - ey * fx) < 0;
    }

    private static double circumradius(double ax, double ay, double bx, double by, double cx, double cy) {
        double dx = bx - ax;
        double dy = by - ay;
        double ex = cx - ax;
        double ey = cy - ay;

        double bl = dx * dx + dy * dy;
        double cl = ex * ex + ey * ey;
        double d = 0.5 / (dx * ey - dy * ex);

        double x = (ey * bl - dy * cl) * d;
        double y = (dx * cl - ex * bl) * d;

        return x * x + y * y;
    }

    private static double[] circumcenter(double ax, double ay, double bx, double by, double cx, double cy) {
        double dx = bx - ax;
        double dy = by - ay;
        double ex = cx - ax;
        double ey = cy - ay;

        double bl = dx * dx + dy * dy;
        double cl = ex * ex + ey * ey;
        double d = 0.5 / (dx * ey - dy * ex);

        double x = ax + (ey * bl - dy * cl) * d;
        double y = ay + (dx * cl - ex * bl) * d;

        return new double[]{x, y};
    }

    /** Quicksort com mediana-de-três, idêntico ao do Delaunator (a ordem importa). */
    private static void quicksort(int[] ids, double[] dists, int left, int right) {
        if (right - left <= 20) {
            for (int i = left + 1; i <= right; i++) {
                int temp = ids[i];
                double tempDist = dists[temp];
                int j = i - 1;
                while (j >= left && dists[ids[j]] > tempDist) ids[j + 1] = ids[j--];
                ids[j + 1] = temp;
            }
        } else {
            int median = (left + right) >> 1;
            int i = left + 1;
            int j = right;
            swap(ids, median, i);
            if (dists[ids[left]] > dists[ids[right]]) swap(ids, left, right);
            if (dists[ids[i]] > dists[ids[right]]) swap(ids, i, right);
            if (dists[ids[left]] > dists[ids[i]]) swap(ids, left, i);

            int temp = ids[i];
            double tempDist = dists[temp];
            while (true) {
                do i++; while (i <= right && dists[ids[i]] < tempDist);
                do j--; while (j >= left && dists[ids[j]] > tempDist);
                if (j < i) break;
                swap(ids, i, j);
            }
            ids[left + 1] = ids[j];
            ids[j] = temp;

            if (right - i + 1 >= j - left) {
                quicksort(ids, dists, i, right);
                quicksort(ids, dists, left, j - 1);
            } else {
                quicksort(ids, dists, left, j - 1);
                quicksort(ids, dists, i, right);
            }
        }
    }

    private static void swap(int[] arr, int i, int j) {
        int tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }
}
