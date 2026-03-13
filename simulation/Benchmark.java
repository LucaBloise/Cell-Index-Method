package simulation;

import simulation.model.Area;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Efficiency benchmark for the Cell Index Method vs Brute Force.
 *
 * Fixed parameters:  L = 20, rc = 1, ri ~ U[0.23, 0.26], periodic = false
 *
 * Study 1 – N sweep: vary N with M fixed at M_FIXED
 * Study 2 – M sweep: vary M with N fixed at N_FIXED
 *
 * For each (N, M, method) configuration:
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
    static final float L    = 20.0f;
    static final float RC   = 1.0f;
    static final float RMIN = 0.23f;
    static final float RMAX = 0.26f;

    // ── N sweep configuration ────────────────────────────────────────────────
    // M must satisfy L/M >= RC + 2*RMAX = 1.52  → M <= 13.
    // M=5 gives cell_size=4 which comfortably satisfies the constraint.
    static final int   M_FIXED  = 10;
    static final int[] N_VALUES = {10, 25, 50, 100, 200, 500, 1_000};

    // BF is O(N²); skip it above this N to keep total benchmark runtime sane.
    static final int BF_MAX_N = 10_000;

    // ── M sweep configuration ────────────────────────────────────────────────
    // Valid range: M in [1, 13].  M=1 → all particles in one cell (CIM ≈ BF).
    static final int   N_FIXED  = 500;
    static final int[] M_VALUES = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};

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
            pw.println("study,method,N,M,k_runs,mean_ms,std_ms");

            // ── Study 1: N sweep ────────────────────────────────────────────
            System.out.println("=== Study 1: N sweep  (M fixed = " + M_FIXED + ") ===");
            for (int N : N_VALUES) {
                for (boolean useCIM : new boolean[]{true, false}) {
                    String method = useCIM ? "CIM" : "BF";

                    if (!useCIM && N > BF_MAX_N) {
                        System.out.printf("  N=%-5d  %-3s  [skipped — above BF_MAX_N=%d]%n",
                                N, method, BF_MAX_N);
                        continue;
                    }

                    double[] stats = benchmarkConfig(N, M_FIXED, useCIM);
                    System.out.printf("  N=%-5d  M=%-3d  %-3s  mean=%8.4f ms  std=%8.4f ms%n",
                            N, M_FIXED, method, stats[0], stats[1]);
                    pw.printf(Locale.ROOT, "N_sweep,%s,%d,%d,%d,%.6f,%.6f%n",
                            method, N, M_FIXED, K_RUNS, stats[0], stats[1]);
                }
            }

            // ── Study 2: M sweep ────────────────────────────────────────────
            System.out.println("\n=== Study 2: M sweep  (N fixed = " + N_FIXED + ") ===");
            for (int M : M_VALUES) {
                float cellSize    = L / M;
                float minCellSize = RC + 2 * RMAX;
                if (cellSize < minCellSize) {
                    System.out.printf(
                        "  M=%-3d  [skipped — cell size %.4f < required %.4f]%n",
                        M, cellSize, minCellSize);
                    continue;
                }

                for (boolean useCIM : new boolean[]{true, false}) {
                    String method = useCIM ? "CIM" : "BF";
                    double[] stats = benchmarkConfig(N_FIXED, M, useCIM);
                    System.out.printf("  N=%-5d  M=%-3d  %-3s  mean=%8.4f ms  std=%8.4f ms%n",
                            N_FIXED, M, method, stats[0], stats[1]);
                    pw.printf(Locale.ROOT, "M_sweep,%s,%d,%d,%d,%.6f,%.6f%n",
                            method, N_FIXED, M, K_RUNS, stats[0], stats[1]);
                }
            }
        }

        System.out.println("\nResults written to: " + csvFile.toAbsolutePath());
    }

    /**
     * Performs K_WARMUP discarded runs then K_RUNS timed runs for a given
     * (N, M, method) configuration.  Each run uses a fresh random population.
     *
     * @return [mean_ms, sample_std_ms]
     */
    private static double[] benchmarkConfig(int N, int M, boolean useCIM) {
        // Warm-up: let the JIT compile the hot path
        for (int i = 0; i < K_WARMUP; i++) {
            runOnce(N, M, useCIM);
        }

        // Timed runs
        double[] times = new double[K_RUNS];
        for (int i = 0; i < K_RUNS; i++) {
            times[i] = runOnce(N, M, useCIM) / 1_000_000.0;  // ns → ms
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
    private static long runOnce(int N, int M, boolean useCIM) {
        Area area = new Area(L, RC, M, N, false, RMIN, RMAX);
        return useCIM
                ? area.neighboursCellIndexMethod()
                : area.neighboursBruteForce();
    }
}
