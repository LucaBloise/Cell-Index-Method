package simulation.model;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class Area {
    private final float L;   // side length of the square area
    private final float rc;  // interaction radius cut-off
    private final int M;     // number of cells per side  (must satisfy L/M >= rc)
    private final int N;     // number of particles
    private Set<Particle> particleSet = new HashSet<>();

    @SuppressWarnings("unchecked")
    private Set<Particle>[][] grid = new HashSet[0][0];

    public Area(float L, float rc, int M, int N) {
        if (L / M < rc) {
            throw new IllegalArgumentException(
                "Cell size (L/M = " + (L / M) + ") must be >= rc (" + rc + ")");
        }
        this.L  = L;
        this.rc = rc;
        this.M  = M;
        this.N  = N;
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
    //  Brute-force O(N²) neighbour search                                 //
    // ------------------------------------------------------------------ //
    public long neighboursBruteForce() {
        // Clear previous results before recomputing
        for (Particle p : particleSet) p.clearNeighbours();

        long start = System.nanoTime();
        for (Particle p1 : particleSet) {
            for (Particle p2 : particleSet) {
                if (p1 != p2 && p1.distance(p2) <= rc) {
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

                    // ── Same cell: add both directions to avoid a second pass ──
                    for (Particle p2 : grid[i][j]) {
                        if (p1.getId() < p2.getId() && p1.distance(p2) <= rc) {
                            p1.addNeighbour(p2);
                            p2.addNeighbour(p1);
                        }
                    }

                    // ── Right neighbour (i, j+1) ──
                    if (j + 1 < M) {
                        for (Particle p2 : grid[i][j + 1]) {
                            if (p1.distance(p2) <= rc) {
                                p1.addNeighbour(p2);
                                p2.addNeighbour(p1);
                            }
                        }
                    }

                    // ── Cell below (i+1, j) ──
                    if (i + 1 < M) {
                        for (Particle p2 : grid[i + 1][j]) {
                            if (p1.distance(p2) <= rc) {
                                p1.addNeighbour(p2);
                                p2.addNeighbour(p1);
                            }
                        }
                    }

                    // ── Diagonal below-right (i+1, j+1) ──
                    if (i + 1 < M && j + 1 < M) {
                        for (Particle p2 : grid[i + 1][j + 1]) {
                            if (p1.distance(p2) <= rc) {
                                p1.addNeighbour(p2);
                                p2.addNeighbour(p1);
                            }
                        }
                    }

                    // ── Diagonal below-left (i+1, j-1) ──
                    if (i + 1 < M && j - 1 >= 0) {
                        for (Particle p2 : grid[i + 1][j - 1]) {
                            if (p1.distance(p2) <= rc) {
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