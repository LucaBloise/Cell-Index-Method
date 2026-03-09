import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import simulation.model.Particle;

public class Area{
    private Float L;
    private Float rc;
    private Float M;
    private Integer N;
    private Set<Particle> particleSet = new HashSet<>();
    private Set<Particle>[][] grid;

    public Area(Float L, Float rc, Float M, Integer N) {
        if (L/M < r) {
            throw new IllegalArgumentException("L/M must be greater than r");
        }
        this.L = L;
        this.rc = rc;
        this.M = M;
        this.N = N;
        this.populate();
    }

    public void populate() {
        Random random = new Random();
        particleSet.clear();
        for (int i = 0; i < N; i++) {
            float x = random.nextFloat() * L;
            float y = random.nextFloat() * L;
            particleSet.add(new Particle(x, y, 0f));
        }
    }

    public Set<Particle> getParticleSet() {
        return particleSet;
    }

    public long neighboursBruteForce() {

        long start = System.nanoTime();
        for (Particle p1 : particleSet) {
            for (Particle p2 : particleSet) {
                if (p1 != p2 && p1.distance(p2) <= rc) {
                    p1.addNeighbour(p2);
                }
            }
        }
        long end = System.nanoTime();
        return end - start;
    }

    @SuppressWarnings("unchecked")
    private void createGrid() {
        var grid = new HashSet[M][M];

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < M; j++) {
                grid[i][j] = new HashSet<>();
            }
        }
    }

    private void assignParticlesToGrid() {

        float cellSize = L / M;

        for (Particle p : particleSet) {

            int row = (int)(p.getX() / cellSize);
            int col = (int)(p.getY() / cellSize);

            if (row == M) row = M - 1;
            if (col == M) col = M - 1;

            grid[row][col].add(p);
        }
    }

    public long neighboursCellIndexMethod() {

        long start = System.nanoTime();

        createGrid();
        assignParticlesToGrid();

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < M; j++) {

                for (Particle p1 : grid[i][j]) {

                    for (Particle p2 : grid[i][j]) {
                        if (p1 != p2 && p1.distance(p2) <= rc) {
                            p1.addNeighbour(p2);
                        }
                    }

                    if (j + 1 < M) {
                        for (Particle p2 : grid[i][j + 1]) {
                            if (p1.distance(p2) <= rc) {
                                p1.addNeighbour(p2);
                                p2.addNeighbour(p1);
                            }
                        }
                    }

                    if (i + 1 < M) {
                        for (Particle p2 : grid[i + 1][j]) {
                            if (p1.distance(p2) <= rc) {
                                p1.addNeighbour(p2);
                                p2.addNeighbour(p1);
                            }
                        }
                    }

                    if (i + 1 < M && j + 1 < M) {
                        for (Particle p2 : grid[i + 1][j + 1]) {
                            if (p1.distance(p2) <= rc) {
                                p1.addNeighbour(p2);
                                p2.addNeighbour(p1);
                            }
                        }
                    }

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

        long end = System.nanoTime();
        return end - start;
    }


}