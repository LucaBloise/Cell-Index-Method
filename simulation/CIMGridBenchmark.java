import simulation.model.Area;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Grid benchmark for CIM only.
 *
 * Sweeps all combinations of N and M and records mean/std execution time.
 *
 * Fixed parameters: L = 20, rc = 1, ri ~ U[0.23, 0.26], periodic = false
 *
 * Output: simulation/outputs/benchmark/cim_grid_results.csv
 *
 * Run from inside simulation/:
 *   java -cp .. simulation.CIMGridBenchmark
 */
public class CIMGridBenchmark {

    // Fixed simulation parameters
    static final float L    = 20.0f;
    static final float RC   = 1.0f;
    static final float RMIN = 0.23f;
    static final float RMAX = 0.26f;
    static final boolean PERIODIC = false;

    // Requested sweep values (focused on physically valid and informative ranges)
    static final int[] N_VALUES = {10, 100, 300, 500, 800, 1_000};
    static final int[] M_VALUES = {3, 5, 7, 9, 11, 13};

    // Benchmark settings
    static final int K_WARMUP = 3;
    static final int K_RUNS   = 15;

    public static void main(String[] args) throws IOException {

        Path outputDir = Paths.get("outputs", "benchmark");
        Files.createDirectories(outputDir);
        Path csvFile = outputDir.resolve("cim_grid_results.csv");

        float minCellSize = RC + 2 * RMAX;
        int maxValidM = (int) Math.floor(L / minCellSize);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(csvFile))) {
            pw.println("N,M,k_runs,mean_ms,std_ms,status,notes");

            System.out.printf("CIM grid sweep (periodic=%s)  L=%.2f rc=%.2f r=[%.2f, %.2f]%n",
                    PERIODIC, L, RC, RMIN, RMAX);
            System.out.printf("Validity bound: L/M >= rc + 2*rmax = %.4f  ->  M <= %d%n%n",
                    minCellSize, maxValidM);

            for (int N : N_VALUES) {
                for (int M : M_VALUES) {
                    float cellSize = L / M;
                    if (cellSize < minCellSize) {
                        String notes = String.format(Locale.ROOT,
                                "invalid: cell size %.4f < required %.4f", cellSize, minCellSize);
                        pw.printf(Locale.ROOT, "%d,%d,%d,,,invalid,%s%n",
                                N, M, K_RUNS, notes);
                        System.out.printf("N=%-5d M=%-3d  [skipped] %s%n", N, M, notes);
                        continue;
                    }

                        try {
                        double[] stats = benchmarkConfig(N, M);
                        pw.printf(Locale.ROOT, "%d,%d,%d,%.6f,%.6f,ok,%n",
                            N, M, K_RUNS, stats[0], stats[1]);
                        System.out.printf(Locale.ROOT,
                            "N=%-5d M=%-3d  CIM mean=%8.4f ms  std=%8.4f ms%n",
                            N, M, stats[0], stats[1]);
                        } catch (RuntimeException e) {
                        String notes = sanitizeCsv(e.getMessage());
                        pw.printf(Locale.ROOT, "%d,%d,%d,,,failed,%s%n",
                            N, M, K_RUNS, notes);
                        System.out.printf("N=%-5d M=%-3d  [failed] %s%n", N, M, notes);
                        }
                }
            }
        }

        System.out.println("\nResults written to: " + csvFile.toAbsolutePath());
    }

    private static double[] benchmarkConfig(int N, int M) {
        for (int i = 0; i < K_WARMUP; i++) {
            runOnce(N, M);
        }

        double[] times = new double[K_RUNS];
        for (int i = 0; i < K_RUNS; i++) {
            times[i] = runOnce(N, M) / 1_000_000.0;
        }

        double mean = 0;
        for (double t : times) {
            mean += t;
        }
        mean /= K_RUNS;

        double variance = 0;
        for (double t : times) {
            variance += (t - mean) * (t - mean);
        }
        variance /= (K_RUNS - 1);

        return new double[]{mean, Math.sqrt(variance)};
    }

    private static long runOnce(int N, int M) {
        Area area = new Area(L, RC, M, N, PERIODIC, RMIN, RMAX);
        return area.neighboursCellIndexMethod();
    }

    private static String sanitizeCsv(String text) {
        if (text == null || text.isBlank()) {
            return "unknown error";
        }
        return text.replace(',', ';').replace('\n', ' ').replace('\r', ' ').trim();
    }
}