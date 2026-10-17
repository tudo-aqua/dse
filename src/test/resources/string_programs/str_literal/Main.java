import tools.aqua.concolic.Verifier;
/* An interned literal shared by two nondet objects. */
public class Main {
    public static void main(String[] args) {
        L a = Verifier.nondetObject(L.class, null);
        L b = Verifier.nondetObject(L.class, null);
        if (a != null && b != null && a.s != null) {
            if (a.s == b.s) {
                assert false;
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
