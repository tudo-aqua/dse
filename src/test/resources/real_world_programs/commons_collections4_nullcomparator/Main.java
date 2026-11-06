import org.apache.commons.collections4.comparators.NullComparator;
import org.apache.commons.lang3.mutable.MutableInt;
import tools.aqua.concolic.Verifier;

/*
 * Apache Commons Collections 4.6.0 (with Commons Lang 3.21.0): NullComparator.compare(o1, o2) with a concolic
 * comparator (constructors NullComparator(), NullComparator(boolean nullsAreHigh), NullComparator(Comparator) and
 * NullComparator(Comparator, boolean); the latter two reject a null comparator) and two concolic MutableInts.
 */
public class Main {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        NullComparator<MutableInt> comparator = Verifier.nondetObject(NullComparator.class, null);
        MutableInt a = Verifier.nondetObject(MutableInt.class, null);
        MutableInt b = Verifier.nondetObject(MutableInt.class, null);
        int ab = comparator.compare(a, b);
        // reachable iff the comparator sorts null above non-null values (nullsAreHigh)
        if (a == null && b != null && ab > 0) {
            assert false;
        }
    }
}
