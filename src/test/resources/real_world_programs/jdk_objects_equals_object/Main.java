import java.util.Objects;
import tools.aqua.concolic.Verifier;

/* Like jdk_objects_equals, but both arguments are bounded by Object only. */
public class Main {
    public static void main(String[] args) {
        Object p = Verifier.nondetObject(Object.class, null);
        Object q = Verifier.nondetObject(Object.class, null);
        if (Objects.equals(p, q) != Objects.equals(q, p)) {
            assert false;
        }
    }
}
