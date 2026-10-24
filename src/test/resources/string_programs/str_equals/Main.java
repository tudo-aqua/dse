import tools.aqua.concolic.Verifier;
/* Content vs. identity: distinct string objects with equal contents. */
public class Main {
    public static void main(String[] args) {
        P p = Verifier.nondetObject(P.class, null);
        if (p != null && p.a != null && p.b != null && p.a != p.b) {
            if (p.a.equals(p.b)) {
                assert false;
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
