import org.apache.commons.lang3.mutable.MutableInt;
import tools.aqua.concolic.Verifier;

/* Like commons_lang3_mutableint, but the argument of equals is bounded by Object only (any class of the library). */
public class Main {
    public static void main(String[] args) {
        MutableInt a = Verifier.nondetObject(MutableInt.class, null);
        Object b = Verifier.nondetObject(Object.class, null);
        // unreachable: MutableInt.equals only accepts MutableInts
        if (a.equals(b) && !(b instanceof MutableInt)) {
            assert false;
        }
        // reachable: two distinct objects that are equal
        if (a.equals(b) && a != b) {
            assert false;
        }
    }
}
