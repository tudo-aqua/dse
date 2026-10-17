import tools.aqua.concolic.Verifier;

/* A write through one alias, a read through the other. */
public class Main {
    public static void main(String[] args) {
        BoxPair p = Verifier.nondetObject(BoxPair.class, null);
        if (p != null && p.a != null && p.b != null) {
            p.a.x = 5;
            if (p.b.x == 5) {
                assert false;                         // aliasing, or distinct boxes with b.x == 5
            } else {
                throw new IllegalStateException();   // only with distinct boxes
            }
        }
    }
}
