import org.apache.commons.lang3.mutable.MutableInt;
import tools.aqua.concolic.Verifier;

/*
 * Apache Commons Lang 3.21.0: MutableInt.equals(Object) and compareTo(MutableInt) with a concolic argument.
 * Public constructors without String parameters: MutableInt(), MutableInt(int), MutableInt(Number); the latter
 * raises a NullPointerException for null. equals checks the type of the argument (instanceof MutableInt).
 */
public class Main {
    public static void main(String[] args) {
        MutableInt a = Verifier.nondetObject(MutableInt.class, null);
        MutableInt b = Verifier.nondetObject(MutableInt.class, null);
        // unreachable: equals and compareTo agree for MutableInt
        if (a.equals(b) && a.compareTo(b) != 0) {
            assert false;
        }
        // reachable: two distinct objects that are equal
        if (a.equals(b) && a != b) {
            assert false;
        }
    }
}
