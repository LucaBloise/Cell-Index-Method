import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Particle {
    private Float x;
    private Float y;
    private Float r;
    private List<Particle> neighbours = new ArrayList<>();

    public Particle(Float x, Float y, Float r) {
        this.x = x;
        this.y = y;
        this.r = r;
    }

    public Float getX() {
        return x;
    }

    public Float getY() {
        return y;
    }

    public Float getR() {
        return r;
    }

    public List<Particle> getNeighbours() {
        return neighbours;
    }

    public void addNeighbour(Particle neighbour) {
        neighbours.add(neighbour);
    }

    public void clearNeighbours() {
        neighbours.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Particle)) return false;
        Particle p = (Particle) o;
        return Objects.equals(x, p.x) && Objects.equals(y, p.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    public Float distance(Particle other) {
        return (float) Math.sqrt(Math.pow(this.x - other.x, 2) + Math.pow(this.y - other.y, 2));
    }
}