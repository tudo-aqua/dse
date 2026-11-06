/** The classic broken extension of equals (cf. J. Bloch, Effective Java, Item 10): violates symmetry. */
public class ColorPoint extends Point {
    private final int color;

    public ColorPoint(int x, int y, int color) {
        super(x, y);
        this.color = color;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ColorPoint)) {
            return false;
        }
        return super.equals(o) && ((ColorPoint) o).color == color;
    }
}
