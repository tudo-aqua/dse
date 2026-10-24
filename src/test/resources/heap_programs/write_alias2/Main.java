import tools.aqua.concolic.Verifier;

/* Writes through both aliases, a read through the first: its value is ite(a = b, 7, 5), no aliasing decision. */
public class Main {
    public static void main(String[] args) {
        BoxPair p = Verifier.nondetObject(BoxPair.class, null);
        if (p != null && p.a != null && p.b != null) {
            p.a.x = 5;
            p.b.x = 7;
            if (p.a.x == 7) {
                assert false;                         // only with aliasing
            } else {
                throw new IllegalStateException();   // only with distinct boxes
            }
        }
    }
}
