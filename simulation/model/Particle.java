package simulation.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Particle {
    // Static counter so every particle gets a unique id, regardless of position
    private static int nextId = 0;

    private final int id;
    private float x;
    private float y;
    private float r;
    private List<Particle> neighbours = new ArrayList<>();

    public Particle(float x, float y, float r) {
        this.id = nextId++;
        this.x = x;
        this.y = y;
        this.r = r;
    }

    public int getId() { return id; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getR() { return r; }

    public List<Particle> getNeighbours() { return neighbours; }

    public void addNeighbour(Particle neighbour) { neighbours.add(neighbour); }

    public void clearNeighbours() { neighbours.clear(); }

    // Center-to-center Euclidean distance
    public float distance(Particle other) {
        float dx = this.x - other.x;
        float dy = this.y - other.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // Border-to-border distance (relevant when particles have a non-zero radius)
    public float borderDistance(Particle other) {
        return distance(other) - this.r - other.r;
    }

    // Identity is determined by the unique id, not by position
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Particle)) return false;
        return this.id == ((Particle) o).id;
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return String.format("Particle{id=%d, x=%.3f, y=%.3f, r=%.3f}", id, x, y, r);
    }
}