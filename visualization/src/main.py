"""
Particle Neighbour Visualiser
=============================
Reads the output of Cell Index Method / Brute Force simulation and draws an
interactive figure.

Usage:
    python main.py                   # loads the most-recent run automatically
    python main.py <path/to/run_dir> # loads a specific run directory

Output file format expected (output.txt):
    <id>\t<x>\t<y>\t<r>\t<neighbour_id_1> <neighbour_id_2> ...

Clicking a particle:
    • Selected particle  → red
    • Its neighbours     → green
    • Everything else    → white (uncoloured)
    Clicking the same particle again, or clicking empty space, deselects.

Grid toggle:
    Use the "Show Grid" checkbox (bottom-right) to overlay the M×M cell grid.
"""

import glob
import os
import sys

import matplotlib.patches as mpatches
import matplotlib.pyplot as plt
import numpy as np
from matplotlib.patches import Circle
from matplotlib.widgets import CheckButtons


# ─────────────────────────────────────────────────────────────────────────────
# I/O helpers
# ─────────────────────────────────────────────────────────────────────────────

def find_latest_output_dir() -> str:
    """Return the most-recent valid simulation run directory under outputs/."""
    # visualization/src/main.py  →  up two levels → project root
    project_root = os.path.dirname(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    )
    outputs_dir = os.path.join(project_root, "simulation", "outputs")
    candidates = []

    for run_dir in glob.glob(os.path.join(outputs_dir, "*")):
        if not os.path.isdir(run_dir):
            continue
        output_path = os.path.join(run_dir, "output.txt")
        props_path = os.path.join(run_dir, "properties.txt")
        if os.path.exists(output_path) and os.path.exists(props_path):
            candidates.append(run_dir)

    if not candidates:
        raise FileNotFoundError(
            f"No valid run directories found in: {outputs_dir}. "
            "Expected each run directory to contain output.txt and properties.txt."
        )

    return sorted(candidates)[-1]


def _is_valid_run_dir(run_dir: str) -> bool:
    """True when run_dir contains both output.txt and properties.txt."""
    if not os.path.isdir(run_dir):
        return False
    output_path = os.path.join(run_dir, "output.txt")
    props_path = os.path.join(run_dir, "properties.txt")
    return os.path.exists(output_path) and os.path.exists(props_path)


def resolve_run_dir(user_path: str) -> str:
    """
    Resolve a user-provided run directory path.

    Accepts absolute or relative paths. Also tries common fallbacks such as
    providing only the run id (timestamp folder name).
    """
    normalized_user_path = os.path.normpath(user_path.strip())
    project_root = os.path.dirname(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    )

    candidates = []

    # 1) As written by the user (resolved to absolute from current cwd)
    candidates.append(os.path.abspath(normalized_user_path))

    # 2) If user passed only the run id (or a malformed nested path), try by basename
    run_id = os.path.basename(normalized_user_path)
    if run_id:
        candidates.append(os.path.join(project_root, "simulation", "outputs", run_id))
        candidates.append(os.path.join(project_root, "outputs", run_id))

    # 3) Try fixing duplicated "simulation/outputs" segments if present
    duplicated = os.path.join("simulation", "outputs", "simulation", "outputs")
    if duplicated in normalized_user_path:
        fixed = normalized_user_path.replace(duplicated, os.path.join("simulation", "outputs"))
        candidates.append(os.path.abspath(fixed))

    # Remove duplicates while preserving order
    seen = set()
    unique_candidates = []
    for c in candidates:
        if c not in seen:
            seen.add(c)
            unique_candidates.append(c)

    for candidate in unique_candidates:
        if _is_valid_run_dir(candidate):
            return candidate

    tested = "\n  - " + "\n  - ".join(unique_candidates)
    raise FileNotFoundError(
        "Could not find a valid run directory. "
        "Expected both output.txt and properties.txt in one of:" + tested
    )


def parse_properties(props_path: str) -> dict:
    """Return a {stripped_key: stripped_value} dict from properties.txt."""
    result = {}
    with open(props_path, encoding="utf-8") as f:
        for line in f:
            if ":" in line:
                key, val = line.split(":", 1)
                result[key.strip()] = val.strip()
    return result


def parse_output(output_path: str) -> dict:
    """
    Parse output.txt.  Each line must be:
        <id>\\t<x>\\t<y>\\t<r>\\t<neighbour_ids space-separated>

    Returns:
        dict mapping particle_id (int) to
            {"x": float, "y": float, "r": float, "neighbours": set[int]}
    """
    particles = {}
    with open(output_path, encoding="utf-8") as f:
        for lineno, raw in enumerate(f, 1):
            line = raw.strip()
            if not line:
                continue
            parts = line.split("\t")
            if len(parts) < 4:
                raise ValueError(
                    f"output.txt line {lineno} has only {len(parts)} tab-separated "
                    f"field(s); expected at least 4 (id, x, y, r).  "
                    f"Re-run the simulation with the updated Main.java to regenerate "
                    f"output files that include particle positions."
                )
            pid = int(parts[0])
            x   = float(parts[1])
            y   = float(parts[2])
            r   = float(parts[3])
            neighbours: set[int] = set()
            if len(parts) > 4 and parts[4].strip():
                neighbours = {int(n) for n in parts[4].split()}
            particles[pid] = {"x": x, "y": y, "r": r, "neighbours": neighbours}
    return particles


# ─────────────────────────────────────────────────────────────────────────────
# Main visualisation
# ─────────────────────────────────────────────────────────────────────────────

def visualize(run_dir: str) -> None:
    output_path = os.path.join(run_dir, "output.txt")
    props_path  = os.path.join(run_dir, "properties.txt")

    props     = parse_properties(props_path)
    particles = parse_output(output_path)

    L  = float(props["L  (side length)"].split()[0].replace(",", "."))
    M  = int(  props["M  (cells / side)"].split()[0])
    rc = float(props["rc (cutoff)"].split()[0].replace(",", "."))
    cell_size = L / M

    method_raw  = props.get("Method", "")
    method_label = "CIM" if "CIM" in method_raw else "BF"
    periodic_raw  = props.get("Periodic BC", "")
    periodic_label = "Periodic BC: on" if periodic_raw.startswith("Yes") else "Periodic BC: off"

    # Minimum radius used purely for rendering/hit-testing when r == 0
    min_display_r = L * 0.008

    # ── Figure & axes ────────────────────────────────────────────────────────
    fig, ax = plt.subplots(figsize=(8, 8))
    plt.subplots_adjust(left=0.08, right=0.95, top=0.94, bottom=0.11)

    ax.set_xlim(0, L)
    ax.set_ylim(0, L)
    ax.set_aspect("equal")
    ax.set_xlabel("x")
    ax.set_ylabel("y")
    ax.set_title(
        f"N={len(particles)}  L={L}  M={M}  rc={rc}  |  {method_label}  |  {periodic_label}"
        "\nClick a particle to highlight its neighbours",
        fontsize=10,
    )

    # Square boundary
    boundary = mpatches.Rectangle(
        (0, 0), L, L,
        linewidth=1.5, edgecolor="black", facecolor="none", zorder=3,
    )
    ax.add_patch(boundary)

    # ── Grid lines (hidden by default) ───────────────────────────────────────
    grid_lines: list = []
    for i in range(1, M):
        vline, = ax.plot(
            [i * cell_size, i * cell_size], [0, L],
            color="#aaaaaa", lw=0.8, ls="--", zorder=1,
        )
        hline, = ax.plot(
            [0, L], [i * cell_size, i * cell_size],
            color="#aaaaaa", lw=0.8, ls="--", zorder=1,
        )
        vline.set_visible(False)
        hline.set_visible(False)
        grid_lines.extend([vline, hline])

    # ── Particle circles ─────────────────────────────────────────────────────
    circles: dict[int, Circle] = {}
    for pid, p in particles.items():
        display_r = max(p["r"], min_display_r)
        circle = Circle(
            (p["x"], p["y"]), display_r,
            facecolor="white", edgecolor="black", linewidth=0.8, zorder=2,
        )
        ax.add_patch(circle)
        circles[pid] = circle

    # ── State ─────────────────────────────────────────────────────────────────
    state: dict = {"selected": None}

    # ── Colour helpers ────────────────────────────────────────────────────────
    def reset_colors() -> None:
        for c in circles.values():
            c.set_facecolor("white")
            c.set_edgecolor("black")
            c.set_linewidth(0.8)

    def highlight(pid: int) -> None:
        reset_colors()
        state["selected"] = pid
        circles[pid].set_facecolor("red")
        circles[pid].set_edgecolor("darkred")
        circles[pid].set_linewidth(1.5)
        for nid in particles[pid]["neighbours"]:
            if nid in circles:
                circles[nid].set_facecolor("green")
                circles[nid].set_edgecolor("darkgreen")
                circles[nid].set_linewidth(1.5)
        fig.canvas.draw_idle()

    # ── Click handler ─────────────────────────────────────────────────────────
    def on_button_press(event) -> None:
        if event.inaxes is not ax or event.button != 1:
            return
        xc, yc = event.xdata, event.ydata

        # Find the topmost particle the cursor is inside (closest centre wins)
        best_pid, best_dist = None, float("inf")
        for pid, p in particles.items():
            hit_r = max(p["r"], min_display_r)
            d = np.hypot(xc - p["x"], yc - p["y"])
            if d <= hit_r and d < best_dist:
                best_dist = d
                best_pid  = pid

        if best_pid is not None:
            if state["selected"] == best_pid:
                # Clicking the already-selected particle deselects it
                reset_colors()
                state["selected"] = None
                fig.canvas.draw_idle()
            else:
                highlight(best_pid)
        else:
            if state["selected"] is not None:
                reset_colors()
                state["selected"] = None
                fig.canvas.draw_idle()

    fig.canvas.mpl_connect("button_press_event", on_button_press)

    # ── Grid toggle widget ────────────────────────────────────────────────────
    ax_check = fig.add_axes([0.35, 0.02, 0.25, 0.06])
    check = CheckButtons(ax_check, ["Show Grid"], [False])

    def on_grid_toggle(_label: str) -> None:
        visible = check.get_status()[0]
        for line in grid_lines:
            line.set_visible(visible)
        fig.canvas.draw_idle()

    check.on_clicked(on_grid_toggle)

    plt.show()


# ─────────────────────────────────────────────────────────────────────────────
# Entry point
# ─────────────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    if len(sys.argv) > 1:
        run_directory = resolve_run_dir(sys.argv[1])
    else:
        run_directory = find_latest_output_dir()

    print(f"Loading run: {run_directory}")
    visualize(run_directory)
