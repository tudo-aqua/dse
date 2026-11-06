import java.util.Comparator;
import tools.aqua.concolic.Verifier;

/*
 * Like commons_collections4_nullcomparator, but the comparator is bounded by the JDK interface Comparator (all
 * comparators of the library: NullComparator, ComparatorChain, ReverseComparator, FixedOrderComparator, ...) and
 * the compared objects by Object.
 */
public class Main {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        Comparator<Object> comparator = Verifier.nondetObject(Comparator.class, null);
        Object a = Verifier.nondetObject(Object.class, null);
        Object b = Verifier.nondetObject(Object.class, null);
        int ab = comparator.compare(a, b);
        // reachable, e.g. with a NullComparator that sorts null above non-null values
        if (a == null && b != null && ab > 0) {
            assert false;
        }
    }
}
