import org.apache.commons.lang3.compare.ComparableUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import tools.aqua.concolic.Verifier;

/*
 * Apache Commons Lang 3.21.0: ComparableUtils.is(a).between(b, c) and greaterThan with three concolic MutableInts.
 * between(b, c) holds iff b <= a <= c or c <= a <= b (compareTo), and raises a NullPointerException for null
 * arguments.
 */
public class Main {
    public static void main(String[] args) {
        MutableInt a = Verifier.nondetObject(MutableInt.class, null);
        MutableInt b = Verifier.nondetObject(MutableInt.class, null);
        MutableInt c = Verifier.nondetObject(MutableInt.class, null);
        // unreachable: a value between b and c cannot be smaller than both
        if (ComparableUtils.is(a).between(b, c)
                && ComparableUtils.is(b).greaterThan(a) && ComparableUtils.is(c).greaterThan(a)) {
            assert false;
        }
    }
}
