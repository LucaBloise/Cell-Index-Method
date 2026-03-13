package simulation;

import simulation.model.Area;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Efficiency benchmark for Cell Index Method vs Brute Force
 * under constant particle density.
 *
 * Fixed parameters: rc = 1, ri ~ U[0.23, 0.26], periodic = false
 *
 * Constant density setup:
 *   rho = N0 / L0^2 with N0 = 500 and L0 = 20
 *   For each N in N_VALUES:
 *     L = sqrt(N / rho)
 *     M = floor(L / (rc + 2*rMax))   (largest valid M)
 *
 * Study – N sweep at constant density: vary N and scale L accordingly.
 *
 * For each (N, L, M, method) configuration:
 *   • K_WARMUP runs are performed and discarded (JVM warm-up)
 *   • K_RUNS  runs are timed; each uses a fresh random particle population
 *   → mean and sample std-dev of elapsed time (ms) are recorded
 *
 * Output: simulation/outputs/benchmark/results.csv
 *
 * Run from inside the simulation/ directory:
 *   java simulation.Benchmark
 */
public class Benchmark {

    // ── Fixed simulation parameters ──────────────────────────────────────────
    static final float RC   = 1.0f;
    static final float RMIN = 0.23f;
    static final float RMAX = 0.26f;
    static final boolean PERIODIC = false;

    // ── Constant-density reference ───────────────────────────────────────────
    static final int   N0 = 500;
    static final float L0 = 20.0f;
    static final float DENSITY = N0 / (L0 * L0); // rho = N / L^2

    // ── N sweep configuration at fixed rho ───────────────────────────────────
    static final int[] N_VALUES = {100, 200, 500, 1_000, 2_000};

    // BF is O(N²); skip very large N so the benchmark always finishes quickly.
    static final int BF_MAX_N = 2_000;

    // ── Benchmark settings ───────────────────────────────────────────────────
    static final int K_WARMUP = 3;   // discarded warm-up runs
    static final int K_RUNS   = 15;  // timed runs (each with a fresh population)

    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {

        Path outputDir = Paths.get("outputs", "benchmark");
        Files.createDirectories(outputDir);
        Path csvFile = outputDir.resolve("results.csv");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(csvFile))) {

            // CSV header
            pw.println("study,method,N,L,density,M,k_runs,mean_ms,std_ms");

            // ── Study: N sweep at constant density ─────────────────────────
            System.out.printf(Locale.ROOT,
                    "=== Constant-density sweep ===%n" +
                    "rho = %.5f (from N0=%d, L0=%.2f), rc=%.2f, r in [%.2f, %.2f]%n",
                    DENSITY, N0, L0, RC, RMIN, RMAX);

            for (int N : N_VALUES) {
                float L = (float) Math.sqrt(N / DENSITY);
                int M = chooseMaxValidM(L);
                float cellSize = L / M;

                System.out.printf(Locale.ROOT,
                        "%nN=%d  -> L=%.4f, M=%d, cell_size=%.4f%n", N, L, M, cellSize);

                for (boolean useCIM : new boolean[]{true, false}) {
                    String method = useCIM ? "CIM" : "BF";

                    if (!useCIM && N > BF_MAX_N) {
                        System.out.printf("  N=%-5d  %-3s  [skipped — above BF_MAX_N=%d]%n",
                                N, method, BF_MAX_N);
                        continue;
                    }

                        double[] stats = benchmarkConfig(N, L, M, useCIM);
                    System.out.printf("  N=%-5d  M=%-3d  %-3s  mean=%8.4f ms  std=%8.4f ms%n",
                            N, M, method, stats[0], stats[1]);
                    pw.printf(Locale.ROOT, "constant_density,%s,%d,%.6f,%.6f,%d,%d,%.6f,%.6f%n",
                            method, N, L, DENSITY, M, K_RUNS, stats[0], stats[1]);
                }
            }
        }

        System.out.println("\nResults written to: " + csvFile.toAbsolutePath());
    }

    /**
     * Performs K_WARMUP discarded runs then K_RUNS timed runs for a given
     * (N, L, M, method) configuration. Each run uses a fresh random population.
     *
     * @return [mean_ms, sample_std_ms]
     */
    private static double[] benchmarkConfig(int N, float L, int M, boolean useCIM) {
        // Warm-up: let the JIT compile the hot path
        for (int i = 0; i < K_WARMUP; i++) {
            runOnce(N, L, M, useCIM);
        }

        // Timed runs
        double[] times = new double[K_RUNS];
        for (int i = 0; i < K_RUNS; i++) {
            times[i] = runOnce(N, L, M, useCIM) / 1_000_000.0;  // ns → ms
        }

        // Mean
        double mean = 0;
        for (double t : times) mean += t;
        mean /= K_RUNS;

        // Sample variance (Bessel's correction)
        double variance = 0;
        for (double t : times) variance += (t - mean) * (t - mean);
        variance /= (K_RUNS - 1);

        return new double[]{mean, Math.sqrt(variance)};
    }

    /**
     * Creates a fresh Area (new random particle population), runs the chosen
     * algorithm, and returns the elapsed time in nanoseconds.
     */
    private static long runOnce(int N, float L, int M, boolean useCIM) {
        Area area = new Area(L, RC, M, N, PERIODIC, RMIN, RMAX);
        return useCIM
                ? area.neighboursCellIndexMethod()
                : area.neighboursBruteForce();
    }

    private static int chooseMaxValidM(float L) {
        float minCellSize = RC + 2 * RMAX;
        int maxValidM = (int) Math.floor(L / minCellSize);
        return Math.max(1, maxValidM);
    }
}
