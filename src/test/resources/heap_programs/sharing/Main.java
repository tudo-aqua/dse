import tools.aqua.concolic.Verifier;

/* Sharing between two fields of one object. */
public class Main {
    public static void main(String[] args) {
        Pair p = Verifier.nondetObject(Pair.class, null);
        if (p != null && p.a != null) {
            if (p.a == p.b) {
                assert false;
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
