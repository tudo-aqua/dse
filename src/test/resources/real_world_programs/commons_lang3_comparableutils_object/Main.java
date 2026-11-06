import org.apache.commons.lang3.compare.ComparableUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import tools.aqua.concolic.Verifier;

/* Like commons_lang3_comparableutils, but the bounds of b and c are Object only (any class of the library). */
public class Main {
    public static void main(String[] args) {
        MutableInt a = Verifier.nondetObject(MutableInt.class, null);
        Object b = Verifier.nondetObject(Object.class, null);
        Object c = Verifier.nondetObject(Object.class, null);
        if (b instanceof MutableInt && c instanceof MutableInt) {
            MutableInt mb = (MutableInt) b;
            MutableInt mc = (MutableInt) c;
            // unreachable: a value between b and c cannot be smaller than both
            if (ComparableUtils.is(a).between(mb, mc)
                    && ComparableUtils.is(mb).greaterThan(a) && ComparableUtils.is(mc).greaterThan(a)) {
                assert false;
            }
        }
    }
}
