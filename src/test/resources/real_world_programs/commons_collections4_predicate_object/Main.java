import org.apache.commons.collections4.Predicate;
import tools.aqua.concolic.Verifier;

/* Like commons_collections4_predicate, but the argument of evaluate is bounded by Object only. */
public class Main {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        Predicate<Object> predicate = Verifier.nondetObject(Predicate.class, null);
        Object o = Verifier.nondetObject(Object.class, null);
        boolean first = predicate.evaluate(o);
        boolean second = predicate.evaluate(o);
        if (first != second) {
            assert false;
        }
    }
}
