"""
Benchmark Plotter
=================
Reads simulation/outputs/benchmark/results.csv produced by Benchmark.java
and generates two figures:

  Figure 1 — Execution time vs N  (M fixed)
  Figure 2 — Execution time vs M  (N fixed)

Each figure shows both CIM and BF with mean ± 1 std error bars.

Usage:
    python benchmark_plot.py
"""

import os
import sys

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


# ─────────────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────────────

def find_csv() -> str:
    project_root = os.path.dirname(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    )
    path = os.path.join(
        project_root, "simulation", "outputs", "benchmark", "results.csv"
    )
    if not os.path.exists(path):
        raise FileNotFoundError(
            f"Benchmark results not found at:\n  {path}\n"
            "Run Benchmark.java first:\n"
            "  cd simulation && java simulation.Benchmark"
        )
    return path


# ─────────────────────────────────────────────────────────────────────────────
# Plotting
# ─────────────────────────────────────────────────────────────────────────────

COLORS  = {"CIM": "#1f77b4", "BF": "#d62728"}
MARKERS = {"CIM": "o",       "BF": "s"}
LABELS  = {"CIM": "Cell Index Method (CIM)", "BF": "Brute Force (BF)"}


def _add_subplot(ax, data: "pd.DataFrame", x_col: str, x_label: str, title: str) -> None:
    """Draw CIM and BF error-bar lines on *ax* for the given sweep data."""
    for method in ["CIM", "BF"]:
        sub = data[data["method"] == method].sort_values(x_col)
        if sub.empty:
            continue
        ax.errorbar(
            sub[x_col],
            sub["mean_ms"],
            yerr=sub["std_ms"],
            label=LABELS[method],
            color=COLORS[method],
            marker=MARKERS[method],
            capsize=4,
            linewidth=1.5,
            markersize=6,
            elinewidth=1,
        )

    ax.set_xlabel(x_label, fontsize=11)
    ax.set_ylabel("Execution time (ms)", fontsize=11)
    ax.set_title(title, fontsize=10)
    ax.legend(fontsize=9)
    ax.grid(True, alpha=0.3, which="both")


def plot_benchmark(csv_path: str) -> None:
    df = pd.read_csv(csv_path)

    n_data = df[df["study"] == "N_sweep"]
    m_data = df[df["study"] == "M_sweep"]

    fig, axes = plt.subplots(1, 2, figsize=(14, 6))
    fig.suptitle(
        "CIM vs Brute Force — Efficiency Study\n"
        r"$L=20,\ r_c=1,\ r_i \sim \mathcal{U}[0.23,\,0.26]$",
        fontsize=12,
    )

    # ── Figure 1: N sweep ────────────────────────────────────────────────────
    if not n_data.empty:
        M_fixed = int(n_data["M"].iloc[0])
        _add_subplot(
            axes[0], n_data, "N",
            x_label="N  (number of particles)",
            title=f"Execution time vs N\n(M = {M_fixed} fixed)",
        )
    else:
        axes[0].set_title("N sweep — no data")

    # ── Figure 2: M sweep ────────────────────────────────────────────────────
    if not m_data.empty:
        N_fixed = int(m_data["N"].iloc[0])
        _add_subplot(
            axes[1], m_data, "M",
            x_label="M  (cells per side)",
            title=f"Execution time vs M\n(N = {N_fixed} fixed)",
        )
    else:
        axes[1].set_title("M sweep — no data")

    plt.tight_layout()
    plt.show()


# ─────────────────────────────────────────────────────────────────────────────
# Entry point
# ─────────────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    csv_path = sys.argv[1] if len(sys.argv) > 1 else find_csv()
    print(f"Loading: {csv_path}")
    plot_benchmark(csv_path)
