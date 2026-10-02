import java.util.Objects;
import tools.aqua.concolic.Verifier;

/*
 * JDK class library: java.util.Objects.equals(a, b) is (a == b) || (a != null && a.equals(b)), i.e., it
 * dispatches dynamically on the first argument. With a Point and a ColorPoint at the same coordinates,
 * Objects.equals is not symmetric.
 */
public class Main {
    public static void main(String[] args) {
        Point p = Verifier.nondetObject(Point.class, null);
        Point q = Verifier.nondetObject(Point.class, null);
        if (Objects.equals(p, q) != Objects.equals(q, p)) {
            assert false;
        }
    }
}
