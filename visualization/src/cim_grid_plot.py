"""
CIM Grid Benchmark Plotter
==========================
Reads simulation/outputs/benchmark/cim_grid_results.csv produced by
CIMGridBenchmark.java and generates visualizations for the N x M sweep.

Usage:
    python cim_grid_plot.py
"""

import os
import sys

import matplotlib.pyplot as plt
import pandas as pd


def find_csv() -> str:
    project_root = os.path.dirname(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    )
    path = os.path.join(
        project_root, "simulation", "outputs", "benchmark", "cim_grid_results.csv"
    )
    if not os.path.exists(path):
        raise FileNotFoundError(
            f"CIM grid results not found at:\n  {path}\n"
            "Run Java benchmark first:\n"
            "  cd simulation\n"
            "  javac -d .. model\\*.java Main.java Benchmark.java CIMGridBenchmark.java\n"
            "  java -cp .. simulation.CIMGridBenchmark"
        )
    return path


def plot_cim_grid(csv_path: str) -> None:
    df = pd.read_csv(csv_path)
    valid = df[df["status"] == "ok"].copy()

    if valid.empty:
        raise ValueError("No valid rows to plot (all combinations were invalid).")

    valid["N"] = valid["N"].astype(int)
    valid["M"] = valid["M"].astype(int)

    fig, ax = plt.subplots(1, 1, figsize=(9, 6))
    fig.suptitle(
        "CIM execution time vs M\n"
        r"$L=20,\ r_c=1,\ r_i \sim \mathcal{U}[0.23, 0.26]$",
        fontsize=12,
    )

    # Time vs M, one curve per N
    for n in sorted(valid["N"].unique()):
        sub = valid[valid["N"] == n].sort_values("M")
        ax.errorbar(
            sub["M"],
            sub["mean_ms"],
            yerr=sub["std_ms"],
            marker="s",
            linewidth=1.4,
            capsize=3,
            label=f"N={n}",
        )

    ax.set_title("Execution time vs M")
    ax.set_xlabel("M (cells per side)")
    ax.set_ylabel("Execution time (ms)")
    ax.grid(True, alpha=0.3, which="both")
    ax.legend(fontsize=9)

    plt.tight_layout()
    plt.show()


if __name__ == "__main__":
    csv = sys.argv[1] if len(sys.argv) > 1 else find_csv()
    print(f"Loading: {csv}")
    plot_cim_grid(csv)
