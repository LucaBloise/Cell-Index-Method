package simulation.model;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class Area {
    private final float L;        // side length of the square area
    private final float rc;       // interaction radius cut-off
    private final int M;          // number of cells per side  (must satisfy L/M >= rc)
    private final int N;          // number of particles
    private final boolean periodic; // true → toroidal (wrap-around), false → hard walls
    private Set<Particle> particleSet = new HashSet<>();

    @SuppressWarnings("unchecked")
    private Set<Particle>[][] grid = new HashSet[0][0];

    public Area(float L, float rc, int M, int N, boolean periodic) {
        if (L / M < rc) {
            throw new IllegalArgumentException(
                "Cell size (L/M = " + (L / M) + ") must be >= rc (" + rc + ")");
        }
        // With periodic boundaries and M < 3 the "forward" sweep wraps back onto
        // already-processed cells, so every cross-boundary pair would be registered
        // twice. Require at least 3 cells per side to keep each pair unique.
        if (periodic && M < 3) {
            throw new IllegalArgumentException(
                "Periodic mode requires M >= 3 (got M = " + M + ")");
        }
        this.L        = L;
        this.rc       = rc;
        this.M        = M;
        this.N        = N;
        this.periodic = periodic;
        this.populate();
    }

    public void populate() {
        Random random = new Random();
        particleSet.clear();

        // Track occupied positions by encoding (x,y) bit patterns into a single long.
        // This guarantees no two particles share the exact same floating-point coordinates.
        Set<Long> usedPositions = new HashSet<>();

        for (int i = 0; i < N; i++) {
            float x, y;
            long posKey;
            do {
                x = random.nextFloat() * L;
                y = random.nextFloat() * L;
                // Pack both 32-bit float bit patterns into one 64-bit key
                posKey = ((long) Float.floatToIntBits(x) << 32)
                       | (Float.floatToIntBits(y) & 0xFFFFFFFFL);
            } while (usedPositions.contains(posKey));

            usedPositions.add(posKey);
            particleSet.add(new Particle(x, y, 0f));
        }
    }

    public Set<Particle> getParticleSet() {
        return particleSet;
    }

    // ------------------------------------------------------------------ //
    //  Distance helper                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Returns the relevant distance between two particles.
     * <p>
     * Walls  → straight-line Euclidean distance.
     * Periodic → minimum-image convention: for each axis we take the shorter
     *            of the direct path and the wrapped path through the boundary.
     */
    private float dist(Particle p1, Particle p2) {
        if (!periodic) {
            return p1.distance(p2);
        }
        float dx = Math.abs(p1.getX() - p2.getX());
        float dy = Math.abs(p1.getY() - p2.getY());
        if (dx > L / 2f) dx = L - dx;   // shorter path wraps around
        if (dy > L / 2f) dy = L - dy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ------------------------------------------------------------------ //
    //  Brute-force O(N²) neighbour search                                 //
    // ------------------------------------------------------------------ //
    public long neighboursBruteForce() {
        // Clear previous results before recomputing
        for (Particle p : particleSet) p.clearNeighbours();

        long start = System.nanoTime();
        for (Particle p1 : particleSet) {
            for (Particle p2 : particleSet) {
                // dist() applies minimum-image convention when periodic
                if (p1 != p2 && dist(p1, p2) <= rc) {
                    p1.addNeighbour(p2);
                }
            }
        }
        return System.nanoTime() - start;
    }

    // ------------------------------------------------------------------ //
    //  Cell Index Method                                                   //
    // ------------------------------------------------------------------ //
    @SuppressWarnings("unchecked")
    private void createGrid() {
        // Assign to the FIELD, not a local variable
        this.grid = new HashSet[M][M];
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < M; j++) {
                this.grid[i][j] = new HashSet<>();
            }
        }
    }

    private void assignParticlesToGrid() {
        float cellSize = L / M;
        for (Particle p : particleSet) {
            int row = (int) (p.getX() / cellSize);
            int col = (int) (p.getY() / cellSize);
            // Clamp particles sitting exactly on the upper boundary
            if (row >= M) row = M - 1;
            if (col >= M) col = M - 1;
            grid[row][col].add(p);
        }
    }

    public long neighboursCellIndexMethod() {
        // Clear previous results before recomputing
        for (Particle p : particleSet) p.clearNeighbours();

        long start = System.nanoTime();

        createGrid();
        assignParticlesToGrid();

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < M; j++) {

                for (Particle p1 : grid[i][j]) {

                    // ── Same cell (both directions via id guard) ──────────────
                    for (Particle p2 : grid[i][j]) {
                        if (p1.getId() < p2.getId() && dist(p1, p2) <= rc) {
                            p1.addNeighbour(p2);
                            p2.addNeighbour(p1);
                        }
                    }

                    // ── Right (i, j+1) ───────────────────────────────────────
                    // Walls:    only if there is a real cell to the right.
                    // Periodic: always; j+1 wraps to 0 at the right edge.
                    if (periodic || j + 1 < M) {
                        int nj = (j + 1) % M;
                        for (Particle p2 : grid[i][nj]) {
                            if (dist(p1, p2) <= rc) {
                                p1.addNeighbour(p2);
                                p2.addNeighbour(p1);
                            }
                        }
                    }

                    // ── Below (i+1, j) ───────────────────────────────────────
                    if (periodic || i + 1 < M) {
                        int ni = (i + 1) % M;
                        for (Particle p2 : grid[ni][j]) {
                            if (dist(p1, p2) <= rc) {
                                p1.addNeighbour(p2);
                                p2.addNeighbour(p1);
                            }
                        }
                    }

                    // ── Diagonal below-right (i+1, j+1) ─────────────────────
                    if (periodic || (i + 1 < M && j + 1 < M)) {
                        int ni = (i + 1) % M;
                        int nj = (j + 1) % M;
                        for (Particle p2 : grid[ni][nj]) {
                            if (dist(p1, p2) <= rc) {
                                p1.addNeighbour(p2);
                                p2.addNeighbour(p1);
                            }
                        }
                    }

                    // ── Diagonal below-left (i+1, j-1) ──────────────────────
                    if (periodic || (i + 1 < M && j - 1 >= 0)) {
                        int ni = (i + 1) % M;
                        int nj = (j - 1 + M) % M;
                        for (Particle p2 : grid[ni][nj]) {
                            if (dist(p1, p2) <= rc) {
                                p1.addNeighbour(p2);
                                p2.addNeighbour(p1);
                            }
                        }
                    }
                }
            }
        }

        return System.nanoTime() - start;
    }

}