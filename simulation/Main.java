package simulation;

import simulation.model.Area;
import simulation.model.Particle;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Entry point for the Cell-Index-Method / Brute-Force neighbour search.
 *
 * Usage (run from the simulation/ directory):
 *   java simulation.Main <N> <L> <M> <rc> <method> <periodic> <r>
 *   java simulation.Main <N> <L> <M> <rc> <method> <periodic> <rMin> <rMax>
 *
 *   N        – number of particles                         (integer  > 0)
 *   L        – side length of the square area              (float    > 0)
 *   M        – cells per side  (L/M must be ≥ rc + 2*rMax; periodic mode also requires M ≥ 3)
 *   rc       – interaction radius cut-off                  (float   >= 0)
 *   method   – CIM  (Cell Index Method)  |  BF  (Brute Force O(N²))
 *   periodic – true  (toroidal / wrap-around)  |  false  (hard walls)
 *   r        – fixed radius for all particles              (float   >= 0)
 *   rMin     – minimum radius, drawn from Uniform[rMin, rMax]  (float >= 0)
 *   rMax     – maximum radius, drawn from Uniform[rMin, rMax]  (float >= rMin)
 *
 * Output (written to outputs/<executionId>/ inside the working directory):
 *   output.txt     – one line per particle: id TAB x TAB y TAB r TAB space-separated neighbour ids
 *   properties.txt – all run parameters, algorithm choice, and timing
 */
public class Main {

    public static void main(String[] args) {

        // ── 1. Parse & validate arguments ────────────────────────────────────
        //   7 args: N L M rc method periodic r          (fixed radius)
        //   8 args: N L M rc method periodic rMin rMax  (random radius in [rMin, rMax])
        if (args.length < 7) {
            printUsage();
            System.exit(1);
        }

        int     N;
        float   L, rc;
        int     M;
        boolean useCIM;
        boolean periodic;
        float   rMin, rMax;

        try {
            N  = Integer.parseInt(args[0]);
            L  = Float.parseFloat(args[1]);
            M  = Integer.parseInt(args[2]);
            rc = Float.parseFloat(args[3]);

            String methodArg = args[4].toUpperCase(Locale.ROOT);
            if (!methodArg.equals("CIM") && !methodArg.equals("BF")) {
                System.err.println("Error: method must be 'CIM' or 'BF'.");
                printUsage();
                System.exit(1);
                return;
            }
            useCIM   = methodArg.equals("CIM");
            periodic = Boolean.parseBoolean(args[5]);

            rMin = Float.parseFloat(args[6]);
            rMax = (args.length >= 8) ? Float.parseFloat(args[7]) : rMin;

        } catch (NumberFormatException e) {
            System.err.println("Error parsing arguments: " + e.getMessage());
            printUsage();
            System.exit(1);
            return;
        }

        // ── 2. Build the area (populates N random particles internally) ───────
        Area area;
        try {
            area = new Area(L, rc, M, N, periodic, rMin, rMax);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid parameters: " + e.getMessage());
            System.exit(1);
            return;
        }

        // ── 3. Run the chosen algorithm, measure wall-clock time ──────────────
        long elapsedNs = useCIM
                ? area.neighboursCellIndexMethod()
                : area.neighboursBruteForce();

        System.out.printf("%-34s  %,.3f ms%n",
                useCIM ? "Cell Index Method (CIM)" : "Brute Force (O(N²))",
                elapsedNs / 1e6);

        // ── 4. Create output directory: outputs/<executionId>/ ────────────────
        //   The ID uses a millisecond-precision timestamp so consecutive runs
        //   never collide.  Run this program from inside simulation/ so that
        //   the outputs folder ends up at simulation/outputs/.
        String executionId = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));

        Path outputDir = Paths.get("outputs", executionId);
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            System.err.println("Could not create output directory: " + e.getMessage());
            System.exit(1);
            return;
        }

        // ── 5. Write result files ─────────────────────────────────────────────
        writeOutputFile    (outputDir, area);
        writePropertiesFile(outputDir, executionId, N, L, M, rc,
                            useCIM, periodic, rMin, rMax, elapsedNs, area);

        System.out.println("Results written to: " + outputDir.toAbsolutePath());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // output.txt
    //   One line per particle (sorted by id), formatted as:
    //   <particle_id>\t<x>\t<y>\t<r>\t<neighbour_id_1> <neighbour_id_2> ...
    //
    //   The neighbours listed are those whose border-to-border distance from
    //   the particle is ≤ rc.  (Border-to-border = center-to-center when r = 0.)
    // ─────────────────────────────────────────────────────────────────────────
    private static void writeOutputFile(Path dir, Area area) {
        Path file = dir.resolve("output.txt");

        // Sort particles by id so the file is deterministic and easy to read
        List<Particle> sorted = new ArrayList<>(area.getParticleSet());
        sorted.sort(Comparator.comparingInt(Particle::getId));

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(file))) {
            for (Particle p : sorted) {

                // Collect and sort neighbour ids for a tidy, reproducible output
                List<Integer> neighbourIds = new ArrayList<>();
                for (Particle n : p.getNeighbours()) {
                    neighbourIds.add(n.getId());
                }
                Collections.sort(neighbourIds);

                StringBuilder sb = new StringBuilder();
                sb.append(p.getId()).append('\t')
                  .append(p.getX()).append('\t')
                  .append(p.getY()).append('\t')
                  .append(p.getR()).append('\t');
                for (int i = 0; i < neighbourIds.size(); i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(neighbourIds.get(i));
                }
                pw.println(sb);
            }
        } catch (IOException e) {
            System.err.println("Could not write output.txt: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // properties.txt  –  all metadata about the execution
    // ─────────────────────────────────────────────────────────────────────────
    private static void writePropertiesFile(Path dir, String executionId,
                                            int N, float L, int M, float rc,
                                            boolean useCIM, boolean periodic,
                                            float rMin, float rMax,
                                            long elapsedNs, Area area) {
        Path file = dir.resolve("properties.txt");

        // Count unique neighbour pairs.
        // Both algorithms store each pair in both directions, so:
        //   total entries = 2 × unique pairs
        int totalEntries = 0;
        for (Particle p : area.getParticleSet()) {
            totalEntries += p.getNeighbours().size();
        }
        int uniquePairs = totalEntries / 2;

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(file))) {
            pw.println("=== Simulation Properties ===");
            pw.println();
            pw.println("Execution ID      : " + executionId);
            pw.println("Timestamp         : " + timestamp);
            pw.println();
            pw.println("--- Parameters ---");
            pw.printf ("N  (particles)    : %d%n",   N);
            pw.printf ("L  (side length)  : %.4f%n", L);
            pw.printf ("M  (cells / side) : %d%n",   M);
            pw.printf ("rc (cutoff)       : %.4f%n", rc);
            pw.printf ("Cell size (L/M)   : %.4f%n", L / (float) M);
            if (rMin == rMax) {
                pw.printf("Radius mode       : Fixed  (r = %.4f)%n", rMin);
            } else {
                pw.printf("Radius mode       : Random uniform  [%.4f, %.4f]%n", rMin, rMax);
            }
            pw.println();
            pw.println("--- Algorithm ---");
            pw.println("Method            : " + (useCIM
                    ? "Cell Index Method (CIM)"
                    : "Brute Force (O(N²))"));
            pw.println("Periodic BC       : " + (periodic
                    ? "Yes — toroidal (wrap-around)"
                    : "No  — hard walls"));
            pw.println();
            pw.println("--- Results ---");
            pw.printf ("Execution time    : %,d ns  (%.3f ms)%n",
                    elapsedNs, elapsedNs / 1e6);
            pw.printf ("Unique neighbour pairs found : %,d%n", uniquePairs);
        } catch (IOException e) {
            System.err.println("Could not write properties.txt: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private static void printUsage() {
        System.out.println();
        System.out.println("Usage: java simulation.Main <N> <L> <M> <rc> <method> <periodic> <r>");
        System.out.println("   or: java simulation.Main <N> <L> <M> <rc> <method> <periodic> <rMin> <rMax>");
        System.out.println();
        System.out.println("  N        – number of particles                    (integer > 0)");
        System.out.println("  L        – side length of the square area          (float > 0)");
        System.out.println("  M        – cells per side  (L/M >= rc + 2*rMax;  periodic needs M >= 3)");
        System.out.println("  rc       – interaction radius cut-off              (float >= 0)");
        System.out.println("  method   – CIM  (Cell Index Method)  |  BF  (Brute Force)");
        System.out.println("  periodic – true  (toroidal)  |  false  (hard walls)");
        System.out.println("  r        – fixed radius for all particles          (float >= 0)");
        System.out.println("  rMin rMax– radius drawn from Uniform[rMin, rMax]   (floats, rMin >= 0)");
        System.out.println();
        System.out.println("Run from inside the simulation/ directory so that");
        System.out.println("outputs are written to simulation/outputs/<executionId>/");
        System.out.println();
    }
}

